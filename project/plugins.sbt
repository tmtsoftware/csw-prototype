resolvers ++= Seq(
        Classpaths.typesafeResolver,
        "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.3.0")

addSbtPlugin("com.typesafe.akka" % "akka-sbt-plugin" % "2.2-SNAPSHOT")

