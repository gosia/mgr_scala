package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import scala.util.{Random => ScalaRandom}

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers
import com.mgr.scheduler.validators
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging


abstract class RandomBase extends Base with Logging {
  def drawRandomRoomTimes(group: docs.Group, rt: RoomTimes): Seq[(String, String)] = {

    group.terms_num match {
      case 0 => Seq()
      case _ =>
        val validRoomIds = validators.Room.getIds(group, rt.rooms)
        val validTermIds = validators.Term.getIds(group, rt.allTerms, rt.timetable, rt.teacherMap)

        val validRoomTimes = rt.remainingRoomTimes.filter({
          case (roomId, termId) => validRoomIds.contains(roomId) && validTermIds.contains(termId)
        })

        if (validRoomTimes.isEmpty) {
          throw scheduler.SchedulerException("No room time to pick from!")
        }

        val validRoomTimesByNum: Seq[Seq[(String, String)]] =
          rt.getRemainingRoomTimesByNum(validRoomTimes, group.terms_num)

        if (validRoomTimesByNum.isEmpty) {
          throw scheduler.SchedulerException("No num room time to pick from!")
        }

        val newRoomTimes = validRoomTimesByNum(ScalaRandom.nextInt(validRoomTimesByNum.size))
        newRoomTimes
    }
  }

  def orderGroups(groups: Seq[docs.Group], rt: RoomTimes): Future[Seq[docs.Group]]

  def doWork(
    task: docs.Task,
    groups: Seq[docs.Group],
    teachers: Seq[docs.Teacher],
    rooms: Seq[docs.Room],
    terms: Seq[docs.Term],
    labels: Seq[docs.Label]
  ): Future[Unit] = {
    log.info(
      s"Got all required data: ${groups.length} groups, ${teachers.length} teachers, " +
        s"${rooms.length} rooms and ${terms.length} terms"
    )

    val rt = RoomTimes(rooms, terms, teachers)
    val lastRt = groups.foldLeft(rt) {
      case (oldRt, group) =>
        val newTimes = drawRandomRoomTimes(group, oldRt)
        newTimes.foldLeft(oldRt)({ case (newRt, newTime) => newRt.transition(group, newTime)})
    }
    val newDoc = task.finish(lastRt.timetable)
    couchClient.update(newDoc) map { _ => () }
  }

  def start(task: docs.Task): Future[Unit] = {
    log.info(s"Starting task ${task._id}")

    handlers.ConfigHandler.getConfigDef(task.config_id).map({
      case (groups, teachers, rooms, terms, labels) =>
        orderGroups(groups, RoomTimes(rooms, terms, teachers)) flatMap { orderedGroups =>
          doWork(task, orderedGroups, teachers, rooms, terms, labels)
        }
    })

  }

}
