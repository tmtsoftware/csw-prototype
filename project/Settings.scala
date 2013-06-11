import sbt._

// Defines the global build settings so they don't need to be edited everywhere
trait Settings {
  val Organization = "org.tmt"
  val Version = "1.0"
  val SrcScalaVersion = "2.10.1"
  val AkkaVersion = "2.1.4"

  // Dependencies
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % AkkaVersion
  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % AkkaVersion
  val typesafeConfig = "com.typesafe" % "config" % "1.0.1"
  val scalaLogging = "com.typesafe" %% "scalalogging-slf4j" % "1.0.1"
  val logback = "ch.qos.logback" % "logback-classic" % "1.0.13"

  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "2.3.1.201302201838-r"
  val scalaIoFile = "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.2"

  // Test dependencies
  val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % "test"
  val scalaTest = "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test"
  val junit = "com.novocode" % "junit-interface" % "0.10-M4" % "test"

  // Local dependencies
  val csAkka = "org.tmt" %% "org.tmt.csw.cs.akka" % Version
  val csApi = "org.tmt" %% "org.tmt.csw.cs.api" % Version
  val csCore = "org.tmt" %% "org.tmt.csw.cs.core" % Version


}
