package csw.util.itemSet

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import csw.util.itemSet.StateVariable._
import csw.util.itemSet.ItemSets._
import csw.util.itemSet.Events._

/**
 * Defines methods for serializing configs
 */
object ItemSetSerializer {

  /**
   * Defines API to serialize to a byte array
   */
  trait ItemSetSerializer[A] {
    def write(in: A): Array[Byte]

    def read(bytes: Array[Byte]): A
  }

  def read[A](bytes: Array[Byte])(implicit cl: ItemSetSerializer[A]): A = cl.read(bytes)

  def write[A](in: A)(implicit cl: ItemSetSerializer[A]): Array[Byte] = cl.write(in)

  def writeObj[A](in: A): Array[Byte] = {
    val bos = new ByteArrayOutputStream
    val out = new ObjectOutputStream(bos)
    out.writeObject(in)
    out.close()
    bos.toByteArray
  }

  def readObj[A](bytes: Array[Byte]): A = {
    val in = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val obj = in.readObject()
    in.close()
    obj.asInstanceOf[A]
  }

  /**
   * Implicit serializers using Java I/O
   */
  implicit object SetupSerializer extends ItemSetSerializer[Setup] {
    def write(in: Setup): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): Setup = readObj[Setup](bytes)
  }

  implicit object ObserveSerializer extends ItemSetSerializer[Observe] {
    def write(in: Observe): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): Observe = readObj[Observe](bytes)
  }

  implicit object WaitSerializer extends ItemSetSerializer[Wait] {
    def write(in: Wait): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): Wait = readObj[Wait](bytes)
  }


  implicit object SequenceItemSetSerializer extends ItemSetSerializer[SequenceItemSet] {
    def write(in: SequenceItemSet): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SequenceItemSet = readObj[SequenceItemSet](bytes)
  }

  implicit object ControlItemSetSerializer extends ItemSetSerializer[ControlItemSet] {
    def write(in: ControlItemSet): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ControlItemSet = readObj[ControlItemSet](bytes)
  }

  implicit object StatusEventSerializer extends ItemSetSerializer[StatusEvent] {
    def write(in: StatusEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): StatusEvent = readObj[StatusEvent](bytes)
  }

  implicit object ObserveEventSerializer extends ItemSetSerializer[ObserveEvent] {
    def write(in: ObserveEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ObserveEvent = readObj[ObserveEvent](bytes)
  }

  implicit object SystemEventSerializer extends ItemSetSerializer[SystemEvent] {
    def write(in: SystemEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SystemEvent = readObj[SystemEvent](bytes)
  }

  implicit object EventServiceEventSerializer extends ItemSetSerializer[EventServiceEvent] {
    def write(in: EventServiceEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): EventServiceEvent = readObj[EventServiceEvent](bytes)
  }

  implicit object DemandStateSerializer extends ItemSetSerializer[DemandState] {
    def write(in: DemandState): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): DemandState = readObj[DemandState](bytes)
  }

  implicit object CurrentStateSerializer extends ItemSetSerializer[CurrentState] {
    def write(in: CurrentState): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): CurrentState = readObj[CurrentState](bytes)
  }

  implicit object StateVariableSerializer extends ItemSetSerializer[StateVariable] {
    def write(in: StateVariable): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): StateVariable = readObj[StateVariable](bytes)
  }

  implicit object CurrentStatesSerializer extends ItemSetSerializer[CurrentStates] {
    def write(in: CurrentStates): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): CurrentStates = readObj[CurrentStates](bytes)
  }

}
