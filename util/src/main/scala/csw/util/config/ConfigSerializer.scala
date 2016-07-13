package csw.util.config

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import csw.util.config.StateVariable._
import csw.util.config.Configurations._
import csw.util.config.Events._

/**
 * Defines methods for serializing configs
 */
object ConfigSerializer {

  /**
   * Defines API to serialize to a byte array
   */
  trait ConfigSerializer[A] {
    def write(in: A): Array[Byte]

    def read(bytes: Array[Byte]): A
  }

  def read[A](bytes: Array[Byte])(implicit cl: ConfigSerializer[A]): A = cl.read(bytes)

  def write[A](in: A)(implicit cl: ConfigSerializer[A]): Array[Byte] = cl.write(in)

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
  implicit object SetupConfigSerializer extends ConfigSerializer[SetupConfig] {
    def write(in: SetupConfig): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SetupConfig = readObj[SetupConfig](bytes)
  }

  implicit object ObserveConfigSerializer extends ConfigSerializer[ObserveConfig] {
    def write(in: ObserveConfig): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ObserveConfig = readObj[ObserveConfig](bytes)
  }

  implicit object WaitConfigSerializer extends ConfigSerializer[WaitConfig] {
    def write(in: WaitConfig): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): WaitConfig = readObj[WaitConfig](bytes)
  }

  implicit object SetupConfigArgSerializer extends ConfigSerializer[SetupConfigArg] {
    def write(in: SetupConfigArg): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SetupConfigArg = readObj[SetupConfigArg](bytes)
  }

  implicit object ObserveConfigArgSerializer extends ConfigSerializer[ObserveConfigArg] {
    def write(in: ObserveConfigArg): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ObserveConfigArg = readObj[ObserveConfigArg](bytes)
  }

  implicit object WaitConfigArgSerializer extends ConfigSerializer[WaitConfigArg] {
    def write(in: WaitConfigArg): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): WaitConfigArg = readObj[WaitConfigArg](bytes)
  }

  implicit object SequenceConfigSerializer extends ConfigSerializer[SequenceConfig] {
    def write(in: SequenceConfig): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SequenceConfig = readObj[SequenceConfig](bytes)
  }

  implicit object ControlConfigSerializer extends ConfigSerializer[ControlConfig] {
    def write(in: ControlConfig): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ControlConfig = readObj[ControlConfig](bytes)
  }

  implicit object SequenceConfigArgSerializer extends ConfigSerializer[SequenceConfigArg] {
    def write(in: SequenceConfigArg): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SequenceConfigArg = readObj[SequenceConfigArg](bytes)
  }

  implicit object ControlConfigArgSerializer extends ConfigSerializer[ControlConfigArg] {
    def write(in: ControlConfigArg): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ControlConfigArg = readObj[ControlConfigArg](bytes)
  }

  implicit object StatusEventSerializer extends ConfigSerializer[StatusEvent] {
    def write(in: StatusEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): StatusEvent = readObj[StatusEvent](bytes)
  }

  implicit object ObserveEventSerializer extends ConfigSerializer[ObserveEvent] {
    def write(in: ObserveEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): ObserveEvent = readObj[ObserveEvent](bytes)
  }

  implicit object SystemEventSerializer extends ConfigSerializer[SystemEvent] {
    def write(in: SystemEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): SystemEvent = readObj[SystemEvent](bytes)
  }

  implicit object EventServiceEventSerializer extends ConfigSerializer[EventServiceEvent] {
    def write(in: EventServiceEvent): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): EventServiceEvent = readObj[EventServiceEvent](bytes)
  }

  implicit object DemandStateSerializer extends ConfigSerializer[DemandState] {
    def write(in: DemandState): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): DemandState = readObj[DemandState](bytes)
  }

  implicit object CurrentStateSerializer extends ConfigSerializer[CurrentState] {
    def write(in: CurrentState): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): CurrentState = readObj[CurrentState](bytes)
  }

  implicit object StateVariableSerializer extends ConfigSerializer[StateVariable] {
    def write(in: StateVariable): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): StateVariable = readObj[StateVariable](bytes)
  }

  implicit object CurrentStatesSerializer extends ConfigSerializer[CurrentStates] {
    def write(in: CurrentStates): Array[Byte] = writeObj(in)

    def read(bytes: Array[Byte]): CurrentStates = readObj[CurrentStates](bytes)
  }

}
