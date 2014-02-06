package com.typesafe.sbtrc
package controller

import com.typesafe.sbt.ui.{ Context => UIContext, Params, SimpleJsonMessage }
import com.typesafe.sbt.ui
import java.lang.reflect.{ Method, Proxy }
import com.typesafe.sbt.ui.SimpleJsonMessage
import scala.util.parsing.json.JSONObject
import sbt._

object AtmosSupport {

  // Hacker to add a value to a task sequence (somewhat ignoring runtime types).
  def hackyAddToTask[T](key: TaskKey[Seq[T]], element: Any): Setting[Task[Seq[T]]] =
    key <<= key map { value =>
      element.asInstanceOf[T] +: value
    }

  def makeAtmosRunHook(exampleSetting: Setting[_], ui: UIContext): Setting[_] = {
    val key = exampleSetting.key
    def eventMonitor(uri: URI): Unit = {
      // TODO - Formalize this as a case class?
      ui.sendEvent("atmosStarted", SimpleJsonMessage(JSONObject(Map("uri" -> uri.toASCIIString()))))
    }
    val listener: URI => Unit = eventMonitor _
    hackyAddToTask(TaskKey(key.key.asInstanceOf[AttributeKey[Task[Seq[AnyRef]]]]) in key.scope, listener)
  }

  def findAtmosSetting(name: String, settings: Seq[Setting[_]]): Option[Setting[_]] =
    (for {
      setting <- settings
      if setting.key.key.label == name
    } yield setting).headOption

  // Adds our hooks into the Atmos build.
  def installHooks(state: State, ui: UIContext): State = {
    val (extracted, ref) = SbtUtil.extractWithRef(state)
    val settings = extracted.session.mergeSettings
    val runHookKey = findAtmosSetting("atmos-run-listeners", settings).getOrElse(
      sys.error("Unable to find play run hook!  Possibly incompatible play version."))
    val fixedHook = makeAtmosRunHook(runHookKey, ui)
    val newSettings = SbtUtil.makeAppendSettings(Seq[Setting[_]](fixedHook), ref, extracted)
    SbtUtil.reloadWithAppended(state, newSettings)
  }

  def isAtmosProject(state: State): Boolean = {
    val extracted = Project.extract(state)
    val settings = extracted.session.mergeSettings
    findAtmosSetting("atmos-run-listeners", settings).isDefined
  }

  def installAtmosSupport(origState: State, ui: UIContext): State = {
    if (isAtmosProject(origState)) {
      installHooks(origState, ui)
    } else origState
  }
}