package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.algorithms
import com.mgr.scheduler.docs
import com.mgr.scheduler.validators
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.ViewResult
import com.mgr.utils.logging.Logging

object TaskHandler extends Logging with Couch {

  def getTaskResult(taskId: String): Future[scheduler.Timetable] = {
    log.info(s"Getting task results for task $taskId")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(doc) =>

        val timetable: Seq[docs.GroupRoomTerm] = doc.timetable getOrElse Seq()

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
    couchClient.get[docs.Task](taskId) map {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(doc) => scheduler.TaskStatus.valueOf(doc.status).get
    }
  }

  def createTask(configId: String, algorithm: scheduler.Algorithm): Future[String] = {
    log.info(s"Creating new task for config $configId and algorithm ${algorithm.name}")

    couchClient.exists(configId) flatMap {
      case false => throw scheduler.ValidationException(s"Przydział $configId nie istnieje")
      case true =>
        val doc = docs.Task(configId, algorithm)
        couchClient.add(doc) map { _ => doc._id}
    }
  }

  def startTask(taskId: String): Future[Unit] = {
    log.info(s"Starting task $taskId")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(doc) =>
        if (doc.status != scheduler.TaskStatus.NotStarted.name.toLowerCase) {
          throw scheduler.ValidationException(s"Zadanie już rozpoczęte")
        }

        couchClient.update(doc.startProcessing()) flatMap { _ =>
          couchClient.get[docs.Task](taskId) flatMap {
            case None => throw scheduler.SchedulerException("Coś poszło nie tak")
            case Some(updated) => algorithms.Dispatcher.start(updated)
          }
        }

    }
  }

  def deleteTask(taskId: String): Future[Unit] = {
    log.info(s"Deleting task $taskId")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(doc) => couchClient.delete[docs.Task](doc) map { _ => () }
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
    couchClient.get[docs.Task](taskId) map {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(doc) => doc.asTaskInfo
    }
  }

  def addEvent(
    taskId: String, groupId: String, point: scheduler.Point
  ): Future[scheduler.Timetable] = {
    log.info(s"Adding timetable for task $taskId, group $groupId, point $point")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(task) =>

        val termQ = couchClient.view("terms/by_config")
          .startkey(task.config_id)
          .endkey(task.config_id)
          .includeDocs
        val roomQ = couchClient.view("rooms/by_config")
          .startkey(task.config_id)
          .endkey(task.config_id)
          .includeDocs

        val groupCouchId = docs.Group.getCouchId(task.config_id, groupId)

        couchClient.get[docs.Group](groupCouchId) flatMap {
          case None => throw scheduler.ValidationException(s"Grupa $groupId nie istnieje")
          case Some(group) =>
            termQ.execute flatMap { termR: ViewResult =>
              val terms = termR.docs[docs.Term]
              val eventTerms = docs.Term.findManyByPoint(terms, point, group.terms_num).getOrElse(
                throw scheduler.ValidationException("Brak terminu")
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
                  throw scheduler.ValidationException("Brak wolnej sali w podanym terminie")
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

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(task) =>
        val (newDoc, removed) = task.removeGroupTimetable(
          docs.Group.getCouchId(task.config_id, groupId)
        )
        couchClient.update(newDoc) map { _ => docs.GroupRoomTerm.toThrift(removed) }
    }
  }

  def getGroupBusyTerms(taskId: String, groupId: String): Future[Seq[String]] = {
    log.info(s"Getting busy terms for group $groupId and task $taskId")

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(task) =>
        val configId = task.config_id
        val couchGroupId = docs.Group.getCouchId(configId, groupId)

        ConfigHandler.getConfigDefMap(configId) map {
          case (exGroups, exTeachers, exRooms, exTerms, exLabels) =>
            val group = exGroups.getOrElse(
              couchGroupId,
              throw scheduler.ValidationException(s"Grupa $groupId nie istnieje")
            )
            val allTerms = exTerms.keys.toSet

            val conflictingTimetable = task.timetable.map(_.filter(x => {
              val g = exGroups(x.group)
              g.teachers.toSet.intersect(group.teachers.toSet).nonEmpty
            })).getOrElse(Seq())

            val conflictingTermsForTeacher = group.teachers.map(t => {
              val teacher = exTeachers(t)
              allTerms -- teacher.terms.toSet
            }).flatten

            val validRoomsForGroup = validators.Room.getIds(group, exRooms.values.toSeq)
            val roomsCount = validRoomsForGroup.size
            val conflictingTermsForRooms: Seq[String] = task.timetable.map({ timetable =>

              // rooms busy by timetable
              val busyRoomsByTimetable = timetable.filter(x =>
                validRoomsForGroup.contains(x.room)
              ).foldLeft[Map[String, Set[String]]](Map())({ case (m, x) =>
                m.get(x.term) match {
                  case None => m + (x.term -> Set(x.room))
                  case Some(s) => m + (x.term -> (s + x.room))
                }
              })

              // add rooms busy by no valid terms
              val busyRooms: Map[String, Set[String]] = validRoomsForGroup.map(x => {
                val wrongTerms = allTerms -- exRooms(x).terms
                wrongTerms.map((_, x))
              }).flatten.foldLeft(busyRoomsByTimetable)({ case (m, x) =>
                m.get(x._1) match {
                  case None => m + (x._1 -> Set(x._2))
                  case Some(s) => m + (x._1 -> (s + x._2))
                }
              })

              busyRooms.mapValues(_.size).filter({ case (k, v) => v == roomsCount}).keys.toSeq
            }).getOrElse(Seq())

            val conflictingTermsForGroup = allTerms -- group.terms.toSet

            val conflictingTerms = (
              conflictingTermsForTeacher ++ conflictingTermsForRooms ++ conflictingTermsForGroup
            ).map(docs.Term.getRealId)

            (conflictingTimetable.map(x => docs.Term.getRealId(x.term)) ++ conflictingTerms)
              .toSet.toSeq
        }
    }
  }

}
