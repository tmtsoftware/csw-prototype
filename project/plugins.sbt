resolvers ++= Seq(
        Classpaths.typesafeResolver,
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.5.1")

addSbtPlugin("com.typesafe.akka" % "akka-sbt-plugin" % "2.2.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.7")
