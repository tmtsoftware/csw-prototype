package org.tmt.csw.cs.core.osgi

import org.osgi.framework.{BundleContext, BundleActivator}

/**
 *
 */
class Activator extends BundleActivator {

  def start(context: BundleContext) {
    println("XXX org.tmt.csw.cs.core.osgi.Activator start")
  }

  def stop(context: BundleContext) {

  }
}
