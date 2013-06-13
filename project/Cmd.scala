import sbt._
import Keys._

// Defines the sbt build for the cmd (Command Service) subproject.
trait Cmd extends Build with Settings {
  // top level Cmd project
  lazy val cmd = Project(id = "cmd", base = file("cmd"), settings = buildSettings) aggregate(cmd_core, cmd_akka)

  // Cmd subprojects with dependency information
  lazy val cmd_core = Project(id = "cmd-core", base = file("cmd/core"), settings = buildSettings)
  lazy val cmd_akka = Project(id = "cmd-akka", base = file("cmd/akka"), settings = buildSettings) dependsOn(cmd_core)
}
