package com.mgr.scheduler.docs

import com.twitter.util.Future

import com.mgr.scheduler.handlers.ConfigHandler
import com.mgr.thrift.scheduler

final case class GroupRoomTerm(
  group: String,
  room: String,
  term: String
)

object GroupRoomTerm {

  def toThriftTimetable(
    timetable: Seq[GroupRoomTerm]
  ): Map[String, scheduler.PlaceAndTime] = {
    timetable map { x => (
      x.group,
      scheduler.PlaceAndTime(
        term=Term.getRealId(x.term), room=Room.getRealId(x.room)
      )
      ) } toMap
  }

  def toThriftString(configId: String, timetable: Seq[GroupRoomTerm]): Future[String] = {

    val timetableMap = timetable.map(x => (x.group, x)).toMap

    ConfigHandler.getConfigDefMap(configId) map {
      case (groupMap, teacherMap, roomMap, termMap) => {

        def toTxtTimetable(group: Group, room: Room, term: Term): String = {
          s"${term.toTxt}, ${group.toTxt}, ${room.toTxt}"
        }

        def ppTeacher(teacherId: String): String = {
          teacherMap.get(teacherId).get.toTxt
        }

        def ppRoom(roomId: String): String = {
          roomMap.get(roomId).get.toTxt
        }

        def formatValues(data: Map[String, Seq[String]]) = {
          data.mapValues(_.map(groupId => {
            val t: GroupRoomTerm = timetableMap.get(groupId).get
            val room = roomMap.get(t.room).get
            val term = termMap.get(t.term).get
            val group = groupMap.get(t.group).get
            (group, room, term)
          }).sortBy(_._3._id).map(x => toTxtTimetable(x._1, x._2, x._3)))
        }

        val byTeachers: String = formatValues(groupMap.map({
          case (groupId, group: Group) => {
            group.teachers map { case t => (t, groupId) }
          } }).flatten.toSeq.groupBy(_._1).mapValues(_.map(_._2))
        ).map({
          case (teacherId, rt) => s"${ppTeacher(teacherId)}\n${rt.mkString("\n")}"
        }).mkString("\n\n\n")

        val byRooms: String = formatValues(
          timetable.map(x => {
            (x.room, x.group)
          }).groupBy(_._1).mapValues(_.map(_._2))
        ).map({
          case (roomId, rt) => s"${ppRoom(roomId)}\n${rt.mkString("\n")}"
        }).mkString("\n\n\n")

        s"=== TEACHERS ===\n\n$byTeachers\n\n=== ROOMS ===\n\n$byRooms"
    }}
  }

}
