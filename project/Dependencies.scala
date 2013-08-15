import sbt._

// Dependencies

object Dependencies {

  val AkkaVersion = "2.2.0"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor      = "com.typesafe.akka"             %% "akka-actor"            % AkkaVersion
  val akkaKernel     = "com.typesafe.akka"             %% "akka-kernel"           % AkkaVersion
  val akkaRemote     = "com.typesafe.akka"             %% "akka-remote"           % AkkaVersion
  val typesafeConfig = "com.typesafe"                   % "config"                % "1.0.1"
  val scalaLogging   = "com.typesafe"                  %% "scalalogging-slf4j"    % "1.0.1"
  val logback        = "ch.qos.logback"                 % "logback-classic"       % "1.0.13"

  val jgit           = "org.eclipse.jgit"               % "org.eclipse.jgit"      % "2.3.1.201302201838-r"
  val scalaIoFile    = "com.github.scala-incubator.io" %% "scala-io-file"         % "0.4.2"

  // Test dependencies
  val akkaTestKit    = "com.typesafe.akka"             %% "akka-testkit"          % AkkaVersion
  val scalaTest      = "org.scalatest"                  % "scalatest_2.10"        % "2.0.M5b"
  val junit          = "com.novocode"                   % "junit-interface"       % "0.10-M4"

  val liftJSON       = "net.liftweb"                   %% "lift-json"             % "2.5"

  // Local dependencies
  /*
  val csAkka         = "org.tmt.csw" % "cs.akka" % Version
  val csApi          = "org.tmt.csw" % "cs.api" % Version
  val csCore         = "org.tmt.csw" % "cs.core" % Version

  val cmdAkka        = "org.tmt.csw" % "cmd.akka" % Version
  val cmdCore        = "org.tmt.csw" % "cmd.core" % Version

  val pkgAkka        = "org.tmt.csw" % "pkg.akka" % Version
*/
}
