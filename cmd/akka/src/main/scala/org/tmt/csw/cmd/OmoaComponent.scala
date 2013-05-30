package org.tmt.csw.cmd

import com.typesafe.config.Config
import scala.collection.mutable
import akka.actor.{Status, Actor}

/**
 * Interface for OMOA components that carry out queued commands
 */
abstract class OmoaComponent {


  /**
   * A target OMOA component uses the Setup Config information to configure the target OMOA component.
   * The phrase used to describe this is a component must match the Config. In a recursive way, it can
   * either match the Config itself or pass all or part of the Config on to another OMOA component
   * (or create a new Config). To match a Setup Config, an SEC performs calculations or starts actions
   * in one or more Assemblies or HCDs. To pass it on, the SEC breaks the Config apart into new Configs
   * and Submits the new Configs to other SECs, Assemblies or HCDs and tracks their progress.
   */
  def matchConfig(config: Config)
}

