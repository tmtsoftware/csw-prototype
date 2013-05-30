import sbt._
import Keys._

// This is the top level build object used by sbt.
// It extends one trait for each subproject.
// See Cs.scala as an example.
object Csw extends Build with Settings with Cs with Cmd with Test {

  // Add one section for each subproject
  lazy val parent = Project(id = "csw", base = file(".")) aggregate(cs, cmd, test)
}


