package csw.util.itemSet

import csw.util.itemSet.ItemSets.ItemSet
import csw.util.itemSet.UnitsOfMeasure.Units

import scala.annotation.varargs
import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._

/**
 * TMT Source Code: 6/23/16.
 */
private[itemSet] object JavaHelpers {

  // BooleanItem
  def jvalue(item: BooleanItem): java.lang.Boolean = item.values(0)

  def jvalue(item: BooleanItem, index: Int): java.lang.Boolean = item.values(index)

  def jvalues(item: BooleanItem): java.util.List[java.lang.Boolean] = item.values.map(i => i: java.lang.Boolean).asJava

  def jget(item: BooleanItem, index: Int): java.util.Optional[java.lang.Boolean] = item.get(index).map(i => i: java.lang.Boolean).asJava

  def jset(key: BooleanKey, v: java.util.List[java.lang.Boolean], units: Units): BooleanItem = BooleanItem(key.keyName, v.asScala.toVector.map(i => i: Boolean), units)

  @varargs
  def jset(key: BooleanKey, v: java.lang.Boolean*) = BooleanItem(key.keyName, v.map(i => i: Boolean).toVector, units = UnitsOfMeasure.NoUnits)

  // ByteArrayItem
  def jvalue(item: ByteArrayItem): ByteArray = item.values(0)

  def jvalue(item: ByteArrayItem, index: Int): ByteArray = item.values(index)

  def jvalues(item: ByteArrayItem): java.util.List[ByteArray] = item.values.asJava

  def jget(item: ByteArrayItem, index: Int): java.util.Optional[ByteArray] = item.get(index).asJava

  def jset(key: ByteArrayKey, v: java.util.List[ByteArray], units: Units): ByteArrayItem = ByteArrayItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: ByteArrayKey, v: ByteArray*) = ByteArrayItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // ByteMatrixItem
  def jvalue(item: ByteMatrixItem): ByteMatrix = item.values(0)

  def jvalue(item: ByteMatrixItem, index: Int): ByteMatrix = item.values(index)

  def jvalues(item: ByteMatrixItem): java.util.List[ByteMatrix] = item.values.asJava

  def jget(item: ByteMatrixItem, index: Int): java.util.Optional[ByteMatrix] = item.get(index).asJava

  def jset(key: ByteMatrixKey, v: java.util.List[ByteMatrix], units: Units): ByteMatrixItem = ByteMatrixItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: ByteMatrixKey, v: ByteMatrix*) = ByteMatrixItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // CharItem
  def jvalue(item: CharItem): java.lang.Character = item.values(0)

  def jvalue(item: CharItem, index: Int): java.lang.Character = item.values(index)

  def jvalues(item: CharItem): java.util.List[java.lang.Character] = item.values.map(i => i: java.lang.Character).asJava

  def jget(item: CharItem, index: Int): java.util.Optional[java.lang.Character] = item.get(index).map(i => i: java.lang.Character).asJava

  def jset(key: CharKey, v: java.util.List[java.lang.Character], units: Units): CharItem = CharItem(key.keyName, v.asScala.toVector.map(i => i: Char), units)

  @varargs
  def jset(key: CharKey, v: java.lang.Character*) = CharItem(key.keyName, v.map(i => i: Char).toVector, units = UnitsOfMeasure.NoUnits)

  // DoubleItem
  def jvalue(item: DoubleItem): java.lang.Double = item.values(0)

  def jvalue(item: DoubleItem, index: Int): java.lang.Double = item.values(index)

  def jvalues(item: DoubleItem): java.util.List[java.lang.Double] = item.values.map(i => i: java.lang.Double).asJava

  def jget(item: DoubleItem, index: Int): java.util.Optional[java.lang.Double] = item.get(index).map(i => i: java.lang.Double).asJava

  def jset(key: DoubleKey, v: java.util.List[java.lang.Double], units: Units): DoubleItem = DoubleItem(key.keyName, v.asScala.toVector.map(i => i: Double), units)

  @varargs
  def jset(key: DoubleKey, v: java.lang.Double*) = DoubleItem(key.keyName, v.map(i => i: Double).toVector, units = UnitsOfMeasure.NoUnits)

  // DoubleArrayItem
  def jvalue(item: DoubleArrayItem): DoubleArray = item.values(0)

  def jvalue(item: DoubleArrayItem, index: Int): DoubleArray = item.values(index)

  def jvalues(item: DoubleArrayItem): java.util.List[DoubleArray] = item.values.asJava

  def jget(item: DoubleArrayItem, index: Int): java.util.Optional[DoubleArray] = item.get(index).asJava

  def jset(key: DoubleArrayKey, v: java.util.List[DoubleArray], units: Units): DoubleArrayItem = DoubleArrayItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: DoubleArrayKey, v: DoubleArray*) = DoubleArrayItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // DoubleMatrixItem
  def jvalue(item: DoubleMatrixItem): DoubleMatrix = item.values(0)

  def jvalue(item: DoubleMatrixItem, index: Int): DoubleMatrix = item.values(index)

  def jvalues(item: DoubleMatrixItem): java.util.List[DoubleMatrix] = item.values.asJava

  def jget(item: DoubleMatrixItem, index: Int): java.util.Optional[DoubleMatrix] = item.get(index).asJava

  def jset(key: DoubleMatrixKey, v: java.util.List[DoubleMatrix], units: Units): DoubleMatrixItem = DoubleMatrixItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: DoubleMatrixKey, v: DoubleMatrix*) = DoubleMatrixItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // FloatItem
  def jvalue(item: FloatItem): java.lang.Float = item.values(0)

  def jvalue(item: FloatItem, index: Int): java.lang.Float = item.values(index)

  def jvalues(item: FloatItem): java.util.List[java.lang.Float] = item.values.map(i => i: java.lang.Float).asJava

  def jget(item: FloatItem, index: Int): java.util.Optional[java.lang.Float] = item.get(index).map(i => i: java.lang.Float).asJava

  def jset(key: FloatKey, v: java.util.List[java.lang.Float], units: Units): FloatItem = FloatItem(key.keyName, v.asScala.toVector.map(i => i: Float), units)

  @varargs
  def jset(key: FloatKey, v: java.lang.Float*) = FloatItem(key.keyName, v.map(i => i: Float).toVector, units = UnitsOfMeasure.NoUnits)

  // FloatArrayItem
  def jvalue(item: FloatArrayItem): FloatArray = item.values(0)

  def jvalue(item: FloatArrayItem, index: Int): FloatArray = item.values(index)

  def jvalues(item: FloatArrayItem): java.util.List[FloatArray] = item.values.asJava

  def jget(item: FloatArrayItem, index: Int): java.util.Optional[FloatArray] = item.get(index).asJava

  def jset(key: FloatArrayKey, v: java.util.List[FloatArray], units: Units): FloatArrayItem = FloatArrayItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: FloatArrayKey, v: FloatArray*) = FloatArrayItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // FloatMatrixItem
  def jvalue(item: FloatMatrixItem): FloatMatrix = item.values(0)

  def jvalue(item: FloatMatrixItem, index: Int): FloatMatrix = item.values(index)

  def jvalues(item: FloatMatrixItem): java.util.List[FloatMatrix] = item.values.asJava

  def jget(item: FloatMatrixItem, index: Int): java.util.Optional[FloatMatrix] = item.get(index).asJava

  def jset(key: FloatMatrixKey, v: java.util.List[FloatMatrix], units: Units): FloatMatrixItem = FloatMatrixItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: FloatMatrixKey, v: FloatMatrix*) = FloatMatrixItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // IntItem
  def jvalue(item: IntItem): java.lang.Integer = item.values(0)

  def jvalue(item: IntItem, index: Int): java.lang.Integer = item.values(index)

  def jvalues(item: IntItem): java.util.List[java.lang.Integer] = item.values.map(i => i: java.lang.Integer).asJava

  def jget(item: IntItem, index: Int): java.util.Optional[java.lang.Integer] = item.get(index).map(i => i: java.lang.Integer).asJava

  def jset(key: IntKey, v: java.util.List[java.lang.Integer], units: Units): IntItem = IntItem(key.keyName, v.asScala.toVector.map(i => i: Int), units)

  @varargs
  def jset(key: IntKey, v: java.lang.Integer*) = IntItem(key.keyName, v.map(i => i: Int).toVector, units = UnitsOfMeasure.NoUnits)

  // IntArrayItem
  def jvalue(item: IntArrayItem): IntArray = item.values(0)

  def jvalue(item: IntArrayItem, index: Int): IntArray = item.values(index)

  def jvalues(item: IntArrayItem): java.util.List[IntArray] = item.values.asJava

  def jget(item: IntArrayItem, index: Int): java.util.Optional[IntArray] = item.get(index).asJava

  def jset(key: IntArrayKey, v: java.util.List[IntArray], units: Units): IntArrayItem = IntArrayItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: IntArrayKey, v: IntArray*) = IntArrayItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // IntMatrixItem
  def jvalue(item: IntMatrixItem): IntMatrix = item.values(0)

  def jvalue(item: IntMatrixItem, index: Int): IntMatrix = item.values(index)

  def jvalues(item: IntMatrixItem): java.util.List[IntMatrix] = item.values.asJava

  def jget(item: IntMatrixItem, index: Int): java.util.Optional[IntMatrix] = item.get(index).asJava

  def jset(key: IntMatrixKey, v: java.util.List[IntMatrix], units: Units): IntMatrixItem = IntMatrixItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: IntMatrixKey, v: IntMatrix*) = IntMatrixItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // LongItem
  def jvalue(item: LongItem): java.lang.Long = item.values(0)

  def jvalue(item: LongItem, index: Int): java.lang.Long = item.values(index)

  def jvalues(item: LongItem): java.util.List[java.lang.Long] = item.values.map(i => i: java.lang.Long).asJava

  def jget(item: LongItem, index: Int): java.util.Optional[java.lang.Long] = item.get(index).map(i => i: java.lang.Long).asJava

  def jset(key: LongKey, v: java.util.List[java.lang.Long], units: Units): LongItem = LongItem(key.keyName, v.asScala.toVector.map(i => i: Long), units)

  @varargs
  def jset(key: LongKey, v: java.lang.Long*) = LongItem(key.keyName, v.map(i => i: Long).toVector, units = UnitsOfMeasure.NoUnits)

  // LongArrayItem
  def jvalue(item: LongArrayItem): LongArray = item.values(0)

  def jvalue(item: LongArrayItem, index: Int): LongArray = item.values(index)

  def jvalues(item: LongArrayItem): java.util.List[LongArray] = item.values.asJava

  def jget(item: LongArrayItem, index: Int): java.util.Optional[LongArray] = item.get(index).asJava

  def jset(key: LongArrayKey, v: java.util.List[LongArray], units: Units): LongArrayItem = LongArrayItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: LongArrayKey, v: LongArray*) = LongArrayItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // LongMatrixItem
  def jvalue(item: LongMatrixItem): LongMatrix = item.values(0)

  def jvalue(item: LongMatrixItem, index: Int): LongMatrix = item.values(index)

  def jvalues(item: LongMatrixItem): java.util.List[LongMatrix] = item.values.asJava

  def jget(item: LongMatrixItem, index: Int): java.util.Optional[LongMatrix] = item.get(index).asJava

  def jset(key: LongMatrixKey, v: java.util.List[LongMatrix], units: Units): LongMatrixItem = LongMatrixItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: LongMatrixKey, v: LongMatrix*) = LongMatrixItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // ShortItem
  def jvalue(item: ShortItem): java.lang.Short = item.values(0)

  def jvalue(item: ShortItem, index: Int): java.lang.Short = item.values(index)

  def jvalues(item: ShortItem): java.util.List[java.lang.Short] = item.values.map(i => i: java.lang.Short).asJava

  def jget(item: ShortItem, index: Int): java.util.Optional[java.lang.Short] = item.get(index).map(i => i: java.lang.Short).asJava

  def jset(key: ShortKey, v: java.util.List[java.lang.Short], units: Units): ShortItem = ShortItem(key.keyName, v.asScala.toVector.map(i => i: Short), units)

  @varargs
  def jset(key: ShortKey, v: java.lang.Short*) = ShortItem(key.keyName, v.map(i => i: Short).toVector, units = UnitsOfMeasure.NoUnits)

  // ShortArrayItem
  def jvalue(item: ShortArrayItem): ShortArray = item.values(0)

  def jvalue(item: ShortArrayItem, index: Int): ShortArray = item.values(index)

  def jvalues(item: ShortArrayItem): java.util.List[ShortArray] = item.values.asJava

  def jget(item: ShortArrayItem, index: Int): java.util.Optional[ShortArray] = item.get(index).asJava

  def jset(key: ShortArrayKey, v: java.util.List[ShortArray], units: Units): ShortArrayItem = ShortArrayItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: ShortArrayKey, v: ShortArray*) = ShortArrayItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // ShortMatrixItem
  def jvalue(item: ShortMatrixItem): ShortMatrix = item.values(0)

  def jvalue(item: ShortMatrixItem, index: Int): ShortMatrix = item.values(index)

  def jvalues(item: ShortMatrixItem): java.util.List[ShortMatrix] = item.values.asJava

  def jget(item: ShortMatrixItem, index: Int): java.util.Optional[ShortMatrix] = item.get(index).asJava

  def jset(key: ShortMatrixKey, v: java.util.List[ShortMatrix], units: Units): ShortMatrixItem = ShortMatrixItem(key.keyName, v.asScala.toVector, units)

  @varargs
  def jset(key: ShortMatrixKey, v: ShortMatrix*) = ShortMatrixItem(key.keyName, v.toVector, units = UnitsOfMeasure.NoUnits)

  // StringItem
  def jvalue(item: StringItem): java.lang.String = item.values(0)

  def jvalue(item: StringItem, index: Int): java.lang.String = item.values(index)

  def jvalues(item: StringItem): java.util.List[java.lang.String] = item.values.map(i => i: java.lang.String).asJava

  def jget(item: StringItem, index: Int): java.util.Optional[java.lang.String] = item.get(index).map(i => i: java.lang.String).asJava

  def jset(key: StringKey, v: java.util.List[java.lang.String], units: Units): StringItem = StringItem(key.keyName, v.asScala.toVector.map(i => i: String), units)

  @varargs
  def jset(key: StringKey, v: java.lang.String*) = StringItem(key.keyName, v.map(i => i: String).toVector, units = UnitsOfMeasure.NoUnits)

  def jadd[I <: Item[_], T <: ItemSet[T]](sc: T, items: java.util.List[I]): T = {
    val x = items.asScala
    x.foldLeft(sc)((r, i) => r.add(i))
  }

  def jget[S, I <: Item[S], T <: ItemSet[T]](sc: T, key: Key[S, I]): java.util.Optional[I] = sc.get(key).asJava

  def jget[S, I <: Item[S], T <: ItemSet[T], J](sc: T, key: Key[S, I], index: Int): java.util.Optional[J] = {
    sc.get(key) match {
      case Some(item) => (if (index >= 0 && index < item.size) Some(jvalue(sc, key, index)) else None).asJava
      case None       => None.asJava
    }
  }

  def jvalue[S, I <: Item[S], T <: ItemSet[T], J](sc: T, key: Key[S, I], index: Int): J = {
    val item = sc.get(key)
    item match {
      case Some(x) => x.values(index).asInstanceOf[J]
      case None    => throw new NoSuchElementException(s"Item: $key not found")
    }
  }

  def jvalue[S, I <: Item[S], T <: ItemSet[T], J](sc: T, key: Key[S, I]): J = {
    val item = sc.get(key)
    item match {
      case Some(x) => x.values(0).asInstanceOf[J]
      case None    => throw new NoSuchElementException(s"Item: $key not found")
    }
  }

  def jvalues[S, I <: Item[S], T <: ItemSet[T], J](sc: T, key: Key[S, I]): java.util.List[J] = {
    val item = sc.get(key)
    item match {
      case Some(x) => x.values.map(i => i.asInstanceOf[J]).asJava
      case None    => throw new NoSuchElementException(s"Item: $key not found")
    }
  }

  // ChoiceItem
  def jvalue(item: ChoiceItem): Choice = item.values(0)

  def jvalue(item: ChoiceItem, index: Int): Choice = item.values(index)

  def jvalues(item: ChoiceItem): java.util.List[Choice] = item.values.map(i => i: Choice).asJava

  def jget(item: ChoiceItem, index: Int): java.util.Optional[Choice] = item.get(index).map(i => i: Choice).asJava

  def jset(key: ChoiceKey, v: java.util.List[Choice], units: Units): ChoiceItem = ChoiceItem(key.keyName, key.choices, v.asScala.toVector.map(i => i: Choice), units)

  @varargs
  def jset(key: ChoiceKey, v: Choice*) = ChoiceItem(key.keyName, key.choices, v.map(i => i: Choice).toVector, units = UnitsOfMeasure.NoUnits)

  // StructItem
  def jvalue(item: StructItem): Struct = item.values(0)

  def jvalue(item: StructItem, index: Int): Struct = item.values(index)

  def jvalues(item: StructItem): java.util.List[Struct] = item.values.map(i => i: Struct).asJava

  def jget(item: StructItem, index: Int): java.util.Optional[Struct] = item.get(index).map(i => i: Struct).asJava

  def jset(key: StructKey, v: java.util.List[Struct], units: Units): StructItem = StructItem(key.keyName, v.asScala.toVector.map(i => i: Struct), units)

  @varargs
  def jset(key: StructKey, v: Struct*) = StructItem(key.keyName, v.map(i => i: Struct).toVector, units = UnitsOfMeasure.NoUnits)
}
