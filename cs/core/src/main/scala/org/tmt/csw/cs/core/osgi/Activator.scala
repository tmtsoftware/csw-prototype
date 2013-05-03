package org.tmt.csw.cs.core.osgi

import org.osgi.framework.{BundleContext, BundleActivator}

/**
 *
 */
class Activator extends BundleActivator {

  def start(context: BundleContext) {
    println("XXX org.tmt.csw.cs.core.remoteRepo = " + context.getProperty("org.tmt.csw.cs.core.remoteRepo"))
  }

  def stop(context: BundleContext) {

  }
}
