//package csw.examples.vsliceJava.assembly
//
//import akka.actor.{ActorRef, ActorSystem, PoisonPill}
//import akka.testkit.{TestKit, TestProbe}
//import com.typesafe.scalalogging.slf4j.LazyLogging
//import csw.services.ccs.CommandStatus2._
//import csw.services.ccs.SequentialExecution.SequentialExecutor
//import csw.services.ccs.SequentialExecution.SequentialExecutor.{ExecuteOne, StartTheSequence}
//import csw.services.loc.ConnectionType.AkkaType
//import csw.services.loc.LocationService
//import csw.services.pkg.Component.{DoNotRegister, HcdInfo}
//import csw.services.pkg.Supervisor3
//import csw.services.pkg.Supervisor3.{LifecycleInitialized, LifecycleRunning}
//import csw.services.pkg.SupervisorExternal.{LifecycleStateChanged, SubscribeLifecycleCallback}
//import csw.util.config.Configurations
//import csw.util.config.Configurations.SetupConfig
//
//import scala.concurrent.duration._
//
//object CommandHandlerTests {
//  LocationService.initInterface()
//  val system = ActorSystem("TromboneAssemblyCommandHandlerTests")
//}
//
///**
// * TMT Source Code: 9/21/16.
// */
//class CommandHandlerTests extends TestKit(CommandHandlerTests.system)
//    with FunSpecLike with ShouldMatchers with BeforeAndAfterAll with LazyLogging {
//
//  override def afterAll = TestKit.shutdownActorSystem(system)
//
//  val ac = AssemblyTestData.TestAssemblyContext
//
//  def setupState(ts: TromboneState) = {
//    // These times are important to allow time for test actors to get and process the state updates when running tests
//    expectNoMsg(20.milli)
//    system.eventStream.publish(ts)
//    // This is here to allow the destination to run and set its state
//    expectNoMsg(20.milli)
//  }
//
//  def startHCD: ActorRef = {
//    val testInfo = HcdInfo(
//      TromboneHCD.componentName,
//      TromboneHCD.trombonePrefix,
//      TromboneHCD.componentClassName,
//      DoNotRegister, Set(AkkaType), 1.second
//    )
//
//    Supervisor3(testInfo)
//  }
//
//  def newCommandHandler(tromboneHCD: ActorRef, allEventPublisher: Option[ActorRef] = None) = {
//    //val thandler = TestActorRef(TromboneCommandHandler.props(configs, tromboneHCD, allEventPublisher), "X")
//    //thandler
//    system.actorOf(TromboneCommandHandler.props(ac, tromboneHCD, allEventPublisher))
//  }
//
//  it("should allow running datum directly to CommandHandler") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    ch ! TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
//
//    val sc = SetupConfig(ac.datumCK)
//
//    ch ! ExecuteOne(sc, Some(fakeAssembly.ref))
//
//    val msg = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandStatus2])
//    println("Final: " + msg)
//
//    // Demonstrate error
//    ch ! TromboneState(cmdItem(cmdUninitialized), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
//    ch ! ExecuteOne(sc, Some(fakeAssembly.ref))
//
//    val errMsg = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandStatus2])
//    val monitor = TestProbe()
//    monitor.watch(ch)
//    ch ! PoisonPill
//    monitor.expectTerminated(ch)
//  }
//
//  it("should allow running datum through SequentialExecutor") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveUnindexed), sodiumItem(false), nssItem(false)))
//
//    val sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(ac.datumCK))
//
//    val se = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//
//    se ! StartTheSequence(ch)
//
//    val msg = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    println("Final: " + msg)
//
//    // Demonstrate error
//    ch ! TromboneState(cmdItem(cmdUninitialized), moveItem(moveUnindexed), sodiumItem(false), nssItem(false))
//
//    val se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se2 ! StartTheSequence(ch)
//
//    val errMsg = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    errMsg.overall should equal(Incomplete)
//    errMsg.details.results.head._1 shouldBe a[NoLongerValid]
//    println("Final: " + errMsg)
//
//    val monitor = TestProbe()
//    monitor.watch(ch)
//    monitor.watch(tromboneHCD)
//    system.stop(ch) // ch ! PoisonPill
//    monitor.expectTerminated(ch)
//    system.stop(tromboneHCD) //tromboneHCD ! PoisonPill
//    monitor.expectTerminated(tromboneHCD, 4.seconds)
//  }
//
//  it("should allow running move") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    //expectNoMsg(100.milli)
//
//    val testPosition = 90.0
//    val sca = Configurations.createSetupConfigArg("testobsId", ac.moveSC(testPosition))
//
//    val se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se2 ! StartTheSequence(ch)
//
//    fakeAssembly.expectMsgClass(35.seconds, classOf[CommandResult])
//    val finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testPosition)
//
//    // Use the engineering GetAxisUpdate to get the current encoder for checking
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    val upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    upd.current should equal(finalPos)
//    ch ! PoisonPill
//    system.stop(tromboneHCD)
//    system.stop(se2)
//  }
//
//  it("should allow running a move without sequence") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    //expectNoMsg(100.milli)
//
//    val testPosition = 90.0
//    ch ! ExecuteOne(ac.moveSC(testPosition), Some(fakeAssembly.ref))
//
//    fakeAssembly.expectMsgClass(35.seconds, classOf[CommandStatus2])
//    val finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testPosition)
//
//    // Use the engineering GetAxisUpdate to get the current encoder for checking
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    val upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    upd.current should equal(finalPos)
//    ch ! PoisonPill
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow two moves") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    val pos1 = 86.0
//    val pos2 = 150.1
//
//    val sca = Configurations.createSetupConfigArg("testobsId", ac.moveSC(pos1), ac.moveSC(pos2))
//
//    val se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se2 ! StartTheSequence(ch)
//
//    val finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, pos2)
//
//    val msg = fakeAssembly.expectMsgClass(35.seconds, classOf[CommandResult])
//    info(s"result: $msg")
//
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    val upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    upd.current should equal(finalPos)
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow a move with a stop") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    val pos1 = 150.1
//
//    val sca = Configurations.createSetupConfigArg("testobsId", ac.moveSC(pos1))
//
//    val se = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se ! StartTheSequence(ch)
//
//    Configurations.createSetupConfigArg("testobsId", SetupConfig(ac.stopCK))
//    Thread.sleep(20) // This is an arbitrary time to get things going before sending stop
//
//    // This won't work
//    //val se2 = system.actorOf(SequentialExecutor.props(sca2, Some(fakeAssembly.ref)))
//    //se2 ! StartTheSequence(ch)
//
//    // This will also work
//    //  se ! StopCurrentCommand
//    ch ! SetupConfig(ac.stopCK)
//
//    val msg = fakeAssembly.expectMsgClass(35.seconds, classOf[CommandResult])
//    msg.overall shouldBe Incomplete
//    msg.details.status(0) shouldBe Cancelled
//    info(s"result: $msg")
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow a single position command") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    val testRangeDistance = 94.0
//    val positionConfig = ac.positionSC(testRangeDistance)
//    info("Position: " + positionConfig)
//    val sca = Configurations.createSetupConfigArg("testobsId", positionConfig)
//
//    val se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se2 ! StartTheSequence(ch)
//
//    fakeAssembly.expectMsgClass(5.seconds, classOf[CommandResult])
//    val finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testRangeDistance)
//
//    // Use the engineering GetAxisUpdate to get the current encoder
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    val upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    upd.current should equal(finalPos)
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow a set of positions for the fun of it") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    // This will send a config arg with 10 position commands
//    val testRangeDistance = 90 to 180 by 10
//    val positionConfigs = testRangeDistance.map(f => ac.positionSC(f))
//
//    val sca = Configurations.createSetupConfigArg("testobsId", positionConfigs: _*)
//
//    val se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se2 ! StartTheSequence(ch)
//
//    fakeAssembly.expectMsgClass(5.seconds, classOf[CommandResult])
//
//    // Test
//    val finalPos = Algorithms.stagePositionToEncoder(ac.controlConfig, testRangeDistance.last)
//    // Use the engineering GetAxisUpdate to get the current encoder for checking
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    val upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    upd.current should equal(finalPos)
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should get error for setAngle and setElevation when not following") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(false), nssItem(false)))
//
//    val sca = Configurations.createSetupConfigArg("testobsId", ac.setAngleSC(22.0))
//
//    var se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//
//    //    ch ! ExecSequential(sca, tromboneHCD, Some(fakeAssembly.ref))
//    se2 ! StartTheSequence(ch)
//
//    val errMsg = fakeAssembly.expectMsgClass(35.seconds, classOf[CommandResult])
//    errMsg.overall should equal(Incomplete)
//    errMsg.details.results.head._1 shouldBe a[Invalid]
//
//    val sca2 = Configurations.createSetupConfigArg("testobsId", ac.setElevationSC(96.0))
//    se2 = system.actorOf(SequentialExecutor.props(sca2, Some(fakeAssembly.ref)))
//
//    se2 ! StartTheSequence(ch)
//
//    val errMsg2 = fakeAssembly.expectMsgClass(35.seconds, classOf[CommandResult])
//    errMsg2.overall should equal(Incomplete)
//    errMsg2.details.results.head._1 shouldBe a[Invalid]
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow follow and a stop") {
//
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    // set the state so the command succeeds
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(false)))
//
//    //fakeAssembly.expectNoMsg(30.milli)
//    val sca = Configurations.createSetupConfigArg("testobsId", ac.followSC(false), SetupConfig(ac.stopCK))
//    val se = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se ! StartTheSequence(ch)
//
//    val msg2 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    info("Msg: " + msg2)
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow follow, with setElevation, SetAngle and stop") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    // I'm sending this event to the follower so I know its state so I can check the final result
//    // to see that it moves the stage to the right place when sending a new elevation
//    val testFocusError = 0.0
//    val testElevation = 100.0
//    val initialZenithAngle = 0.0
//
//    // set the state so the command succeeds
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(false)))
//
//    //fakeAssembly.expectNoMsg(30.milli)
//    var sca = Configurations.createSetupConfigArg("testobsId", ac.followSC(false))
//    val se = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se ! StartTheSequence(ch)
//
//    var totalRangeDistance = Algorithms.focusZenithAngleToRangeDistance(ac.calculationConfig, testElevation, testFocusError, initialZenithAngle)
//    var stagePosition = Algorithms.rangeDistanceToStagePosition(totalRangeDistance)
//    var expectedEncoderValue = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition)
//    logger.info(s"Expected for setElevation: $expectedEncoderValue")
//
//    val msg = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    logger.info(">>>>>>>Msg: " + msg)
//
//    sca = Configurations.createSetupConfigArg("testobsId", ac.setElevationSC(testElevation))
//    val se2 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se2 ! StartTheSequence(ch)
//
//    val msg2 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    logger.info("Msg3: " + msg2)
//
//    // Use the engineering GetAxisUpdate to get the current encoder for checking
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    var upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    upd.current should equal(expectedEncoderValue)
//
//    fakeAssembly.expectNoMsg(2.seconds)
//    val testZenithAngle = 30.0
//    sca = Configurations.createSetupConfigArg("testobsId", ac.setAngleSC(testZenithAngle))
//    val se3 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se3 ! StartTheSequence(ch)
//
//    totalRangeDistance = Algorithms.focusZenithAngleToRangeDistance(ac.calculationConfig, testElevation, testFocusError, testZenithAngle)
//    stagePosition = Algorithms.rangeDistanceToStagePosition(totalRangeDistance)
//    expectedEncoderValue = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition)
//    logger.info(s"Expected for setAngle: $expectedEncoderValue")
//
//    val msg3 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    logger.info("Msg3: " + msg3)
//
//    // Use the engineering GetAxisUpdate to get the current encoder for checking
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    //logger.info(s"Upd2: $upd")
//    upd.current should equal(expectedEncoderValue)
//
//    sca = Configurations.createSetupConfigArg("testobsId", SetupConfig(ac.stopCK))
//    val se4 = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se4 ! StartTheSequence(ch)
//
//    val msg4 = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    logger.info("Msg: " + msg4)
//    fakeAssembly.expectNoMsg(1.seconds)
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//  it("should allow follow, with setElevation, SetAngle and stop as a single sequence") {
//    val tromboneHCD = startHCD
//    val fakeAssembly = TestProbe()
//
//    // The following is to synchronize the test with the HCD entering Running state
//    // This is boiler plate for setting up an HCD for testing
//    tromboneHCD ! SubscribeLifecycleCallback(fakeAssembly.ref)
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleInitialized))
//    fakeAssembly.expectMsg(LifecycleStateChanged(LifecycleRunning))
//    //info("Running")
//
//    val ch = newCommandHandler(tromboneHCD)
//
//    // I'm sending this event to the follower so I know its state so I can check the final result
//    // to see that it moves the stage to the right place when sending a new elevation
//    val testFocusError = 0.0
//    val testElevation = 100.0
//    //val initialZenithAngle = 0.0
//    val testZenithAngle = 30.0
//
//    // set the state so the command succeeds
//    setupState(TromboneState(cmdItem(cmdReady), moveItem(moveIndexed), sodiumItem(true), nssItem(false)))
//
//    //fakeAssembly.expectNoMsg(30.milli)
//    val sca = Configurations.createSetupConfigArg("testobsId", ac.followSC(false), ac.setElevationSC(testElevation), ac.setAngleSC(testZenithAngle), SetupConfig(ac.stopCK))
//    val se = system.actorOf(SequentialExecutor.props(sca, Some(fakeAssembly.ref)))
//    se ! StartTheSequence(ch)
//
//    val msg = fakeAssembly.expectMsgClass(10.seconds, classOf[CommandResult])
//    logger.info(">>>>>>>Msg: " + msg)
//
//    fakeAssembly.expectNoMsg(2.seconds)
//
//    val totalRangeDistance = Algorithms.focusZenithAngleToRangeDistance(ac.calculationConfig, testElevation, testFocusError, testZenithAngle)
//    val stagePosition = Algorithms.rangeDistanceToStagePosition(totalRangeDistance)
//    val expectedEncoderValue = Algorithms.stagePositionToEncoder(ac.controlConfig, stagePosition)
//    logger.info(s"Expected for setAngle: $expectedEncoderValue")
//
//    // Use the engineering GetAxisUpdate to get the current encoder for checking
//    fakeAssembly.send(tromboneHCD, GetAxisUpdateNow)
//    val upd = fakeAssembly.expectMsgClass(classOf[AxisUpdate])
//    logger.info(s"Upd2: $upd")
//
//    // Cleanup
//    system.stop(ch)
//    system.stop(tromboneHCD)
//  }
//
//}
