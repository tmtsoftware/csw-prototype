package csw.util.config

/**
 * This Units stuff is just for play
 * although something should be developed or borrowed
 * for use.
 */
object UnitsOfMeasure {

  // Should parameterize Units so concat can be created concat[A, B]
  case class Units(name: String) {
    override def toString = "[" + name + "]"
  }

  object NoUnits extends Units("none")

  object Meters extends Units("m")

  object Deg extends Units("deg")

  object Seconds extends Units("sec")

  object Milliseconds extends Units("ms")

  object Units {
    def fromString(name: String): Units = name match {
      case Meters.name       ⇒ Meters
      case Deg.name          ⇒ Deg
      case Seconds.name      ⇒ Seconds
      case Milliseconds.name ⇒ Milliseconds
      case _                 ⇒ NoUnits
    }
  }

}

