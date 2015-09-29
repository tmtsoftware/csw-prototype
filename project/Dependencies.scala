import sbt.Keys._
import sbt._
import sbt.Project.projectToRef

// Dependencies

object Dependencies {

  val scalaVersion = "2.11.7"
  val akkaVersion = "2.3.12"
  val akkaStreamsVersion = "1.0"
//  val sprayVersion = "1.3.3"
  val hornetqVersion = "2.4.7.Final"
  //  val hornetqVersion = "2.5.0-SNAPSHOT"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % akkaVersion
  val akkaRemote = "com.typesafe.akka" %% "akka-remote" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  // Akka streams (experimental)
  val akkaStream = "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamsVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamsVersion
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamsVersion
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamsVersion
  val akkaSse = "de.heikoseeberger" %% "akka-sse" % "1.0.0"

  val jeromq = "org.zeromq" % "jeromq" % "0.3.3"
  val typesafeConfig = "com.typesafe" % "config" % "1.2.0"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2"
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.1"
  val janino = "org.codehaus.janino" % "janino" % "2.7.6"
  val logstashLogbackEncoder = "net.logstash.logback"   % "logstash-logback-encoder" % "3.1"
  val upickle = "com.lihaoyi" %% "upickle" % "0.3.4"
  val scalaPickling = "org.scala-lang.modules" %% "scala-pickling" % "0.10.1"

  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "3.5.1.201410131835-r"

  val redisScala = "com.etaty.rediscala" %% "rediscala" % "1.5.0"

  val hornetqServer = "org.hornetq" % "hornetq-server" % hornetqVersion
  val hornetqNative = "org.hornetq" % "hornetq-native" % hornetqVersion from s"http://repo1.maven.org/maven2/org/hornetq/hornetq-native/$hornetqVersion/hornetq-native-$hornetqVersion.jar"
  val ficus = "net.ceedubs" % "ficus_2.11" % "1.1.2"

  val protobufJava = "com.google.protobuf" % "protobuf-java" % "2.5.0"

  val scopt = "com.github.scopt" %% "scopt" % "3.3.0"

  val jmdns = "javax.jmdns" % "jmdns" % "3.4.1"

  // Test dependencies
  val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit-experimental" % akkaStreamsVersion
  val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaStreamsVersion

  val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
  val akkaMultiNodeTest = "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion

  val scalaTest = "org.scalatest" %% "scalatest" % "2.2.5"
  val junit = "com.novocode" % "junit-interface" % "0.11"
  val specs2 = "org.specs2" %% "specs2" % "3.3.1"

  // REPL dependencies
  val scalaLibrary = "org.scala-lang" % "scala-library" % scalaVersion
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion
  val jline = "jline" % "jline" % "2.13"
}

