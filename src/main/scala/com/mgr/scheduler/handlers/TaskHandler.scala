package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.algorithms
import com.mgr.scheduler.config.Config
import com.mgr.scheduler.docs
import com.mgr.scheduler.validators
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.Client
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object TaskHandler extends Logging {

  val couchClient = Client(Config.couchHost, Config.couchPort, "scheduler")

  def backendExceptions: PartialFunction[Throwable, Nothing] = {
    case e: scheduler.SchedulerException =>
      val message = s"Unexpected exception - ${e.toString}:${e.getMessage}\n"
      log.warning(message)
      throw e
    case e =>
      val stacktrace = e.getStackTraceString
      val message = s"Unexpected exception - ${e.toString}:${e.getMessage}\n$stacktrace"
      log.warning(message)
      throw scheduler.SchedulerException(message)
  }

  def getTaskResult(taskId: String): Future[scheduler.Timetable] = {
    log.info(s"Getting task results for task $taskId")

    couchClient.get[docs.Task](taskId) flatMap { doc =>
      if (doc.status != scheduler.TaskStatus.Finished.name.toLowerCase) {
        throw scheduler.SchedulerException(s"Task is not finished (status - ${doc.status})")
      }

      val timetable: Seq[docs.GroupRoomTerm] = doc.timetable getOrElse(
        throw scheduler.SchedulerException("Document missing timetable")
      )

      docs.GroupRoomTerm.toThriftString(doc.config_id, timetable) map { timetablestr: String =>
        scheduler.Timetable(
          docs.GroupRoomTerm.toThriftTimetable(timetable),
          timetablestr
        )
      }

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
          algorithms.Dispatcher.start(doc) handle backendExceptions
        }}
      }

    }
  }

  def deleteTask(taskId: String): Future[Unit] = {
    log.info(s"Deleting task $taskId")

    couchClient.get[docs.Task](taskId) flatMap { doc =>
      couchClient.delete[docs.Task](doc) map { _ => () }
    }
  }

  def getTasks(configIdOpt: Option[String]): Future[Seq[scheduler.TaskInfo]] = {
    log.info(s"Getting tasks for config $configIdOpt")

    val configIdsF: Future[Seq[String]] = configIdOpt match {
      case Some(configId) => Future.value(Seq(configId))
      case None =>
        val configIdsQuery = couchClient.view("utils/by_type").startkey("config").endkey("config")
        configIdsQuery.execute map { _.ids }
    }

    val queryTasksF: Future[Seq[Future[Seq[scheduler.TaskInfo]]]] = configIdsF map { configIds =>
      configIds map { configId => {
        val query = couchClient.view("tasks/by_config").startkey(configId).endkey(configId)
          .includeDocs

        query.execute map { result: ViewResult => result mapDocs {
          doc: docs.Task => doc.asTaskInfo
        }}
      }}
    }

    queryTasksF flatMap { queryTasks => {
      Future.collect(queryTasks) map { _.flatten }
    }}

  }

  def getTaskInfo(taskId: String): Future[scheduler.TaskInfo] = {
    log.info(s"Getting task info $taskId")
    couchClient.get[docs.Task](taskId) map { _.asTaskInfo }
  }

  def addEvent(
    taskId: String, groupId: String, point: scheduler.Point
  ): Future[scheduler.Timetable] = {
    log.info(s"Adding timetable for task $taskId, group $groupId, point $point")

    couchClient.get[docs.Task](taskId) flatMap { task =>

      val termQ = couchClient.view("terms/by_config")
        .startkey(task.config_id)
        .endkey(task.config_id)
        .includeDocs
      val roomQ = couchClient.view("rooms/by_config")
        .startkey(task.config_id)
        .endkey(task.config_id)
        .includeDocs

      val groupCouchId = docs.Group.getCouchId(task.config_id, groupId)

      couchClient.get[docs.Group](groupCouchId) flatMap { group =>
        termQ.execute flatMap { termR: ViewResult =>
          val terms = termR.docs[docs.Term]
          val eventTerms = docs.Term.findManyByPoint(terms, point, group.terms_num).getOrElse(
            throw scheduler.SchedulerException("No term to match")
          )
          val eventTermIdsSet = eventTerms.map(_.getRealId).toSet

          roomQ.execute flatMap { roomR: ViewResult =>
            val rooms = roomR.docs[docs.Room]

            val validRoomIds = validators.Room.getIds(group, rooms) filter { roomId =>
              task.timetable.map(xs =>
                !xs.exists(x => x.room == roomId && eventTermIdsSet.contains(x.term))
              ).getOrElse(true)
            }

            if (validRoomIds.size == 0) {
              throw scheduler.SchedulerException("No room to match")
            }
            val roomId = validRoomIds.head

            val newTimetableObjects = eventTermIdsSet.map(
              termId => docs.GroupRoomTerm(
                docs.Group.getCouchId(task.config_id, groupId),
                roomId,
                docs.Term.getCouchId(task.config_id, termId)
              )
            ).toSeq
            val newTask = task.extendTimetable(newTimetableObjects)
            couchClient.update(newTask) map {
              _ => docs.GroupRoomTerm.toThrift(newTimetableObjects)
            }
          }

        }
      }
    }
  }

  def removeEvent(taskId: String, groupId: String): Future[scheduler.Timetable] = {
    log.info(s"Deleting group $groupId timetable from task $taskId")

    couchClient.get[docs.Task](taskId) flatMap { task =>
      val (newDoc, removed) = task.removeGroupTimetable(
        docs.Group.getCouchId(task.config_id, groupId)
      )
      couchClient.update(newDoc) map { _ => docs.GroupRoomTerm.toThrift(removed) }
    }
  }

}
