import sbt._

// Dependencies

object Dependencies {

  val scalaVersion = "2.11.8"

  val akkaVersion = "2.4.14"
  val akkaHttpVersion = "10.0.0"
  val akkaStreamsVersion = "2.4.14"

  val hornetqVersion = "2.4.7.Final"

  val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.7.0"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion // all akka is ApacheV2
  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % akkaVersion
  val akkaRemote = "com.typesafe.akka" %% "akka-remote" % akkaVersion
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  // Akka streams (experimental)
  val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaStreamsVersion
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
  val akkaHttpCore = "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

  val akkaSse = "de.heikoseeberger" %% "akka-sse" % "1.5.0" // ApacheV2
//  val akkaKryo = "com.github.romix.akka" %% "akka-kryo-serialization" % "0.4.1" // ApacheV2

  val jeromq = "org.zeromq" % "jeromq" % "0.3.5" // LGPL
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2" // ApacheV2
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.1" // EPL v1.0 and the LGPL 2.1
  val janino = "org.codehaus.janino" % "janino" % "2.7.6" // BSD
  val logstashLogbackEncoder = "net.logstash.logback"   % "logstash-logback-encoder" % "3.1" // ApacheV2

  val upickle = "com.lihaoyi" %% "upickle" % "0.4.0" // MIT
  val scalaPickling = "org.scala-lang.modules" %% "scala-pickling" % "0.10.1" // SCALA LICENSE (BSD-like)
//  val sprayJsonShapeless = "com.github.fommil" %% "spray-json-shapeless" % "1.2.0" // ApacheV2

  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "3.5.1.201410131835-r" // EDL (new-style BSD)
  val svnkit = "org.tmatesoft.svnkit" % "svnkit" % "1.8.11" // TMate Open Source License

  val slick = "com.typesafe.slick" % "slick_2.11" % "3.1.1" // BSD 3-clause
  val postgresql = "org.postgresql" % "postgresql" % "9.4.1211" // POSTGRESQL
  val HikariCP = "com.zaxxer" % "HikariCP" % "2.5.1" // ApacheV2

  val redisScala = "com.github.etaty" %% "rediscala" % "1.6.0" // ApacheV2

  val hornetqServer = "org.hornetq" % "hornetq-server" % hornetqVersion // ApacheV2
  val hornetqNative = "org.hornetq" % "hornetq-native" % hornetqVersion from s"http://repo1.maven.org/maven2/org/hornetq/hornetq-native/$hornetqVersion/hornetq-native-$hornetqVersion.jar"
  val ficus = "com.iheart" %% "ficus" % "1.2.3" // MIT

  val protobufJava = "com.google.protobuf" % "protobuf-java" % "2.6.1" // New BSD license

  val scopt = "com.github.scopt" %% "scopt" % "3.3.0"//  MIT License
  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"  // LGPL/ASL

  val jmdns = "org.jmdns" % "jmdns" % "3.5.1" // ApacheV2

  // Test dependencies
  val akkaStreamTestKit = "com.typesafe.akka" %% "akka-stream-testkit" % akkaStreamsVersion // ApacheV2
  val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion

  val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion // ApacheV2
  val akkaMultiNodeTest = "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion

  val scalaTest = "org.scalatest" %% "scalatest" % "2.2.6" // ApacheV2
  val junit = "com.novocode" % "junit-interface" % "0.11" // Two-clause BSD-style license
  val junitInterface = "com.novocode" % "junit-interface" % "0.11"
  val specs2 = "org.specs2" %% "specs2-core" % "3.7" // MIT-style
  val assertj = "org.assertj" % "assertj-core" % "3.5.2" // ApacheV2

  // REPL dependencies
  val scalaLibrary = "org.scala-lang" % "scala-library" % scalaVersion // Scala License: BSD 3-Clause
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion
  val jline = "jline" % "jline" % "2.13" // BSD
}

