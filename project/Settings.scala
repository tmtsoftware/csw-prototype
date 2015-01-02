import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.{SbtMultiJvm, SbtScalariform}
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import com.typesafe.sbt.packager.Keys._
import play.twirl.sbt.Import.TwirlKeys
import sbt.Keys._
import sbt._

// Defines the global build settings so they don't need to be edited everywhere
object Settings {
  val Version = "0.1-SNAPSHOT"

  val buildSettings = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Version,
    scalaVersion := Dependencies.scalaVersion,
    crossPaths := true,
    parallelExecution in Test := false,
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += "Akka Releases" at "http://repo.typesafe.com/typesafe/akka-releases",
    //    resolvers += "Akka Snapshots" at "http://repo.typesafe.com/typesafe/akka-snapshots",
    resolvers += "Spray repo" at "http://repo.spray.io",
    //    resolvers += "Spray nightlies" at "http://nightlies.spray.io",
    resolvers += Resolver.sonatypeRepo("releases"),
    //    resolvers += Resolver.sonatypeRepo("snapshots"),
    //    resolvers += "rediscala" at "https://github.com/etaty/rediscala-mvn/raw/master/releases/",
    resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven",
    resolvers += "mDialog releases" at "http://mdialog.github.io/releases/",
    // local maven repo
    //    resolvers += "Local Maven" at Path.userHome.asFile.toURI.toURL + ".m2/repository"
    resolvers += sbtResolver.value
  )

  lazy val defaultSettings = buildSettings ++ formatSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation")
  )

  // For standalone applications
  def packageSettings(summary: String, desc: String) = defaultSettings ++
    packagerSettings ++ packageArchetype.java_application ++ Seq(
    version in Rpm := Version,
    rpmRelease := "0",
    rpmVendor := "TMT Common Software",
    rpmUrl := Some("http://www.tmt.org"),
    rpmLicense := Some("MIT"),
    rpmGroup := Some("CSW"),
    packageSummary := summary,
    packageDescription := desc
  )

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences
  )

  lazy val twirlSettings = Seq(
    TwirlKeys.templateImports += "csw.services.cmd.akka.CommandServiceActor.CommandServiceStatus"
  )

  import scalariform.formatter.preferences._

  def formattingPreferences: FormattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)
}
