import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys

// Defines the global build settings so they don't need to be edited everywhere
object Settings {
  val Version = "1.0"
//  val AkkaVersion = "2.3-SNAPSHOT"
  val AkkaVersion = "2.2.0"

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Version,
    scalaVersion := "2.10.2",
    crossPaths := false,
    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Akka Releases" at "http://repo.typesafe.com/typesafe/akka-releases",
    resolvers += "Akka Snapshots" at "http://repo.typesafe.com/typesafe/akka-snapshots",
    resolvers += "Spray repo" at "http://repo.spray.io",
    resolvers += "Spray nightlies" at "http://nightlies.spray.io"
  )

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
     ScalariformKeys.preferences in Compile := formattingPreferences,
     ScalariformKeys.preferences in Test    := formattingPreferences
   )

   import scalariform.formatter.preferences._
   def formattingPreferences: FormattingPreferences =
     FormattingPreferences()
       .setPreference(RewriteArrowSymbols, true)
       .setPreference(AlignParameters, true)
       .setPreference(AlignSingleLineCaseStatements, true)
       .setPreference(DoubleIndentClassDeclaration, true)
}
