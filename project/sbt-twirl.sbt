
// needed because sbt-twirl depends on twirl-compiler which is only available
// at repo.spray.io
resolvers += "spray repo" at "http://repo.spray.io"

addSbtPlugin("io.spray" % "sbt-twirl" % "0.7.0")
