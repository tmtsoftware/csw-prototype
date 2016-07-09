//package csw.util.config3
//
//import scala.collection.immutable.Vector
//
///**
// * TYpe of an enum head
// */
//trait EnumValue {
//  def head: String
//
//  def name: String
//
//  def description: String
//}
//
//case class OneEnum(name: String, head: String, description: String = "") extends EnumValue
//
///**
// * A key for an enum head
// * @param nm the name of the key
// * @param possibles the available values
// */
//class EnumKey(nm: String, possibles: Vector[EnumValue]) extends GenericKey[EnumValue](nm)
