name := "org.tmt.csw.cmd.core"

libraryDependencies ++= Seq(
    typesafeConfig,
    scalaLogging,
    logback,
    scalaTest,
    "net.liftweb" %% "lift-json" % "2.5" % "test"
)
