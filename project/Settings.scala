import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtMultiJvm
import scala.Some
import twirl.sbt.TwirlPlugin._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtNativePackager._

// Defines the global build settings so they don't need to be edited everywhere
object Settings {
  val Version = "1.0"

  val buildSettings = Defaults.defaultSettings ++ Seq (
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Version,
    scalaVersion := "2.10.3",
    crossPaths := false,
    parallelExecution in Test := false,
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += "Akka Releases" at "http://repo.typesafe.com/typesafe/akka-releases",
//    resolvers += "Akka Snapshots" at "http://repo.typesafe.com/typesafe/akka-snapshots",
    resolvers += "Spray repo" at "http://repo.spray.io",
//    resolvers += "Spray nightlies" at "http://nightlies.spray.io",
    resolvers += Resolver.sonatypeRepo("releases"),
//    resolvers += Resolver.sonatypeRepo("snapshots"),
    resolvers += "rediscala" at "https://github.com/etaty/rediscala-mvn/raw/master/releases/",
    // local maven repo
    resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository"
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )

  // For standalone applications
  lazy val packageSettings = defaultSettings ++ packagerSettings ++ packageArchetype.java_application


  lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
    // Next line fixes missing source folder in idea project, but breaks the "test" target
//    unmanagedSourceDirectories in Test <+= baseDirectory { _ / "src" / "multi-jvm" / "scala" },
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    // make sure that MultiJvm tests are executed by the default test target,
    // and combine the results from ordinary test and multi-jvm tests
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences
  )

  lazy val twirlSettings = Twirl.settings ++ Seq(
    Twirl.twirlImports := Seq(
      "org.tmt.csw.cmd.akka.CommandServiceActor.CommandServiceStatus"
    )
  )

  import scalariform.formatter.preferences._
  def formattingPreferences: FormattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
}
