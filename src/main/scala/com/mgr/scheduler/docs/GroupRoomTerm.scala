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
  ): Map[String, Seq[scheduler.PlaceAndTime]] = {
    byGroupId(timetable) mapValues { _.map(x => scheduler.PlaceAndTime(
      term=Term.getRealId(x.term), room=Room.getRealId(x.room)
    ))}
  }

  def byGroupId(timetable: Seq[GroupRoomTerm]): Map[String, Seq[GroupRoomTerm]] = {
    timetable.foldLeft[Map[String, Seq[GroupRoomTerm]]](Map())({ case (m, x) =>
      val groupKey = Group.getRealId(x.group)
      m.get(groupKey) match {
        case None => m + (groupKey -> Seq(x))
        case Some(_) => m.map({case (k, v) => k == groupKey match {
          case true => (k, v ++ Seq(x))
          case false => (k, v)
        }}).toMap
      }
    })
  }

  def toThriftString(configId: String, timetable: Seq[GroupRoomTerm]): Future[String] = {

    val timetableMap = byGroupId(timetable)

    ConfigHandler.getConfigDefMap(configId) map {
      case (groupMap, teacherMap, roomMap, termMap, _) => {

        def toTxtTimetable(group: Group, room: Room, term: Term, teachers: Seq[Teacher]): String = {
          val teacherStr = teachers.map(_.toTxt).mkString(", ")
          s"${term.toTxt}; ${group.toTxt}; ${room.toTxt}; $teacherStr"
        }

        def ppTeacher(teacherId: String): String = {
          teacherMap.get(teacherId).get.toTxt
        }

        def ppRoom(roomId: String): String = {
          roomMap.get(roomId).get.toTxt
        }

        def formatValues(data: Map[String, Seq[String]]) = {
          data.mapValues(_.map(groupId => {
            val tx: Seq[GroupRoomTerm] = timetableMap.get(Group.getRealId(groupId)).get
            tx.map { t => {
              val room = roomMap.get(t.room).get
              val term = termMap.get(t.term).get
              val group = groupMap.get(t.group).get
              val teachers = groupMap.get(t.group).get.teachers.map(teacherMap(_))
              (group, room, term, teachers)
            } }
          }).flatten.sortBy(_._3._id).map(x => toTxtTimetable(x._1, x._2, x._3, x._4)))
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
          }).groupBy(_._1).mapValues(_.map(_._2).toSet.toSeq)
        ).map({
          case (roomId, rt) => s"${ppRoom(roomId)}\n${rt.mkString("\n")}"
        }).mkString("\n\n\n")

        s"=== TEACHERS ===\n\n$byTeachers\n\n=== ROOMS ===\n\n$byRooms"
    }}
  }

}
