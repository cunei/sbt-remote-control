package sbt
package server

import SbtToProtocolUtils._

// This is a helper class that lets us run discovery methods on sbt.
private[server] object SbtDiscovery {

  private def getRootObjectName(o: AnyRef): String = {
    val rawClassname = o.getClass.getName
    // Technically, the $ shoudl ALWAYS be there for autoplugins.
    if (rawClassname endsWith "$") rawClassname.dropRight(1)
    else rawClassname
  }

  def buildStructure(state: State): protocol.MinimalBuildStructure = {
    val extracted = sbt.Project.extract(state)
    val projects =
      (for {
        (build, unit) <- extracted.structure.units
        resolved <- unit.defined.values
        ref = projectRefToProtocol(ProjectRef(build, resolved.id))
        plugins = resolved.autoPlugins.map(getRootObjectName)
      } yield protocol.MinimalProjectStructure(ref, plugins)).toSeq

    val builds = projects.map(_.id.build).distinct
    protocol.MinimalBuildStructure(
      builds = builds,
      projects = projects)
  }

  // have to leave the type inferencer here.
  def structure(state: State) =
    Project.extract(state).structure

  def keyIndex(state: State): sbt.KeyIndex =
    structure(state).index.keyIndex

  def builds(state: State): Set[String] =
    keyIndex(state).buildURIs map (_.toASCIIString)

  def projects(state: State, build: URI): Set[protocol.ProjectReference] =
    keyIndex(state).projects(build) map { name =>
      protocol.ProjectReference(build, name)
    }

  def keys(state: State, filter: protocol.KeyFilter): Seq[protocol.ScopedKey] =
    filteredkeys(state, filter).map(x => scopedKeyToProtocol(x))

  def tasks(state: State, filter: protocol.KeyFilter): Seq[protocol.ScopedKey] =
    for {
      key <- filteredkeys(state, filter)
      if isTaskKey(key)
    } yield scopedKeyToProtocol(key)

  def inputTasks(state: State, filter: protocol.KeyFilter): Seq[protocol.ScopedKey] =
    for {
      key <- filteredkeys(state, filter)
      if isInputKey(key)
    } yield scopedKeyToProtocol(key)

  // Settings must not be tasks or inputTasks.
  def settings(state: State, filter: protocol.KeyFilter): Seq[protocol.ScopedKey] =
    for {
      key <- filteredkeys(state, filter)
      if !isInputKey(key)
      if !isTaskKey(key)
    } yield scopedKeyToProtocol(key)

  private def filteredkeys(state: State, filter: protocol.KeyFilter): Seq[sbt.ScopedKey[_]] = {
    for {
      setting <- structure(state).settings
      key = setting.key
      if shouldIncludeKey(filter)(key)
    } yield key
  }

  val TaskClass = classOf[sbt.Task[_]]
  // NOTE - This in an approximation...
  def isTaskKey[T](key: sbt.ScopedKey[T]): Boolean = {
    val mf = key.key.manifest
    mf.erasure == TaskClass
  }
  def isInputKey[T](key: sbt.ScopedKey[T]): Boolean = {
    val mf = key.key.manifest
    mf.erasure == classOf[sbt.InputTask[_]]
  }

  private def shouldIncludeKey[T](filter: protocol.KeyFilter)(key: ScopedKey[T]): Boolean = {
    def configPasses: Boolean =
      filter.config.map { c =>
        val opt = key.scope.config.toOption
        opt.isDefined && opt.get.name == c
      } getOrElse true

    def projectPasses: Boolean =
      filter.project.map { pr =>
        val opt = key.scope.project.toOption
        opt.collect {
          case x: ProjectRef =>
            // TODO - add build URI to filter...
            (x.project == pr.project) //&& (x.build == pr.build)
            true
        } getOrElse false
      } getOrElse true
    def keyPasses: Boolean =
      filter.key.map { keyname =>
        val opt = key.scope.task.toOption
        opt.isDefined && opt.get.label == keyname
      } getOrElse true
    // We should include if all fitlers pass (or are non-existent).
    configPasses && projectPasses && keyPasses
  }
}