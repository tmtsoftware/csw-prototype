package org.tmt.csw.util.cfg

import scala.collection.generic.{GenericTraversableTemplate, CanBuildFrom, TraversableFactory}
import scala.collection.mutable.ListBuffer
import scala.collection.{mutable, TraversableLike}
import org.tmt.csw.util.cfg.ConfigValues.CValue
import org.tmt.csw.util.cfg.Units.NoUnits

// There are only a few different kinds of Configs: Setup, Observe, and Wait Configs are currently identified

object SetupConfig extends TraversableFactory[SetupConfig] {

  def apply(obsId: String) = new SetupConfig(obsId)

  //  implicit def canBuildFrom[A]: CanBuildFrom[Coll, A, SetupConfig[A]] = new GenericCanBuildFrom[A]

  implicit def canBuildFrom[A]: CanBuildFrom[SetupConfig[A], A, SetupConfig[A]] =
    new CanBuildFrom[SetupConfig[A], A, SetupConfig[A]] {
      def apply(): mutable.Builder[A, SetupConfig[A]] = newBuilder

      def apply(from: SetupConfig[A]): mutable.Builder[A, SetupConfig[A]] = newBuilder(from.obsId)
    }


  def newBuilder[A] = {
    new ListBuffer[A] mapResult (x => new SetupConfig("", x: _*))
  }

  def newBuilder[A](obsId: String) = {
    new ListBuffer[A] mapResult (x => new SetupConfig(obsId, x: _*))
  }
}


case class SetupConfig[A](obsId: String, s: A*) extends Traversable[A]
  with GenericTraversableTemplate[A, SetupConfig] with TraversableLike[A, SetupConfig[A]] {

  override def companion = SetupConfig

  override def foreach[U](f: A => U) = s.foreach(f)

  override def toString() = mkString(stringPrefix + s"($obsId, ", ", ", ")")
}


object SetupConfigTest {
//  def main(args: Array[String]) {
//    val t = SetupConfig("obs100")
//    val t1 = t ++ List(5, 6)
//    val t1a = t ++: List(5, 6)
//    assert(t1.isInstanceOf[SetupConfig[Int]])
//    assert(t1a.isInstanceOf[List[Int]])
//    val t2 = t1 ++ SetupConfig(2, 9)
//    println(s"t2 = $t2")
//  }

//def main(args: Array[String]) {
//  val t = SetupConfig("obs100")
//  val t1 = t ++ List(5, 6)
//  val t1a = t ++: List(5, 6)
//  assert(t1.isInstanceOf[SetupConfig[Int]])
//  assert(t1a.isInstanceOf[List[Int]])
//  val t2 = t1 ++ SetupConfig(2, 9)
//  println(s"t2 = $t2")
//}

def main(args: Array[String]) {
    val t = SetupConfig("obs100",
      CValue("mobie.red.filter", NoUnits, "F1-red"),
      CValue("tcs.base.pos.name", NoUnits, "m59"),
      CValue("tcs.base.pos.equinox", NoUnits, 2000)
    )
    println(s"t = $t")
  }
}
