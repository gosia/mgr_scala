package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.algorithms
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.Client
import com.mgr.utils.logging.Logging

object TaskHandler extends Logging {

  val couchClient = Client("localhost", 6666, "scheduler")

  def getTaskResult(taskId: String): Future[scheduler.Timetable] = {
    log.info(s"Getting task results for task $taskId")

    couchClient.get[docs.Task](taskId) map { doc =>
      if (doc.status != scheduler.TaskStatus.Finished.name.toLowerCase) {
        throw scheduler.SchedulerException(s"Task is not finished (status - ${doc.status})")
      }

      doc.timetable map { timetable: Seq[docs.GroupRoomTerm] => scheduler.Timetable({
          timetable map { x => (
            x.group,
            scheduler.PlaceAndTime(
              term=docs.Term.getRealId(x.term), room=docs.Room.getRealId(x.room)
            )
          ) } toMap
        })
      } getOrElse(throw scheduler.SchedulerException("Document missing timetable"))

    }
  }

  def getTaskStatus(taskId: String): Future[scheduler.TaskStatus] = {
    log.info(s"Getting task status for task $taskId")
    couchClient.get[docs.Task](taskId) map { doc => scheduler.TaskStatus.valueOf(doc.status).get }
  }

  def createTask(configId: String, algorithm: scheduler.Algorithm): Future[String] = {
    log.info(s"Creating new task for config $configId and algorithm ${algorithm.name}")
    val doc = docs.Task(configId, algorithm)
    couchClient.add(doc) map { _ => doc._id}
  }

  def startTask(taskId: String): Future[Unit] = {
    log.info(s"Starting task $taskId")

    couchClient.get[docs.Task](taskId) map { doc =>
      if (doc.status != scheduler.TaskStatus.NotStarted.name.toLowerCase) {
        throw scheduler.SchedulerException(s"Task already started (status - ${doc.status})")
      }

      couchClient.update(doc.startProcessing()) map { _ =>
        couchClient.get[docs.Task](taskId) map { doc => {
          algorithms.Dispatcher.start(doc)
        }}
      }

    }
  }

}
