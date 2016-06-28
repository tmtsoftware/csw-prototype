package csw.services.alarms

import java.net.InetSocketAddress

import akka.actor.Props
import csw.services.alarms.AlarmModel.SeverityLevel
import redis.{ByteStringDeserializer, ByteStringSerializerLowPriority}
import redis.actors.RedisSubscriberActor
import redis.api.pubsub.{Message, PMessage}

object AlarmSubscriberActor {
  def props(address: InetSocketAddress, channels: Seq[String], patterns: Seq[String], onConnectStatus: Boolean ⇒ Unit): Props =
    Props(classOf[AlarmSubscriberActor], address, channels, patterns, onConnectStatus)
}

/**
 *
 */
class AlarmSubscriberActor(address: InetSocketAddress, channels: Seq[String], patterns: Seq[String], onConnectStatus: Boolean ⇒ Unit)
    extends RedisSubscriberActor(address, channels, patterns, None, onConnectStatus) with ByteStringSerializerLowPriority {

  override def onMessage(m: Message) = {
    val formatter = implicitly[ByteStringDeserializer[String]]
    val s = formatter.deserialize(m.data)
    val sev = SeverityLevel(s).get
    println(s"XXX Message: channel = ${m.channel}, severity = $sev")
  }

  override def onPMessage(pm: PMessage) = {
    val formatter = implicitly[ByteStringDeserializer[String]]
    val s = formatter.deserialize(pm.data)
    val sev = SeverityLevel(s).get
    println(s"XXX PMessage: channel = ${pm.channel}, severity = $sev")
  }
}
