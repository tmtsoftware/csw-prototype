package csw.util.config

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }

import csw.util.config.Configurations._

object ConfigSerializer {

  /**
   * Typeclass to serialize
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
   * Serializers using Java I/O
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

}