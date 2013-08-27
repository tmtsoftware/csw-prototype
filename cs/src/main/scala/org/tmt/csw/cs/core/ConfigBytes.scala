package org.tmt.csw.cs.core

import org.tmt.csw.cs.api.ConfigData

/**
 * Represents a configuration file
 */
case class ConfigBytes(bytes: Array[Byte]) extends ConfigData {

  /**
   * @return a representation of the object as a byte array
   */
  def getBytes: Array[Byte] = bytes


  /**
   * Should only be used for debugging info (no charset handling)
   * @return contents as string
   */
  override def toString: String = {
    new String(bytes)
  }
}
