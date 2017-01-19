package com.mgr.scheduler

import org.specs2.mutable._

class ValidationUnitSpec extends SpecificationWithJUnit {

  "Room validation" should {

    "check room labels" in {

      val result = validators.Room.validRoomLabel(
        TestObjects.group1, Seq(TestObjects.room1, TestObjects.room2)
      )
      val expected = Set()

      result must_== expected
    }
  }
}
