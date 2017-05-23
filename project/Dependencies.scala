import sbt._

// Dependencies

//noinspection TypeAnnotation
object Dependencies {

  val scalaVersion = "2.12.2"

  val akkaVersion = "2.5.1"
  val akkaHttpVersion = "10.0.6"

  val hornetqVersion = "2.4.7.Final"

  // Required by javacsw, supports compatibility between Scala and Java
  val scalaJava8Compat = "org.scala-lang.modules" %% "scala-java8-compat" % "0.8.0"

  // -- Akka actor support --
  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion // all akka is ApacheV2
  val akkaRemote = "com.typesafe.akka" %% "akka-remote" % akkaVersion
  // Required by log project
  val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion

  // Akka HTTP
  // Required by cs, configServiceAnnex for REST API
  val akkaHttp = "com.typesafe.akka" %% "akka-http" % akkaHttpVersion

  // Required by alarms, cs, util for JSON support
  val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion

  // -- Logging --
  // Required by log project (and indirectly by most other projects)
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0" // ApacheV2
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.10" // EPL v1.0 and the LGPL 2.1
  val logstashLogbackEncoder = "net.logstash.logback"   % "logstash-logback-encoder" % "4.8" // ApacheV2
  // Required by logback (runtime dependency)
  val janino = "org.codehaus.janino" % "janino" % "3.0.6" // BSD

  // -- Version Control --
  // Required by cs project
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "3.5.1.201410131835-r" // EDL (new-style BSD)
  val svnkit = "org.tmatesoft.svnkit" % "svnkit" % "1.8.11" // TMate Open Source License

  // -- Database --
  // Required by dbs project (planned)
  val slick = "com.typesafe.slick" % "slick_2.11" % "3.1.1" // BSD 3-clause
  val postgresql = "org.postgresql" % "postgresql" % "9.4.1211" // POSTGRESQL
  val HikariCP = "com.zaxxer" % "HikariCP" % "2.5.1" // ApacheV2

  // Required by alarms, events
  val redisScala = "com.github.etaty" %% "rediscala" % "1.8.0" // ApacheV2

  // Required by event_old
  val hornetqServer = "org.hornetq" % "hornetq-server" % hornetqVersion // ApacheV2
  val hornetqNative = "org.hornetq" % "hornetq-native" % hornetqVersion from s"http://repo1.maven.org/maven2/org/hornetq/hornetq-native/$hornetqVersion/hornetq-native-$hornetqVersion.jar"

  // Required by alarms, event_old (Functional API for reading config files)
  val ficus = "com.iheart" %% "ficus" % "1.4.0" // MIT

  // -- Command line arg parsing support --
  // Required by cs, containerCmd, trackLoction, asConsole, sysControl, csClient
  val scopt = "com.github.scopt" %% "scopt" % "3.5.0"//  MIT License

  // Rquired by alarms (validate JSON schema for alarms.conf)
  val jsonSchemaValidator = "com.github.fge" % "json-schema-validator" % "2.2.6"  // LGPL/ASL

  // Required by loc (mDNS API for location service)
  val jmdns = "org.jmdns" % "jmdns" % "3.5.1" // ApacheV2

  // -- Test dependencies --
  // Required by all projects that test actors
  val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion // ApacheV2
  // Required for cs, pkg (test with multiple jvms)
  val akkaMultiNodeTest = "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion

  // Required for all Scala tests (except those using specs2)
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" // ApacheV2
  // Required for all Java tests
  val junitInterface = "com.novocode" % "junit-interface" % "0.11"
  // Required by javacsw config tests
  val assertj = "org.assertj" % "assertj-core" % "3.6.2" // ApacheV2

  // -- REPL dependencies --
  // Required by sequencer
  val scalaLibrary = "org.scala-lang" % "scala-library" % scalaVersion // Scala License: BSD 3-Clause
  val scalaCompiler = "org.scala-lang" % "scala-compiler" % scalaVersion
  val scalaReflect = "org.scala-lang" % "scala-reflect" % scalaVersion
  val jline = "jline" % "jline" % "2.14.3" // BSD
}

