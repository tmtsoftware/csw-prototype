name := "org.tmt.csw.cmd.akka"

libraryDependencies ++= Seq(
    "org.tmt" %% "org.tmt.csw.cmd.core" % Version,
    akkaActor,
    akkaTestKit,
    scalaTest
)
