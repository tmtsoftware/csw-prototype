resolvers ++= Seq(
        Classpaths.typesafeReleases,
//        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
        Classpaths.sbtPluginSnapshots
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("com.typesafe.akka" % "akka-sbt-plugin" % "2.2.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.8")

addSbtPlugin("io.spray" %% "sbt-twirl" % "0.7.0-SNAPSHOT")
