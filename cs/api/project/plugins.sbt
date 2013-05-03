resolvers ++= Seq(
        Classpaths.typesafeResolver
)

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.3.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.5.0")

