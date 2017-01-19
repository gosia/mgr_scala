package com.mgr.scheduler

import com.mgr.thrift.scheduler

object TestObjects {

  val configId = "test"

  val sterm1: scheduler.Term = scheduler.Term(
    "term1id",
    scheduler.Time(12, 15),
    scheduler.Time(13, 0),
    scheduler.Day.Mon
  )
  val sterm2: scheduler.Term = scheduler.Term(
    "term1id",
    scheduler.Time(13, 15),
    scheduler.Time(14, 0),
    scheduler.Day.Mon
  )

  val term1: docs.Term = docs.Term(configId, sterm1)
  val term2: docs.Term = docs.Term(configId, sterm2)

  val sroom1: scheduler.Room = scheduler.Room(
    id="room1id",
    terms=List(sterm1.id, sterm2.id),
    labels=List("cwiczenia", "wyklad"),
    capacity=200
  )
  val sroom2: scheduler.Room = scheduler.Room(
    id="room2id",
    terms=List(sterm1.id, sterm2.id),
    labels=List("pracownia"),
    capacity=30
  )
  val room1: docs.Room = docs.Room(configId, sroom1)
  val room2: docs.Room = docs.Room(configId, sroom2)

  val steacher1 = scheduler.Teacher(
    id="teacher1id",
    terms=List(sterm1.id, sterm2.id),
    extra = scheduler.TeacherExtra("", "", 0, "")
  )
  val teacher1 = docs.Teacher(configId, steacher1)

  val roomTimes1 = datastructures.RoomTimes(List(room1), List(term1, term2), List(teacher1))

  val sgroup1 = scheduler.Group(
    id="group1id",
    teachers=Seq(teacher1._id),
    terms=Seq(term1._id, term2._id),
    labels=Seq("wyklad"),
    diffTermGroups = Seq(),
    sameTermGroups = Seq(),
    termsNum = 2,
    studentsNum = 10,
    extra = scheduler.GroupExtra(course="", groupType="", notes="")
  )
  val group1 = docs.Group(configId, sgroup1)

}
