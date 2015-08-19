package csw.services.cmd.akka

import akka.actor.ActorRefFactory
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import csw.shared.CommandStatus

import scala.concurrent.duration.Duration
import scala.concurrent.Await
import csw.util.cfg.Configurations._

/**
 * A simple, blocking command service client for use in scripts, in the REPL, or tests.
 */
case class BlockingCommandServiceClient(client: CommandServiceClient)(implicit actorRefFactory: ActorRefFactory) {

  /**
   * Submits a config to the queue for the given command service and waits for it to complete
   * @param conf the config to submit
   * @param timeout optional amount of time to wait for command to complete
   * @return the command completion status
   */
  def submit(conf: ConfigList, timeout: Duration = client.statusTimeout): CommandStatus = {
    implicit val mat = ActorMaterializer()
    val source = Await.result(client.queueSubmit(conf), timeout)
    Await.result(source.filter(_.done).runWith(Sink.head), timeout)
  }

  /**
   * Sends a config directly to the given command service and waits for it to complete
   * @param conf the config to submit
   * @param timeout optional amount of time to wait for command to complete
   * @return the command completion status
   */
  def request(conf: ConfigList, timeout: Duration = client.statusTimeout): CommandStatus = {
    implicit val mat = ActorMaterializer()
    val source = Await.result(client.queueBypassRequest(conf), timeout)
    Await.result(source.filter(_.done).runWith(Sink.head), timeout)
  }

  /**
   * Used to query the current state of a device. A config is passed in (the values are ignored)
   * and a reply will be sent containing the same config with the current values filled out.
   *
   * @param config used to specify the keys for the values that should be returned
   * @param timeout optional amount of time to wait for command to complete
   * @return the config with the values filled out
   */
  def getConfig(config: SetupConfigList, timeout: Duration = client.statusTimeout): SetupConfigList = {
    val response = Await.result(client.configGet(config), timeout)
    response.tryConfig.get
  }
}
