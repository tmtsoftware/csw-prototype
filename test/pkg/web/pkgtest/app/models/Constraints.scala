package models

import play.api.data.validation.{Invalid, Valid, ValidationError, Constraint}

/**
 * Constraints used for validation in forms
 */
object Constraints {

  val coordRegEx = """\d\d:\d\d:\d\d(?:\.\d+)?""".r
  val equinoxRegEx = """[A-Z]\d\d\d\d""".r

  val hhmmssConstraint: Constraint[String] = Constraint("constraints.coordinate.hhmmss")({
    plainText =>
      val errors = plainText match {
        case coordRegEx() => Nil
        case _ => Seq(ValidationError("Expected coordinate as hh:mm:ss.sss"))
      }
      if (errors.isEmpty) {
        Valid
      } else {
        Invalid(errors)
      }
  })

  val ddmmssConstraint: Constraint[String] = Constraint("constraints.coordinate.ddmmss")({
    plainText =>
      val errors = plainText match {
        case coordRegEx() => Nil
        case _ => Seq(ValidationError("Expected coordinate as dd:mm:ss.sss"))
      }
      if (errors.isEmpty) {
        Valid
      } else {
        Invalid(errors)
      }
  })

  val equinoxConstraint: Constraint[String] = Constraint("constraints.coordinate.equinox")({
    plainText =>
      val errors = plainText match {
        case equinoxRegEx() => Nil
        case _ => Seq(ValidationError("Expected equinox as J2000, B1950, etc..."))
      }
      if (errors.isEmpty) {
        Valid
      } else {
        Invalid(errors)
      }
  })

}
