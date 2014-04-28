package org.tmt.csw.cs.core

/**
 * Represents the contents of a config file
 */
case class ConfigString(str: String) extends ConfigData {
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = str.getBytes
  // TODO: Note: maybe serializing the string would be safer here? (no charset handling)

  override def toString: String = str
}
