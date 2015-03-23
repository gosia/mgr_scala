package com.mgr.scheduler.algorithms

import scala.util.{Random => ScalaRandom}

import com.twitter.util.Future

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers
import com.mgr.scheduler.validators
import com.mgr.utils.logging.Logging
import com.mgr.thrift.scheduler


case class Random() extends Base with Logging {
  def drawRandom(group: docs.Group, rt: RoomTimes): Seq[(String, String)] = {
    log.info(s"Drawing random number for group ${group._id}")

    group.terms_num match {
      case 0 => Seq()
      case _ =>
        val validRoomIds = validators.Room.getIds(group, rt.rooms)
        val validTermIds = validators.Term.getIds(group, rt.allTerms, rt.timetable, rt.teacherMap)

        val validRoomTimes = rt.remainingRoomTimes.filterNot({
          case (roomId, termId) => validRoomIds.contains(roomId) && validTermIds.contains(termId)
        })

        if (validRoomTimes.length == 0) {
          throw scheduler.SchedulerException("No room time to pick from!")
        }

        val validRoomTimesByNum: Seq[Seq[(String, String)]] =
          rt.getRemainingRoomTimesByNum(validRoomTimes, group.terms_num)

        if (validRoomTimesByNum.length == 0) {
          throw scheduler.SchedulerException("No num room time to pick from!")
        }

        val newRoomTimes = validRoomTimesByNum(ScalaRandom.nextInt(validRoomTimesByNum.size))
        log.info(s"Draw random values: $newRoomTimes")
        newRoomTimes
    }
  }

  def start(task: docs.Task): Future[Unit] = {
    log.info(s"Starting task ${task._id}")
    handlers.ConfigHandler.getConfigDef(task.config_id).flatMap({
      case (groups, teachers, rooms, terms, labels) =>
        log.info(
          s"Got all required data: ${groups.length} groups, ${teachers.length} teachers, " +
          s"${rooms.length} rooms and ${terms.length} terms"
        )

        val rt = RoomTimes(rooms, terms, teachers)
        val lastRt = groups.foldLeft(rt) {
          case (oldRt, group) =>
            val newTimes = drawRandom(group, oldRt)
            newTimes.foldLeft(oldRt)({ case (newRt, newTime) => newRt.transition(group, newTime)})
        }
        val newDoc = task.finish(lastRt.timetable)
        couchClient.update(newDoc) map { _ => () }
    })

  }

}
