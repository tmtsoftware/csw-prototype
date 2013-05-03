package org.tmt.csw.cs.api

/**
 * Represents the contents of a config file
 */
class ConfigString(str: String) extends ConfigData {
  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = str.getBytes

  override def toString: String = str
}
