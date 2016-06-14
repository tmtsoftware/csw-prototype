//resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3"

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.4")
addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.2")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.3.3")
