//package csw.util.config3
//
//import scala.collection.immutable.Vector
//
///**
// * TYpe of an enum value
// */
//trait EnumValue {
//  def value: String
//
//  def name: String
//
//  def description: String
//}
//
//case class OneEnum(name: String, value: String, description: String = "") extends EnumValue
//
///**
// * A key for an enum value
// * @param nm the name of the key
// * @param possibles the available values
// */
//class EnumKey(nm: String, possibles: Vector[EnumValue]) extends GenericKey[EnumValue](nm)
