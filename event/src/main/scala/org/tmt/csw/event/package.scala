package org.tmt.csw

import org.tmt.csw.util.Configuration
import org.hornetq.api.core.client.{ClientSessionFactory, HornetQClient, ClientSession}
import org.hornetq.api.core.TransportConfiguration
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory

package object event {

  /**
   * An Event here is just a [[Configuration]]
   */
  type Event = Configuration

  // The key for the property that contains the Event object (in string form)
  val propName = "value"

  // Connects to the HornetQ server
  def connectToHornetQ(): (ClientSessionFactory, ClientSession) = {
    val serverLocator = HornetQClient.createServerLocatorWithoutHA(
      new TransportConfiguration(classOf[NettyConnectorFactory].getName))
//    serverLocator.setBlockOnNonDurableSend(false)
//    serverLocator.setBlockOnAcknowledge(false)
//    serverLocator.setBlockOnDurableSend(false)
    serverLocator.setProducerWindowSize(-1)
    serverLocator.setConsumerWindowSize(-1)
    val sf = serverLocator.createSessionFactory
    val session = sf.createSession
    session.start()
    (sf, session)
  }
}

