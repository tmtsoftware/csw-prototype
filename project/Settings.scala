import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import scalariform.formatter.preferences._
import com.typesafe.sbt.packager.Keys._
import sbt.Keys._
import sbt._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.SbtGhPages.ghpages
import com.typesafe.sbt.SbtGit.git
import sbtunidoc.Plugin._
import UnidocKeys._

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.{ MultiJvm, jvmOptions }

// Defines the global build settings so they don't need to be edited everywhere
object Settings {
  val Version = "0.2-SNAPSHOT"

  val buildSettings = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Version,
    scalaVersion := Dependencies.scalaVersion,
    crossPaths := true,
    parallelExecution in Test := false,
    fork := true,
    autoAPIMappings := true,
    resolvers += Resolver.typesafeRepo("releases"),
    resolvers += "Akka Releases" at "http://repo.typesafe.com/typesafe/akka-releases",
    resolvers += "Spray repo" at "http://repo.spray.io",
    resolvers += Resolver.sonatypeRepo("releases"),
    resolvers += "mDialog releases" at "http://mdialog.github.io/releases/",
    resolvers += "Scalaz Bintray Repo" at "https://dl.bintray.com/scalaz/releases",
    resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven",
    resolvers += sbtResolver.value
  )


//  // Used to generate JavaDoc. See https://github.com/typesafehub/genjavadoc
//  lazy val JavaDoc = config("genjavadoc") extend Compile
//
//  lazy val javadocSettings = inConfig(JavaDoc)(Defaults.configSettings) ++ Seq(
//    addCompilerPlugin("com.typesafe.genjavadoc" %% "genjavadoc-plugin" % "0.9" cross CrossVersion.full),
//    scalacOptions += s"-P:genjavadoc:out=${target.value}/java",
//    packageDoc in Compile := (packageDoc in JavaDoc).value,
//    sources in JavaDoc :=
//      (target.value / "java" ** "*.java").get ++
//        (sources in Compile).value.filter(_.getName.endsWith(".java")),
//    javacOptions in JavaDoc := Seq(),
//    artifactName in packageDoc in JavaDoc := ((sv, mod, art) =>
//      "" + mod.name + "_" + sv.binary + "-" + mod.revision + "-javadoc.jar")
//  )

  lazy val defaultSettings = buildSettings ++ formatSettings ++ Seq(
    // compile options ScalaUnidoc, unidoc
    scalacOptions ++= Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
    scalacOptions in(Compile, unidoc) ++= Seq("-doc-root-content", baseDirectory.value + "/root-doc.txt"),
    javacOptions in Compile ++= Seq("-source", "1.8"),
    javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in (Test, run) ++= Seq("-Djava.net.preferIPv4Stack=true"),  // For location service
    jvmOptions in MultiJvm := Seq("-Djava.net.preferIPv4Stack=true"),
    testOptions in Test += Tests.Argument("-oI")
  )

  // For standalone applications
  def packageSettings(name: String, summary: String, desc: String) = defaultSettings ++ Seq(
    version in Rpm := Version,
    rpmRelease := "0",
    rpmVendor := "TMT Common Software",
    rpmUrl := Some("http://www.tmt.org"),
    rpmLicense := Some("ApacheV2"),
    rpmGroup := Some("CSW"),
    packageSummary := summary,
    packageDescription := desc,
    bashScriptExtraDefines ++= Seq(s"addJava -DCSW_VERSION=$Version"),
    bashScriptExtraDefines ++= Seq(s"addJava -Dapplication-name=$name")
  )

  lazy val formatSettings = SbtScalariform.scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test := formattingPreferences
  )

  lazy val siteSettings = site.settings ++ ghpages.settings ++ site.includeScaladoc() ++
    site.preprocessSite() ++ Seq(
    git.remoteRepo := "https://github.com/tmtsoftware/csw.git"
  )

  def formattingPreferences: FormattingPreferences =
    FormattingPreferences()
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(DoubleIndentClassDeclaration, true)

}
