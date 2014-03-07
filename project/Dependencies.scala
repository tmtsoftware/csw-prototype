import sbt._

// Dependencies

object Dependencies {

  val akkaVersion = "2.2.3"
  val sprayVersion = "1.2.0"

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val akkaActor      = "com.typesafe.akka"             %% "akka-actor"            % akkaVersion
  val akkaKernel     = "com.typesafe.akka"             %% "akka-kernel"           % akkaVersion
  val akkaRemote     = "com.typesafe.akka"             %% "akka-remote"           % akkaVersion
  val akkaZeromq     = "com.typesafe.akka"             %% "akka-zeromq"           % akkaVersion
//  val scalaZeromq    = "com.mdialog"                   %% "scala-zeromq"          % "0.2.5"

  val typesafeConfig = "com.typesafe"                   % "config"                % "1.0.1"
  val scalaLogging   = "com.typesafe"                  %% "scalalogging-slf4j"    % "1.0.1"
  val logback        = "ch.qos.logback"                 % "logback-classic"       % "1.0.13"

  val sprayCan       = "io.spray"                       % "spray-can"             % sprayVersion
  val sprayClient    = "io.spray"                       % "spray-client"          % sprayVersion
  val sprayRouting   = "io.spray"                       % "spray-routing"         % sprayVersion
  val sprayJson      = "io.spray"                      %% "spray-json"            % "1.2.3"
  val sprayTestkit   = "io.spray"                       % "spray-testkit"         % sprayVersion

  val jgit           = "org.eclipse.jgit"               % "org.eclipse.jgit"      % "2.3.1.201302201838-r"
  val scalaIoFile    = "com.github.scala-incubator.io" %% "scala-io-file"         % "0.4.2"

//  val zmq            = "org.zeromq"                    %% "zeromq-scala-binding" % "0.0.6"

  // Test dependencies
  val akkaTestKit    = "com.typesafe.akka"             %% "akka-testkit"          % akkaVersion
  val scalaTest      = "org.scalatest"                  % "scalatest_2.10"        % "2.0.M5b"
  val junit          = "com.novocode"                   % "junit-interface"       % "0.10-M4"
  val specs2         = "org.specs2"                    %% "specs2"                % "1.14"
  val liftJSON       = "net.liftweb"                   %% "lift-json"             % "2.5"

//  val akkaMultiNodeTest = "com.typesafe.akka"         %% "akka-remote-tests"        % AkkaVersion
  val akkaMultiNodeTest ="com.typesafe.akka"           %% "akka-multi-node-testkit" % akkaVersion

}

