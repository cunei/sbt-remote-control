package com.typesafe.sbtrc
package launching

import java.io.File
import properties.SbtRcProperties._
import scala.collection.JavaConverters.seqAsJavaListConverter

/**
 * This class contains all the sbt-version specific information we
 * need to launch sbt.
 */
trait SbtBasicProcessLaunchInfo {
  /** The properties file to pass sbt when launching. */
  def propsFile: File
  /** The controller classpath used to hook sbt. */
  def controllerClasspath: Seq[File]
  // TODO - Controller main class?
  // TODO - Launcher jar?
}

/**
 * This class uses the sbt properties helper to generate (lazily) the properties
 *  file used to launch sbt.
 */
trait SbtDefaultPropsfileLaunchInfo extends SbtBasicProcessLaunchInfo {
  /** The sbt version to use in the properties file. */
  def sbtVersion: String
  /** Extra jars/directories to place on sbt's classpath. */
  def extraJars: Seq[File] = Nil
  /** Optional repositories to use when resolvign sbt. */
  def optionalRepositories: Seq[SbtPropertiesHelper.Repository] = Nil

  final override lazy val propsFile = {
    val tmp = File.createTempFile("sbtrc", "properties")
    // TODO - Get this version from properties!
    SbtPropertiesHelper.makePropertiesFile(tmp, sbtVersion, extraJars, optionalRepositories)
    tmp.deleteOnExit()
    tmp
  }
}

/** A "template" for how to create an sbt process launcher. */
trait BasicSbtProcessLauncher extends SbtProcessLauncher {
  override def apply(cwd: File, port: Int, extraJvmArgs: Seq[String] = Seq.empty[String]): ProcessBuilder = {
    import collection.JavaConverters._
    new ProcessBuilder(arguments(cwd, port, extraJvmArgs).asJava).
      directory(cwd)
  }

  /** Default JVM Args to use. */
  def jvmArgs: Seq[String] =
    Seq(
      "-Xss1024K",
      "-Xmx" + SBT_XMX,
      "-XX:PermSize=" + SBT_PERMSIZE,
      "-XX:+CMSClassUnloadingEnabled")

  def isPassThroughProperty(name: String): Boolean =
    name match {
      // Ignore play stuff, or we break it.
      case "http.port" => false
      case "http.address" => false
      case "https.port" => false
      case "https.address" => false
      // TODO - What else should pass through?
      case n if n startsWith "http." => true
      case n if n startsWith "https." => true
      case n if n startsWith "ftp." => true
      case n if n startsWith "socksProxy" => true
      case n if n startsWith "sbt" => true
      case n if n startsWith "ivy" => true
      case _ => false
    }

  def passThroughJvmArgs: Seq[String] = {
    for {
      (name, value) <- sys.props.toSeq
      if isPassThroughProperty(name)
    } yield s"-D$name=$value"
  }

  /**
   * Returns the versoin specific information
   *  launch sbt for the given project.
   *  @param version The sbt binary version to use
   *  @param fulLVersion the complete sbt version
   */
  def getLaunchInfo(version: String, fullVersion: String): SbtBasicProcessLaunchInfo

  /** Returns an sbt launcher jar we can use to launch this process. */
  def sbtLauncherJar: File

  /** Returns the sbt verison + binary version we're about to fork. */
  def getSbtVersions(cwd: File): (String, String) =
    // TODO - Put this in sbt version util!
    io.SbtVersionUtil.findSafeProjectSbtVersion(cwd).getOrElse("0.12.4") ->
      io.SbtVersionUtil.findProjectBinarySbtVersion(cwd).getOrElse("0.12")

  /**
   * Generates the arguments used by the sbt process launcher.
   */
  def arguments(cwd: File, port: Int, extraJvmArgs: Seq[String] = Seq.empty[String]): Seq[String] = {
    val portArg = "-Dsbtrc.control-port=" + port.toString
    // TODO - These need to be configurable *and* discoverable.
    // we have no idea if computers will be able to handle this amount of
    // memory....
    val defaultJvmArgs = jvmArgs ++ extraJvmArgs ++ passThroughJvmArgs
    val (sbtFullVersion, sbtBinaryVersion) = getSbtVersions(cwd)
    val info = getLaunchInfo(sbtBinaryVersion, sbtFullVersion)
    // TODO - handle spaces in strings and such...
    val sbtProps = Seq(
      // TODO - Remove this junk once we don't have to hack our classes into sbt's classloader.
      "-Dsbt.boot.properties=" + info.propsFile.toURI.toASCIIString,
      portArg)
    // TODO - Can we look up the launcher.jar via a class?
    val jar = Seq("-jar", sbtLauncherJar.getAbsolutePath)

    // TODO - Is the cross-platform friendly?
    val probeClasspathString =
      "\"\"\"" + ((info.controllerClasspath map (_.getAbsolutePath)).distinct mkString File.pathSeparator) + "\"\"\""
    val escapedPcp = probeClasspathString.replaceAll("\\\\", "/")
    val sbtcommands = Seq(
      s"apply -cp $escapedPcp com.typesafe.sbtrc.SetupSbtChild",
      "listen")

    val result = Seq("java") ++
      defaultJvmArgs ++
      sbtProps ++
      jar ++
      sbtcommands

    quoteCommandLine(result)
  }
}
