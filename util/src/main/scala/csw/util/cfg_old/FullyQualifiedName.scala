package csw.util.cfg_old
import scala.language.implicitConversions

object FullyQualifiedName {
  val SEPARATOR = '.'

  case class Fqn(fqn: String) {
    assert(fqn != null, "fqn can not be a null string")

    lazy val (prefix, name) = Fqn.getPrefixName(fqn)
  }

  object Fqn {
    implicit def strToFqn(s: String) = Fqn(s)

    private def getPrefixName(s: String): (String, String) = {
      val result = s.splitAt(s.lastIndexOf(SEPARATOR))
      // this skips over the SEPARATOR, if present
      if (isFqn(s)) (result._1, result._2.substring(1)) else result
    }

    def isFqn(s: String): Boolean = s.contains(SEPARATOR)

    def prefix(s: String): String = s.splitAt(s.lastIndexOf(SEPARATOR))._1

    def subsystem(s: String): String = s.splitAt(s.indexOf(SEPARATOR))._1

    // Split doesn't remove the separator, so skip it
    def name(fqn: String): String = getPrefixName(fqn)._2

    // Attempt to think about what to do for improper name
    // Currently not in use since I can't manage to do it functionally with Some, etc
    // XXX allan: use Try?
    def validateName(trialName: String): Option[String] = {
      if (trialName.isEmpty) None
      else if (trialName.contains(SEPARATOR)) None
      else Some(trialName)
    }
  }

}

