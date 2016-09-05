package csw.examples.vsliceJava.assembly;

//import akka.actor.{ActorRef, ActorSystem, Props}
//import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
//import csw.services.loc.Connection
//import csw.services.loc.ConnectionType.AkkaType
//import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister}
//import csw.services.pkg.Supervisor3._
//import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
//
//import scala.concurrent.duration._
//
///**
//  * TMT Source Code: 8/23/16.
//  */
//class TromboneAssemblyBasicTests extends TestKit(ActorSystem("TromboneTests")) with ImplicitSender
//  with FunSpecLike with ShouldMatchers with BeforeAndAfterAll {
//
//  override def afterAll = TestKit.shutdownActorSystem(system)
//
//  val troboneAssemblyPrefix = "nfiraos.ncc.trombone"
//
//  val testInfo = AssemblyInfo(TromboneAssembly.componentName,
//    TromboneAssembly.componentPrefix,
//    TromboneAssembly.componentClassName,
//    DoNotRegister, Set(AkkaType), Set.empty[Connection])
//
//  def getTromboneProps(assemblyInfo: AssemblyInfo, supervisorIn: Option[ActorRef]): Props = {
//    supervisorIn match {
//      case None => TromboneAssembly.props(assemblyInfo, TestProbe().ref)
//      case Some(actorRef) => TromboneAssembly.props(assemblyInfo, actorRef)
//    }
//  }
//
//  def newTrombone(assemblyInfo: AssemblyInfo = testInfo): (TestProbe, ActorRef) = {
//    val supervisor = TestProbe()
//    val props = getTromboneProps(assemblyInfo, Some(supervisor.ref))
//    (supervisor, system.actorOf(props))
//  }
//
//  def newTestTrombone(assemblyInfo: AssemblyInfo = testInfo): (TestProbe, TestActorRef[TromboneAssembly]) = {
//    val supervisor = TestProbe()
//    val props = getTromboneProps(assemblyInfo, Some(supervisor.ref))
//    (supervisor, TestActorRef(props))
//  }
//
//  def lifecycleStart(supervisor: TestProbe, tla: ActorRef): Unit = {
//    supervisor.expectMsg(Initialized)
//    supervisor.expectMsg(Started)
//
//    supervisor.send(tla, Running)
//  }
//
//  describe("low-level instrumented trombone assembly tests") {
//
//    it("should get initialized with configs from files (same as AlgorithmData") {
//      val (_, tla) = newTestTrombone()
//
//      tla.underlyingActor.controlConfig.minElevation should be(AlgorithmData.TestControlConfig.minElevation)
//      tla.underlyingActor.controlConfig.positionScale should be(AlgorithmData.TestControlConfig.positionScale)
//      tla.underlyingActor.controlConfig.minElevationEncoder should be(AlgorithmData.TestControlConfig.minElevationEncoder)
//
//      tla.underlyingActor.calculatorConfig.defaultInitialElevation should be(AlgorithmData.TestCalculationConfig.defaultInitialElevation)
//      tla.underlyingActor.calculatorConfig.focusErrorGain should be(AlgorithmData.TestCalculationConfig.focusErrorGain)
//      tla.underlyingActor.calculatorConfig.lowerFocusLimit should be(AlgorithmData.TestCalculationConfig.lowerFocusLimit)
//      tla.underlyingActor.calculatorConfig.upperFocusLimit should be(AlgorithmData.TestCalculationConfig.upperFocusLimit)
//      tla.underlyingActor.calculatorConfig.zenithFactor should be(AlgorithmData.TestCalculationConfig.zenithFactor)
//
//      expectNoMsg(5.seconds)
//    }
//
//    it("should lifecycle properly with a fake supervisor") {
//      val (supervisor, tla) = newTestTrombone()
//
//      supervisor.expectMsg(Initialized)
//      supervisor.expectMsg(Started)
//
//      supervisor.send(tla, Running)
//
//      supervisor.send(tla, DoShutdown)
//      supervisor.expectMsg(ShutdownComplete)
//
//    }
//
//    it("should work with the supervisor lifecycle") {
//      val (sup, tla) = newTestTrombone()
//
//      val fakeSupervisor = TestProbe()
//
//      fakeSupervisor.send(tla, SubscribeLifecycleCallback(fakeSupervisor.ref))
//      fakeSupervisor.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//      fakeSupervisor.expectMsg(LifecycleStateChanged(LifecycleRunning))
//
//      expectNoMsg(5.seconds)
//    }
//  }
//}
