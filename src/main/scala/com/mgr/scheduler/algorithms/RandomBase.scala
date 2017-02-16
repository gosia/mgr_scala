package com.mgr.scheduler.algorithms

import scala.util.{Random => ScalaRandom}

import com.twitter.util.Future

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers
import com.mgr.scheduler.validators
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging


abstract class RandomBase extends Base with Logging {

  def getValidRoomTimes(group: docs.Group, rt: RoomTimes): Seq[Seq[(String, String)]] = {
    val validRoomIdsGroups: Seq[Set[String]] = validators.Room.getIds(group, rt.rooms)
    val validTermIds = validators.Term.getIds(group, rt.allTerms, rt.timetable, rt.teacherMap)

    validRoomIdsGroups.map({ validRoomIds =>
      rt.remainingRoomTimes.filter({
        case (roomId, termId) => validRoomIds.contains(roomId) && validTermIds.contains(termId)
      })
    })
  }

  def getValidRoomTimesByNum(group: docs.Group, rt: RoomTimes): Seq[Seq[(String, String)]] = {

    val validRoomTimesGroups: Seq[Seq[(String, String)]] = getValidRoomTimes(group, rt)

    if (validRoomTimesGroups.isEmpty || validRoomTimesGroups.exists(_.isEmpty)) {
      throw scheduler.SchedulerException("No room time to pick from!")
    }

    val validRoomTimesByNumGroups: Seq[Seq[Seq[(String, String)]]] = validRoomTimesGroups.map({
      validRoomTimes => rt.getRemainingRoomTimesByNum(validRoomTimes, group.terms_num)
    })

    if (validRoomTimesByNumGroups.isEmpty || validRoomTimesByNumGroups.exists(_.isEmpty)) {
      throw scheduler.SchedulerException("No num room time to pick from!")
    }

    validRoomTimesByNumGroups.reduceLeft[Seq[Seq[(String, String)]]] {
      case (prev, validRoomTimesByNum: Seq[Seq[(String, String)]]) =>

        validRoomTimesByNum.flatMap({ validRoomTimeByNum: Seq[(String, String)] =>
          val currentTermIds = validRoomTimeByNum.map(_._2).toSet
          val canAddTo: Seq[Seq[(String, String)]] = prev.filter({
            xs => xs.map(_._2).toSet.diff(currentTermIds).isEmpty
          })
          canAddTo.map({ _ ++ validRoomTimeByNum})
        })
    }

  }

  def orderGroups(groups: Seq[docs.Group], rt: RoomTimes): Future[Seq[docs.Group]]
  def getRoomTimes(
    group: docs.Group, rt: RoomTimes,
    taskId: String,
    groupsMap: Map[String, docs.Group],
    teachersMap: Map[String, docs.Teacher],
    roomsMap: Map[String, docs.Room],
    termsMap: Map[String, docs.Term],
    labelsMap: Map[String, docs.Label]
  ): Seq[(String, String)]

  def drawRoomTimes(group: docs.Group, rt: RoomTimes): Seq[(String, String)] = {

    group.terms_num match {
      case 0 => Seq()
      case _ =>
        val validRoomTimesByNum = getValidRoomTimesByNum(group, rt)
        validRoomTimesByNum(ScalaRandom.nextInt(validRoomTimesByNum.size))
    }
  }

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
        val newTimes = getRoomTimes(
          group, oldRt, task._id,
          groups.map { x => (x._id, x)} toMap,
          teachers.map { x => (x._id, x)} toMap,
          rooms.map { x => (x._id, x)} toMap,
          terms.map { x => (x._id, x)} toMap,
          labels.map { x => (x._id, x)} toMap
        )
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
