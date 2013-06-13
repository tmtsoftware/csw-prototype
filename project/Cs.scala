import sbt._
import Keys._

// Defines the sbt build for the cs (Config Service) subproject.
trait Cs extends Build with Settings {
  // top level CS project
  lazy val cs = Project(id = "cs", base = file("cs"), settings = buildSettings) aggregate(cs_api, cs_core, cs_akka)

  // CS subprojects with dependency information
  lazy val cs_api = Project(id = "cs-api", base = file("cs/api"), settings = buildSettings)
  lazy val cs_core = Project(id = "cs-core", base = file("cs/core"), settings = buildSettings) dependsOn(cs_api)
  lazy val cs_akka = Project(id = "cs-akka", base = file("cs/akka"), settings = buildSettings) dependsOn(cs_api, cs_core)
}
