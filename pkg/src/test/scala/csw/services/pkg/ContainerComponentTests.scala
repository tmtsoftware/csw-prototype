package csw.services.pkg

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.ComponentType.HCD
import csw.services.loc.ConnectionType.{AkkaType, HttpType}
import csw.services.loc.{ComponentId, Connection, LocationService}
import csw.services.pkg.Component._
import csw.services.pkg.LifecycleManager.{Shutdown, Startup, Uninitialize}
import org.scalatest.{BeforeAndAfterAll, FunSpecLike, Matchers}

import scala.concurrent.duration._

object ContainerComponentTests {
  LocationService.initInterface()

  val system = ActorSystem("Test")

  case class SimpleTestHcd(hcdInfo: HcdInfo) extends Hcd with LifecycleHandler {
    def receive = lifecycleHandlerReceive
  }

  case class SimpleTestAssembly(assemblyInfo: AssemblyInfo) extends Assembly with LifecycleHandler {
    def receive = lifecycleHandlerReceive
  }

  val testAssemblyInfo: AssemblyInfo = {
    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.ContainerComponentTests$SimpleTestAssembly"

    AssemblyInfo(name, prefix, className, RegisterOnly, Set(AkkaType), Set.empty[Connection])
  }

  val testHcdInfo: HcdInfo = {
    val name = "test1Hcd"
    val prefix = "test1.Hcd.prefix"
    val className = "csw.services.pkg.ContainerComponentTests$SimpleTestHcd"

    HcdInfo(name, prefix, className, RegisterOnly, Set(AkkaType), 1.second)
  }

  val testContainerInfo: ContainerInfo = {
    import ContainerComponent._
    ContainerInfo(
      "TestContainer",
      RegisterOnly,
      Set(AkkaType),
      List(testAssemblyInfo, testHcdInfo)
    )
  }
}

class ContainerComponentTests extends TestKit(ContainerComponentTests.system) with ImplicitSender
    with FunSpecLike with Matchers with LazyLogging with BeforeAndAfterAll {

  import ContainerComponent._
  import ContainerComponentTests._

  override def afterAll = TestKit.shutdownActorSystem(system)

  private val ASS1 = "Assembly-1"
  private val HCD2A = "HCD-2A"
  private val HCD2B = "HCD-2B"
  //private val BAD1 = "BAD1"
  private val BAD2 = "BAD2"
  private val CONTAINERNAME = "Container-2"

  val t1 =
    """
     container {
      |  name = "Container-1"
      |  components {
      |    Assembly-1 {
      |      type = Assembly
      |      class = csw.pkgDemo.Assembly1
      |      prefix = ass1.test
      |      connectionType: [http, akka]
      |      connections = [
      |        // Component connections used by this component
      |        // Name: ComponentType ConnectionType
      |        {
      |          name: HCD-2A
      |          type: HCD
      |          connectionType: [akka, http]
      |        }
      |        {
      |          name: HCD-2B
      |          type: HCD
      |          connectionType: [http]
      |        }
      |      ]
      |    }
      |    HCD-2A {
      |      type = HCD
      |      class = csw.pkgDemo.hcd2
      |      prefix = tcs.mobie.blue.filter
      |      connectionType: [akka, http]
      |      rate = 1 second
      |    }
      |    HCD-2B {
      |      type: HCD
      |      class: csw.pkgDemo.hcd2
      |      prefix: tcs.mobie.blue.disperser
      |      connectionType: [http]
      |      rate: 1 second
      |    }
      |    BAD1 {
      |      type: BAD
      |      class: csw.pkgDemo.hcd2
      |      prefix: ""
      |      connectionType: [http, akkax]
      |      rate: 1 seconds
      |    }
      |    BAD2 {
      |      type: BAD
      |      // no class
      |      prefixx: ""
      |      connectionType: [http, akkax]
      |      rate: 11 turkey
      |    }
      |  }
      |}
    """.stripMargin

  val t2 =
    """
     container {
      |  name = "Container-2"
      |  connectionType: [akka]
      |  initialDelay = 2 second
      |  creationDelay = 1 second
      |  lifecycleDelay = 3 seconds
      |  components {
      |     Assembly-1 {
      |      type = Assembly
      |      class = "csw.services.pkg.ContainerComponentTests$SimpleTestAssembly"
      |      prefix = ass1.test
      |      connectionType: [akka]
      |      connections = [
      |        // Component connections used by this component
      |        // Name: ComponentType ConnectionType
      |        {
      |          name: HCD-2A
      |          type: HCD
      |          connectionType: [akka]
      |        }
      |      ]
      |      }
      |      HCD-2A {
      |        type = HCD
      |        class = "csw.services.pkg.ContainerComponentTests$SimpleTestHcd"
      |        prefix = tcs.mobie.blue.filter
      |        connectionType: [akka]
      |        rate = 1 second
      |     }
      |   }
      |}
    """.stripMargin

  protected def testParseStringConfig(s: String) = {
    val options = ConfigParseOptions.defaults().
      setOriginDescription("test string").
      setSyntax(ConfigSyntax.CONF)
    ConfigFactory.parseString(s, options)
  }

  private def setup1: Config = testParseStringConfig(t1)

  private def setupComponents = setup1.getConfig("container.components")

  it("Should handle individual pieces going poorly") {
    import scala.concurrent.duration._

    val conf = setupComponents
    val t1 = conf.getConfig(BAD2)

    assert(parseClassName(BAD2, t1).isFailure)
    assert(parseComponentId(BAD2, t1).isFailure)
    assert(parsePrefix(BAD2, t1).isFailure)
    assert(parseConnType(BAD2, t1).isFailure)
    assert(parseRate(BAD2, t1).getOrElse(1.second) == 1.second)
  }

  it("Should have container fields") {
    val setup1: Config = testParseStringConfig(t2)
    val setup = setup1.getConfig(CONTAINER)

    val containerName = parseName(CONTAINERNAME, setup).get

    assert(containerName == CONTAINERNAME)

    assert(parseDuration(containerName, CREATION_DELAY, setup, 5.seconds) == 1.seconds)
    assert(parseDuration(containerName, INITIAL_DELAY, setup, 5.seconds) == 2.seconds)
    assert(parseDuration(containerName, LIFECYCLE_DELAY, setup, 5.seconds) == 3.seconds)

    val connType = parseConnType(containerName, setup).get
    assert(connType == Set(AkkaType))

    // This one has no delays so should report defaults
    val setup2: Config = testParseStringConfig(t1).getConfig(CONTAINER)
    val containerName2 = parseName("Container-1", setup2).get

    assert(containerName2 == "Container-1")
    assert(parseDuration(containerName2, CREATION_DELAY, setup2, DEFAULT_CREATION_DELAY) == DEFAULT_CREATION_DELAY)
    assert(parseDuration(containerName2, INITIAL_DELAY, setup2, DEFAULT_INITIAL_DELAY) == DEFAULT_INITIAL_DELAY)
    assert(parseDuration(containerName2, LIFECYCLE_DELAY, setup2, DEFAULT_LIFECYCLE_DELAY) == DEFAULT_LIFECYCLE_DELAY)
    // should catch default connectionType
    intercept[ConfigurationParsingException] {
      parseConnType(containerName2, setup2).get
    }
    // Also
    assert(parseConnTypeWithDefault(containerName2, setup2, Set(AkkaType)) == Set(AkkaType))
  }

  it("Should handle individual pieces going well") {

    val conf = setupComponents
    val t1 = conf.getConfig(HCD2A)

    assert(parseClassName(HCD2A, t1).get == "csw.pkgDemo.hcd2")

    assert(parseComponentId(HCD2A, t1).get == ComponentId("HCD-2A", HCD))

    assert(parsePrefix(HCD2A, t1).get == "tcs.mobie.blue.filter")

    assert(parseConnType(HCD2A, t1).get == Set(AkkaType, HttpType))

    assert(parseRate(HCD2A, t1).get == 1.second)

  }

  it("Should handle HcdInfo") {
    val conf = setupComponents
    val t1 = conf.getConfig(HCD2A)

    val hcdInfo = parseHcd(HCD2A, t1)
    assert(hcdInfo.get == HcdInfo(HCD2A, "tcs.mobie.blue.filter", "csw.pkgDemo.hcd2", RegisterOnly, Set(AkkaType, HttpType), 1.second))
  }

  it("Should be able to good assembly") {
    val conf = setupComponents
    val t1 = conf.getConfig(ASS1)

    val assInfo = parseAssembly(ASS1, t1)
    val c1 = Connection(ComponentId(HCD2A, HCD), AkkaType)
    val c2 = Connection(ComponentId(HCD2A, HCD), HttpType)
    val c3 = Connection(ComponentId(HCD2B, HCD), HttpType)
    assert(assInfo.get == AssemblyInfo(ASS1, "ass1.test", "csw.pkgDemo.Assembly1", RegisterAndTrackServices, Set(HttpType, AkkaType), Set(c1, c2, c3)))
  }

  it("Should create a new Ass") {
    val tp = TestProbe()
    val a1 = Supervisor(testAssemblyInfo)
    a1 ! LifecycleManager.Startup
    tp.expectNoMsg(5.seconds)
    a1 ! LifecycleManager.Shutdown

  }

  it("Should create a new Hcd") {
    val tp = TestProbe()
    val a1 = Supervisor(testHcdInfo)
    a1 ! LifecycleManager.Startup
    tp.expectNoMsg(5.seconds)
    a1 ! LifecycleManager.Shutdown

  }

  it("Should create a new Ass from config") {
    import scala.collection.JavaConversions._

    val setup2: Config = testParseStringConfig(t2)
    val conf = setup2.getConfig("container.components")
    val conf2 = conf.root.keySet().toList

    logger.info("Name: " + conf2)

    val cc = ContainerComponent.create(setup2).get

    // XXX allan: FIXME: expectNoMsg makes the tests run very slow
    val tp = TestProbe()
    tp.expectNoMsg(15.seconds)

    cc ! LifecycleToAll(Startup)
    tp.expectNoMsg(10.seconds)

    cc ! LifecycleToAll(Shutdown)
    tp.expectNoMsg(10.seconds)

  }

  it("Should be possible to create a container witha list of componentInfos") {
    val cc = ContainerComponent.create(testContainerInfo)

    val tp = TestProbe()
    tp.expectNoMsg(15.seconds)

    cc ! LifecycleToAll(Startup)
    tp.expectNoMsg(10.seconds)

    cc ! LifecycleToAll(Uninitialize)
    tp.expectNoMsg(10.seconds)
  }

  it("Should be possible to create and halt") {
    val cc2 = testContainerInfo.copy(componentInfos = List(testAssemblyInfo, testHcdInfo))
    val cc = ContainerComponent.create(cc2)

    val tp = TestProbe()
    tp.expectNoMsg(10.seconds)

    logger.info("Sending startup")
    cc ! LifecycleToAll(Startup)
    tp.expectNoMsg(10.seconds)

    cc ! Restart
    tp.expectNoMsg(20.seconds)
  }

}
