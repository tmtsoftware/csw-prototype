package org.tmt.csw.cs.core

import org.tmt.csw.cs.api.ConfigId

/**
 * Type of an id returned from ConfigManager create or update methods.
 * Holds the Git id for the file.
 */
case class GitConfigId(id: String) extends ConfigId
