package com.mgr.scheduler.datastructures

import com.mgr.scheduler.docs
import com.mgr.utils.logging.Logging


case class RoomTimes(
  rooms: Seq[docs.Room],
  timetable: Map[String, (String, String)],
  allTerms: Set[String],
  teacherMap: Map[String, Set[String]],
  remainingRoomTimes: Seq[(String, String)]
  //remainingRoomTimesByNum: Map[Int, Seq[Seq[(String, String)]]]
) {

  def transition(group: docs.Group, nextRoomTime: (String, String)): RoomTimes = {
    val newTimetable = timetable + (group._id -> nextRoomTime)
    val newRemainingRoomTimes = remainingRoomTimes.filterNot(nextRoomTime == _)
    val newTeacherMap = teacherMap.map({
      case (id, terms) => group.teachers.toSet.contains(id) match {
        case true => (id, terms.filterNot(nextRoomTime._2 == _))
        case false => (id, terms)
      }
    }).toMap
    RoomTimes(
      rooms, newTimetable, allTerms, newTeacherMap, newRemainingRoomTimes
    )
  }

}

object RoomTimes extends Logging {
  def apply(
    rooms: Seq[docs.Room], terms: Seq[docs.Term], teachers: Seq[docs.Teacher]
  ): RoomTimes = {
    val timetable: Map[String, (String, String)] = Map()
    val allTerms = terms.map(_._id).toSet
    val teacherMap: Map[String, Set[String]] = teachers.map(t => (t._id, t.terms.toSet)).toMap
    val remainingRoomTimes: Seq[(String, String)] =
      rooms.map(r => r.terms.map(t => (r._id, t))).flatten

    RoomTimes(rooms, timetable, allTerms, teacherMap, remainingRoomTimes)
  }
}
