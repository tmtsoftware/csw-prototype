package csw.services.pkg

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}
import com.typesafe.scalalogging.slf4j.LazyLogging
import csw.services.loc.LocationService
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

}

class ContainerComponentTests extends TestKit(ContainerComponentTests.system) with ImplicitSender
    with FunSpecLike with Matchers with LazyLogging with BeforeAndAfterAll {

  import ContainerComponent._

  override def afterAll = TestKit.shutdownActorSystem(system)

  private val ASS1 = "Assembly-1"
  private val HCD2A = "HCD-2A"
  private val HCD2B = "HCD-2B"
  //private val BAD1 = "BAD1"
  private val BAD2 = "BAD2"
  private val CONTAINERNAME = "Container-2"

  def newTestHcd() = {
    val component = system.actorOf(Props(
      new Actor with Hcd with LifecycleHandler {
        def receive = lifecycleHandlerReceive
      }), "LifecycleHandlerTester1")
    component
  }

  def newTestAssembly() = {
    val component = system.actorOf(Props(
      new Actor with Assembly with LifecycleHandler {
        def receive = lifecycleHandlerReceive
      }), "LifecycleHandlerTester1")
    component
  }

  def testAssemblyInfo: AssemblyInfo = {
    val name = "test1"
    val prefix = "test1.prefix"
    val className = "csw.services.pkg.ContainerComponentTests$SimpleTestAssembly"

    AssemblyInfo(name, prefix, className, RegisterOnly, Set(AkkaType), Set.empty[Connection])
  }

  def testHcdInfo: HcdInfo = {
    val name = "test1Hcd"
    val prefix = "test1.Hcd.prefix"
    val className = "csw.services.pkg.ContainerComponentTests$SimpleTestHcd"

    HcdInfo(name, prefix, className, RegisterOnly, Set(AkkaType), 1.second)
  }

  def testContainerInfo: ContainerInfo = {
    ContainerInfo("TestContainer",
      RegisterOnly,
      Set(AkkaType),
      DEFAULT_INITIAL_DELAY,
      DEFAULT_CREATION_DELAY,
      DEFAULT_LIFECYCLE_DELAY,
      List(testAssemblyInfo, testHcdInfo))
  }

  val t1 =
    """
     container {
      |  name = "Container-1"
      |  components {
      |    Assembly-1 {
      |      type = Assembly
      |      class = csw.pkgDemo.Assembly1
      |      prefix = ass1.test
      |      conntype: [http, akka]
      |      connections = [
      |        // Component connections used by this component
      |        // Name: ComponentType ConnectionType
      |        {
      |          name: HCD-2A
      |          type: HCD
      |          conntype: [akka, http]
      |        }
      |        {
      |          name: HCD-2B
      |          type: HCD
      |          conntype: [http]
      |        }
      |      ]
      |    }
      |    HCD-2A {
      |      type = HCD
      |      class = csw.pkgDemo.hcd2
      |      prefix = tcs.mobie.blue.filter
      |      conntype: [akka]
      |      rate = 1 second
      |    }
      |    HCD-2B {
      |      type: HCD
      |      class: csw.pkgDemo.hcd2
      |      prefix: tcs.mobie.blue.disperser
      |      conntype: [http, akka]
      |      rate: 1 second
      |    }
      |    BAD1 {
      |      type: BAD
      |      class: csw.pkgDemo.hcd2
      |      prefix: ""
      |      conntype: [http, akkax]
      |      rate: 1 seconds
      |    }
      |    BAD2 {
      |      type: BAD
      |      // no class
      |      prefixx: ""
      |      conntype: [http, akkax]
      |      rate: 11 turkey
      |    }
      |  }
      |}
    """.stripMargin

  val t2 =
    """
     container {
      |  name = "Container-2"
      |  conntype: [akka]
      |  initialdelay = 2 second
      |  creationdelay = 1 second
      |  lifecycledelay = 3 seconds
      |  components {
      |     Assembly-1 {
      |      type = Assembly
      |      class = "csw.services.pkg.ContainerComponentTests$SimpleTestAssembly"
      |      prefix = ass1.test
      |      conntype: [akka]
      |      connections = [
      |        // Component connections used by this component
      |        // Name: ComponentType ConnectionType
      |        {
      |          name: HCD-2A
      |          type: HCD
      |          conntype: [akka]
      |        }
      |      ]
      |      }
      |      HCD-2A {
      |        type = HCD
      |        class = "csw.services.pkg.ContainerComponentTests$SimpleTestHcd"
      |        prefix = tcs.mobie.blue.filter
      |        conntype: [akka]
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

    intercept[ConfigurationParsingException] {
      parseClassName(BAD2, t1)
    }
    intercept[ConfigurationParsingException] {
      parseComponentId(BAD2, t1)
    }
    intercept[ConfigurationParsingException] {
      parsePrefix(BAD2, t1)
    }
    intercept[ConfigurationParsingException] {
      parseConnType(BAD2, t1)
    }
    // Bad units will result in 1 second
    assert(parseRate(BAD2, t1) == 1.second)
  }

  it("Should have container fields") {
    val setup1: Config = testParseStringConfig(t2)
    val setup = setup1.getConfig(CONTAINER)
    //logger.info("setup: " + setup)

    val containerName = parseName(CONTAINERNAME, setup)

    assert(containerName == CONTAINERNAME)

    assert(parseDuration(containerName, CREATION_DELAY, setup, 5.seconds) == 1.seconds)
    assert(parseDuration(containerName, INITIAL_DELAY, setup, 5.seconds) == 2.seconds)
    assert(parseDuration(containerName, LIFECYCLE_DELAY, setup, 5.seconds) == 3.seconds)

    val connType = parseConnType(containerName, setup)
    assert(connType == Set(AkkaType))

    // This one has no delays so should report defaults
    val setup2: Config = testParseStringConfig(t1).getConfig(CONTAINER)
    val containerName2 = parseName("Container-1", setup2)

    assert(containerName2 == "Container-1")
    assert(parseDuration(containerName2, CREATION_DELAY, setup2, DEFAULT_CREATION_DELAY) == DEFAULT_CREATION_DELAY)
    assert(parseDuration(containerName2, INITIAL_DELAY, setup2, DEFAULT_INITIAL_DELAY) == DEFAULT_INITIAL_DELAY)
    assert(parseDuration(containerName2, LIFECYCLE_DELAY, setup2, DEFAULT_LIFECYCLE_DELAY) == DEFAULT_LIFECYCLE_DELAY)
    // should catch default conntype
    intercept[ConfigurationParsingException] {
      parseConnType(containerName2, setup2)
    }
    // Also
    assert(parseConnTypeWithDefault(containerName2, setup2, Set(AkkaType)) == Set(AkkaType))
  }

  it("Should handle individual pieces going well") {

    val conf = setupComponents
    val t1 = conf.getConfig(HCD2A)

    assert(parseClassName(HCD2A, t1) == "csw.pkgDemo.hcd2")

    assert(parseComponentId(HCD2A, t1) == ComponentId("HCD-2A", HCD))

    assert(parsePrefix(HCD2A, t1) == "tcs.mobie.blue.filter")

    assert(parseConnType(HCD2A, t1) == Set(AkkaType))

    assert(parseRate(HCD2A, t1) == 1.second)

  }

  it("Should handle HcdInfo") {
    val conf = setupComponents
    val t1 = conf.getConfig(HCD2A)

    val hcdInfo = parseHcd(HCD2A, t1)
    assert(hcdInfo.get == HcdInfo(HCD2A, "tcs.mobie.blue.filter", "csw.pkgDemo.hcd2", RegisterOnly, Set(AkkaType), 1.second))
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

    val tp = TestProbe()
    //nval a1 = Supervisor(assInfo.get)
    //a1 ! LifecycleManager.Startup
    tp.expectNoMsg(15.seconds)
    //a1 ! LifecycleManager.Shutdown

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
