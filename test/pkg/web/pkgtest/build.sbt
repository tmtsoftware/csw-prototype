name := "pkgtest"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "org.tmt" % "cmd" % "1.0"
)     

play.Project.playScalaSettings
