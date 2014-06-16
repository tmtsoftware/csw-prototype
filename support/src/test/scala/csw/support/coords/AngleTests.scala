package csw.support.coords

import org.scalatest.{Matchers, FunSpec}


class AngleTests extends FunSpec with Matchers {


  /**
   * Tests for ValueData
   */
  describe("Basic ValueData tests") {
    import Angle._

    it("it should parse separate ra/dec with spaces to combined ra/dec") {
      (Angle.parseRa("20 54 05.689"), Angle.parseDe("+37 01 17.38")) should equal(Angle.parseRaDe("20 54 05.689 +37 01 17.38"))
    }
    it("it should parse separate ra/dec with colons to combined ra/dec") {
      (Angle.parseRa("10:12:45.3"), Angle.parseDe("-45:17:50")) should equal(Angle.parseRaDe("10:12:45.3-45:17:50"))
    }
    it("it should parse separate ra/dec with no seconds or spaces to combined ra/dec") {
      (Angle.parseRa("15h17m"), Angle.parseDe("-11d10m")) should equal(Angle.parseRaDe("15h17m-11d10m"))
    }
    it("it should parse separate ra/dec with d/m/s in string to combined ra/dec") {
      (Angle.parseRa("275d11m15.6954s"), Angle.parseDe("+17d59m59.876s")) should equal(Angle.parseRaDe("275d11m15.6954s+17d59m59.876s"))
    }
    it("it should parse separate ra/dec implicit arcHour degree to combined ra/dec") {
      (12.34567.arcHour, -17.87654d.degree) should equal(Angle.parseRaDe("12.34567h-17.87654d"))
    }
    it("it should parse separate ra/dec implicit degree and degree to combined ra/dec") {
      (350.123456.degree, -17.33333.degree) should equal(Angle.parseRaDe("350.123456d-17.33333d"))
    }
    it("it should parse separate ra/dec implicit degree and degree to combined ra/dec with no units") {
      (350.123456.degree, -17.33333.degree) should equal(Angle.parseRaDe("350.123456 -17.33333"))
    }
  }

  describe("Test uas parsing") {
    it("check micro arcsec ra conversion to angle") {
      Angle.parseRa("1", "2", "3").uas should equal(1l * 15l * 60l * 60l * 1000l * 1000l + 2l * 15l * 60l * 1000l * 1000l + 3l * 15l * 1000l * 1000l)
    }
    it("check micro arcsec dec conversion to angle") {
      Angle.parseDe("+", "1", "2", "3").uas should equal(1l * 60l * 60l * 1000l * 1000l + 2l * 60l * 1000l * 1000l + 3l * 1000l * 1000l)
    }
    it("check micro arcsec ra conversion to angle as hms") {
      Angle.parseRa("1h2m3s").uas should equal(1l * 15l * 60l * 60l * 1000l * 1000l + 2l * 15l * 60l * 1000l * 1000l + 3l * 15l * 1000l * 1000l)
    }
    it("check micro arcsec ra conversion to angle as space sep hr and min") {
      Angle.parseRa("02 51.2").uas should equal(2l * 15l * 60l * 60l * 1000l * 1000l + 512l * 15l * 60l * 1000l * 100l)
    }
    it("check micro arcsec dec conversion to angle as deg min sec") {
      Angle.parseDe("+1d2'3\"").uas should equal(1l * 60l * 60l * 1000l * 1000l + 2l * 60l * 1000l * 1000l + 3l * 1000l * 1000l)
    }
    it("check micro arcsec dec conversion to angle as deg min sec with negative") {
      Angle.parseDe("-1d2'3\"").uas should equal(-(1l * 60l * 60l * 1000l * 1000l + 2l * 60l * 1000l * 1000l + 3l * 1000l * 1000l))
    }
    it("check micro arcsec dec conversion to angle as space seperated with sign") {
      Angle.parseDe("+13 12").uas should equal(13l * 60l * 60l * 1000l * 1000l + 12l * 60l * 1000l * 1000l)
    }
  }

  describe("Test Conversions") {
    it("Testing various conversions") {
      Angle.D2R * 1d should equal(math.toRadians(1d))
      Angle.R2D * 1d should equal(math.toDegrees(1d))
      Angle.H2D * 1d should equal(15d)
      Angle.D2H * 1d should equal(1d / 15d)
      Angle.D2M should equal(60d)
      Angle.M2D should equal(1d / 60d)
      Angle.D2S should equal(3600d)
      Angle.S2D should equal(1d / 3600d)
      Angle.H2R * 1d should equal(math.toRadians(15d))
      Angle.R2H * math.toRadians(15d) should equal(1d)
      Angle.M2R * 60d should equal(math.toRadians(1d))
      Angle.R2M * math.toRadians(1d) should equal(60d)
      Angle.Mas2R should equal(Angle.D2R / 3600000d)
      Angle.R2Mas should equal(1d / Angle.Mas2R)
    }
  }

  describe("Test Distance") {
    Angle.distance(Angle.D2R * 1d, 0d, Angle.D2R * 2d, 0d) should equal(Angle.D2R * 1d)
    Angle.distance(0, Angle.D2R * 90d, Angle.D2R * 180d, -(Angle.D2R * 90d)) should equal(Angle.D2R * 180d)
  }

  describe("Test Ra To String") {
    Angle.raToString(Angle.H2R * 11) should be("11h")
    Angle.raToString(Angle.H2R * 11 + Angle.H2R * 12 / 60) should be("11h 12m")
    Angle.raToString(Angle.H2R * 11 + Angle.H2R * 12 / 60 + Angle.H2R * 13 / 3600) should be("11h 12m 13s")
    Angle.raToString(Angle.H2R * 11 + Angle.H2R * 12 / 60 + Angle.H2R * 13.3 / 3600) should be("11h 12m 13.3s")
  }

  describe("Test Dec to String") {
    Angle.deToString(Angle.D2R * 11) should equal("11" + Angle.DEGREE_SIGN)
    Angle.deToString(Angle.D2R * 11 + Angle.M2R * 12) should equal("11" + Angle.DEGREE_SIGN + "12'")
    Angle.deToString(Angle.D2R * 11 + Angle.M2R * 12 + Angle.S2R * 13) should equal("11" + Angle.DEGREE_SIGN + "12'13\"")
    Angle.deToString(Angle.D2R * 11 + Angle.M2R * 12 + Angle.S2R * 13.3) should equal("11" + Angle.DEGREE_SIGN + "12'13.3\"")
    Angle.deToString(-(Angle.D2R * 11 + Angle.M2R * 12)) should equal("-11" + Angle.DEGREE_SIGN + "12'")
  }
}

