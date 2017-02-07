package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs
import com.mgr.scheduler.docs.TaskRating
import com.mgr.scheduler.handlers.RatingHandler
import com.mgr.scheduler.validators

case class DecideWithRatingFunction(rating: docs.Rating) extends RandomBase {

  def orderGroups(groups: Seq[docs.Group], rt: RoomTimes): Future[Seq[docs.Group]] = {

    def mapF(group: docs.Group): (docs.Group, Int) = {
      val validRoomIds = validators.Room.getIds(group, rt.rooms)
      val validTermIds = validators.Term.getIds(group, rt.allTerms, rt.timetable, rt.teacherMap)

      val validRoomTimes = rt.remainingRoomTimes.filter({
        case (roomId, termId) => validRoomIds.contains(roomId) && validTermIds.contains(termId)
      })

      (group, validRoomTimes.size)
    }

    Future.value(groups.map(mapF).sortWith(_._2 < _._2).map(_._1))
  }

  def getRoomTimes(
    group: docs.Group, rt: RoomTimes,
    taskId: String,
    groupsMap: Map[String, docs.Group],
    teachersMap: Map[String, docs.Teacher],
    roomsMap: Map[String, docs.Room],
    termsMap: Map[String, docs.Term],
    labelsMap: Map[String, docs.Label]
  ): Seq[(String, String)] = {

    group.terms_num match {
      case 0 => Seq()
      case _ =>
        val validRoomTimesByNum: Seq[Seq[(String, String)]] = getValidRoomTimesByNum(group, rt)

        val validRts: Seq[(RoomTimes, Seq[(String, String)], Double)] = validRoomTimesByNum.map({
          newTimes: Seq[(String, String)] => {
            val validRt: RoomTimes = newTimes.foldLeft(rt)({
              case (newRt, newTime) => newRt.transition(group, newTime)
            })
            val timetable = validRt.getDocsTimetable

            val taskRatingHelper = RatingHandler.countRatingHelperDoc(
              taskId, timetable, groupsMap, teachersMap, roomsMap, termsMap, labelsMap
            )

            val points = TaskRating(
              rating, taskRatingHelper, timetable, termsMap
            ).getRating()

            (validRt, newTimes, points)
          }
        })

        validRts.maxBy(_._3)._2

    }

  }

}
