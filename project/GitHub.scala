package cswbuild

object GitHub {

  def url(v: String): String = {
    val branch = if (v.endsWith("SNAPSHOT")) "master" else v
    "https://github.com/tmtsoftware/csw/tree/" + branch
  }
}
