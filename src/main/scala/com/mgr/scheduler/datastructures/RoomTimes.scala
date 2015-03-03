package com.mgr.scheduler.datastructures

import com.mgr.scheduler.docs
import com.mgr.utils.logging.Logging


case class RoomTimes(
  rooms: Seq[docs.Room],
  terms: Seq[docs.Term],
  timetable: Map[String, Seq[(String, String)]],
  allTerms: Set[String],
  teacherMap: Map[String, Set[String]],
  remainingRoomTimes: Seq[(String, String)]
) extends Logging {

  def transition(group: docs.Group, nextRoomTime: (String, String)): RoomTimes = {
    log.info(s"Transition ${group.getRealId} with $nextRoomTime")
    val newTimetable: Map[String, Seq[(String, String)]] = timetable.get(group._id) match {
      case None => timetable + (group._id -> Seq(nextRoomTime))
      case Some(_) => timetable.map({ case (k, v) => {
        k == group._id match {
          case true => (k, v ++ Seq(nextRoomTime))
          case false => (k, v)
        }
      }}).toMap
    }

    val newRemainingRoomTimes = remainingRoomTimes.filterNot(nextRoomTime == _)
    val newTeacherMap = teacherMap.map({
      case (id, terms) => group.teachers.toSet.contains(id) match {
        case true => (id, terms.filterNot(nextRoomTime._2 == _))
        case false => (id, terms)
      }
    }).toMap
    RoomTimes(
      rooms, terms, newTimetable, allTerms, newTeacherMap, newRemainingRoomTimes
    )
  }

  def getRemainingRoomTimesByNum(
    roomTimes: Seq[(String, String)], num: Int
  ): Seq[Seq[(String, String)]] = {
    RoomTimes.getRemainingRoomTimesByNum(terms, rooms, roomTimes, num: Int)
  }

}

object RoomTimes extends Logging {

  def groupTerms(terms: Seq[docs.Term], num: Int): Map[Int, Seq[Seq[docs.Term]]] = {

    val initMap = Map(1 -> terms.map(Seq(_)))

    (2 to num).foldLeft[Map[Int, Seq[Seq[docs.Term]]]](initMap)({case (m, i) => {
      val prevLayer: Seq[Seq[docs.Term]] = m(i - 1)
      val nextLayer: Seq[Seq[docs.Term]] = prevLayer map { prevGroup: Seq[docs.Term] => {
        val lastTerm: docs.Term = prevGroup.lastOption.get
        lastTerm.findNext(terms) map { t => prevGroup ++ Seq(t) }
      }} flatten

      m + (i -> nextLayer)
    }})
  }

  def getRemainingRoomTimesByNum(
    terms: Seq[docs.Term], rooms: Seq[docs.Room], remainingRoomTimes: Seq[(String, String)],
    num: Int
  ): Seq[Seq[(String, String)]] = {
    log.info(s"Getting remaining room times by num $num")
    val termsMap = terms.map(t => (t._id, t)).toMap
    val roomsMap: Map[String, Seq[docs.Term]] = rooms.map(
      r => r.terms.map(t => (r._id, termsMap(t)))
    ).flatten.groupBy(_._1).mapValues(
      _.filter(x => remainingRoomTimes.exists(y => y._1 == x._1 && y._2 == x._2._id)).map(_._2)
    )

    val initMap = 1 to num map { (_, Seq()) } toMap
    val mm = roomsMap
      .mapValues(groupTerms(_, num))
      .foldLeft[Map[Int, Seq[Seq[(String, String)]]]](initMap) {
        case (m, (roomId, termMap)) => {
          val extension: Map[Int, Seq[Seq[(String, String)]]] =
            termMap.mapValues(_.map(_.map(t => (roomId, t._id))))

          m map { case (k: Int, v: Seq[Seq[(String, String)]]) => extension.get(k) match {
            case None => (k, v)
            case Some(vv) => (k, v ++ vv)
          } } toMap
        }
      }
    log.info(s"$mm")
    mm(num)
  }

  def apply(
    rooms: Seq[docs.Room], terms: Seq[docs.Term], teachers: Seq[docs.Teacher]
  ): RoomTimes = {
    val timetable: Map[String, Seq[(String, String)]] = Map()
    val allTerms = terms.map(_._id).toSet
    val teacherMap: Map[String, Set[String]] = teachers.map(t => (t._id, t.terms.toSet)).toMap
    val remainingRoomTimes: Seq[(String, String)] =
      rooms.map(r => r.terms.map(t => (r._id, t))).flatten


    RoomTimes(
      rooms, terms, timetable, allTerms, teacherMap, remainingRoomTimes
    )
  }
}
