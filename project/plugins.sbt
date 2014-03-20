resolvers ++= Seq(
        Classpaths.typesafeReleases,
        Classpaths.sbtPluginSnapshots
)

addSbtPlugin("com.typesafe.akka" % "akka-sbt-plugin" % "2.2.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.8")

addSbtPlugin("io.spray" %% "sbt-twirl" % "0.7.0-SNAPSHOT")

//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.6.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.0-RC1")
