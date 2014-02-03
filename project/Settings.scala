import akka.sbt.AkkaKernelPlugin._
import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import scala.Some
import twirl.sbt.TwirlPlugin._

// Defines the global build settings so they don't need to be edited everywhere
object Settings {
  val Version = "1.0"

  val buildSettings = Defaults.defaultSettings ++ multiJvmSettings ++ Seq (
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Version,
    scalaVersion := "2.10.3",
    crossPaths := false,
    parallelExecution in Test := false,
    resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/",
    resolvers += "Akka Releases" at "http://repo.typesafe.com/typesafe/akka-releases",
    resolvers += "Akka Snapshots" at "http://repo.typesafe.com/typesafe/akka-snapshots",
    resolvers += "Spray repo" at "http://repo.spray.io",
    resolvers += "Spray nightlies" at "http://nightlies.spray.io",
    resolvers += "Sonatype (releases)" at "https://oss.sonatype.org/content/repositories/releases/"
//    resolvers += "mDialog releases" at "http://mdialog.github.io/releases/"
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )

  lazy val akkaKernelPluginSettings = Seq(
    distJvmOptions in Dist := "-Xms256M -Xmx1024M",
    distBootClass in Dist := "org.tmt.csw.test.app.TestApp",
    outputDirectory in Dist := file("target/test-app")
  )

  lazy val multiJvmSettings = SbtMultiJvm.multiJvmSettings ++ Seq(
    // make sure that MultiJvm test are compiled by the default test compilation
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    // disable parallel tests
    parallelExecution in Test := false,
    executeTests in Test <<=
      (executeTests in Test, executeTests in MultiJvm) map {
        case ((testResults), (multiJvmResults)) =>
          val overall =
            if (testResults.overall.id < multiJvmResults.overall.id) multiJvmResults.overall
            else testResults.overall
          Tests.Output(overall,
            testResults.events ++ multiJvmResults.events,
            testResults.summaries ++ multiJvmResults.summaries)
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
