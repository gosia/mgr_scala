package com.mgr.scheduler

import org.specs2.mutable._

class RoomTimesItSpec extends SpecificationWithJUnit {

  "RoomTimes" should {

    "Init teacherMap" in {

      val t: Map[String, Set[String]] = TestObjects.roomTimes1.teacherMap

      val expected = Set(TestObjects.term1._id, TestObjects.term2._id)

      t(TestObjects.teacher1._id) must_== expected
    }

    "Init remainingRoomTimes" in {

      val expected = Seq(
        (TestObjects.room1._id, TestObjects.term1._id),
        (TestObjects.room1._id, TestObjects.term2._id)
      )

      TestObjects.roomTimes1.remainingRoomTimes must_== expected
    }
  }
}
