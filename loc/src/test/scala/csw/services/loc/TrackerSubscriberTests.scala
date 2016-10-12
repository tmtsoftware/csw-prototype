package csw.services.loc

///**
//  * TODO ...
//  */
//class TrackerSubscriberTests {
//
//}

//Here is a test case that assumes Alarm Service and lgsTromboneHCD are both running.
//
//it("should allow subscriptions") {
//
//  val fakeAssembly = TestProbe()
//  val ts = system.actorOf(TrackerSubscriber.props)
//
//  fakeAssembly.send(ts, Subscribe)
//
//  fakeAssembly.expectNoMsg(1.second)
//
//  fakeAssembly.send(ts, LocationService.TrackConnection(c1))
//
//  var msg = fakeAssembly.expectMsgClass(classOf[Location])
//  info("Msg: " + msg)
//
//  msg = fakeAssembly.expectMsgClass(classOf[Location])
//  info("Msg2: " + msg)
//
//  fakeAssembly.send(ts, LocationService.TrackConnection(c2))
//
//  msg = fakeAssembly.expectMsgClass(classOf[Location])
//  info("Msg: " + msg)
//
//  msg = fakeAssembly.expectMsgClass(classOf[Location])
//  info("Msg2: " + msg)
//}
