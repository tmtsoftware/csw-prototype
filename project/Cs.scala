import sbt._
import Keys._

// Defines the sbt build for the cs (Config Service) subproject.
trait Cs extends Build {
  // top level CS project
  lazy val cs = Project(id = "cs", base = file("cs")) aggregate(cs_api, cs_core, cs_test)

  // CS subprojects with dependency information
  lazy val cs_api = Project(id = "cs-api", base = file("cs/api"))
  lazy val cs_core = Project(id = "cs-core", base = file("cs/core")) dependsOn(cs_api)
  lazy val cs_test = Project(id = "cs-test", base = file("cs/test")) dependsOn(cs_api)

}
