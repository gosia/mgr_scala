package com.mgr.scheduler

import com.twitter.logging.Logger
import com.twitter.util.Future

import com.mgr.scheduler.config.SchedulerServiceConfig
import com.mgr.thrift.scheduler

trait ExceptionsHandler {
  val log: Logger

  def exceptions: PartialFunction[Throwable, Nothing] = {
    case e @ (_: scheduler.SchedulerException | _: scheduler.ValidationException) => throw e
    case e =>
      val stacktrace = e.getStackTraceString
      val message = s"Unexpected exception - ${e.toString}:${e.getMessage}\n$stacktrace"
      log.warning(message)
      throw scheduler.SchedulerException(message)
  }
}


trait ConfigServiceImpl extends ExceptionsHandler {
  def createConfig(
    info: scheduler.ConfigCreationInfo,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    handlers.ConfigHandler.createConfig(info, terms, rooms, teachers, groups) handle exceptions
  }

  def getConfig(configId: String): Future[scheduler.Config] = {
    handlers.ConfigHandler.getConfig(configId) handle exceptions
  }

  def deleteConfig(configId: String): Future[Unit] = {
    handlers.ConfigHandler.deleteConfig(configId) handle exceptions
  }

  def getConfigs(): Future[Seq[scheduler.ConfigBasicInfo]] = {
    handlers.ConfigHandler.getConfigs() handle exceptions
  }

  def addConfigElement(
    configId: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    handlers.JoinedHandler.addConfigElement(
      configId, terms, rooms, teachers, groups
    ) handle exceptions
  }

  def editConfigElement(
    configId: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    handlers.JoinedHandler.editConfigElement(
      configId, terms, rooms, teachers, groups
    ) handle exceptions
  }

  def removeConfigElement(
    configId: String, elementId: String, elementType: String
  ): Future[Unit] = {
    handlers.JoinedHandler.removeConfigElement(configId, elementId, elementType) handle exceptions
  }

  def copyConfigElements(
    toConfigId: String, fromConfigId: String, elementsType: String)
  : Future[Unit] = {
    handlers.ConfigHandler.copyConfigElements(
      toConfigId, fromConfigId, elementsType
    ) handle exceptions
  }
}

trait TaskServiceImpl extends ExceptionsHandler {

  def getTasks(configIdOpt: Option[String]): Future[Seq[scheduler.TaskInfo]] = {
    handlers.TaskHandler.getTasks(configIdOpt) handle exceptions
  }

  def getTaskInfo(taskId: String): Future[scheduler.TaskInfo] = {
    handlers.TaskHandler.getTaskInfo(taskId) handle exceptions
  }

  def getTaskResult(taskId: String): Future[scheduler.Timetable] = {
    handlers.TaskHandler.getTaskResult(taskId) handle exceptions
  }

  def getTaskStatus(taskId: String): Future[scheduler.TaskStatus] = {
    handlers.TaskHandler.getTaskStatus(taskId) handle exceptions
  }

  def createTask(configId: String, algorithm: scheduler.Algorithm): Future[String] = {
    handlers.TaskHandler.createTask(configId, algorithm) handle exceptions
  }

  def startTask(taskId: String): Future[Unit] = {
    handlers.TaskHandler.startTask(taskId) handle exceptions
  }

  def deleteTask(taskId: String): Future[Unit] = {
    handlers.TaskHandler.deleteTask(taskId) handle exceptions
  }

  def addTaskEvent(
    taskId: String, groupId: String, point: scheduler.Point
  ): Future[scheduler.Timetable] = {
    handlers.TaskHandler.addEvent(taskId, groupId, point) handle exceptions
  }

  def removeTaskEvent(
    taskId: String, groupId: String
  ): Future[scheduler.Timetable] = {
    handlers.TaskHandler.removeEvent(taskId, groupId) handle exceptions
  }
}

trait FileServiceImpl extends ExceptionsHandler {
  def createFile(info: scheduler.FileCreationInfo): Future[scheduler.File] = {
    handlers.FileHandler.create(info) handle exceptions
  }
  def deleteFile(fileId: String): Future[Unit] = {
    handlers.FileHandler.delete(fileId) handle exceptions
  }
  def getFile(fileId: String): Future[scheduler.File] = {
    handlers.FileHandler.get(fileId) handle exceptions
  }
  def getFiles(): Future[Seq[scheduler.FileBasicInfo]] = {
    handlers.FileHandler.list() handle exceptions
  }

  def saveFile(fileId: String, content: String): Future[Unit] = {
    handlers.FileHandler.save(fileId, content) handle exceptions
  }

  def linkFile(fileId: String): Future[scheduler.FileBasicInfo] = {
    handlers.FileHandler.link(fileId) handle exceptions
  }
}

trait RatingServiceImpl extends ExceptionsHandler {
  def recountTaskRatingHelper(taskId: String): Future[Unit] = {
    handlers.RatingHandler.countRatingHelper(taskId) handle exceptions
  }

  def getTaskRatingHelper(taskId: String): Future[scheduler.TaskRatingHelper] = {
    handlers.RatingHandler.getRatingHelper(taskId) handle exceptions
  }

  def saveRating(rating: scheduler.Rating): Future[Unit] =
    handlers.RatingHandler.save(rating) handle exceptions

  def deleteRating(ratingId: String): Future[Unit] =
    handlers.RatingHandler.delete(ratingId) handle exceptions

  def getRating(ratingId: String): Future[scheduler.Rating] =
    handlers.RatingHandler.get(ratingId) handle exceptions

  def getRatings(): Future[Seq[scheduler.Rating]] =
    handlers.RatingHandler.list() handle exceptions
}

trait VotesServiceImpl extends ExceptionsHandler {
  def listConfigVotes(): Future[Seq[scheduler.UsersVotes]] = {
    handlers.VoteHandler.list() handle exceptions
  }

  def getConfigVotes(configId: String): Future[scheduler.UsersVotes] = {
    handlers.VoteHandler.get(configId) handle exceptions
  }

  def setConfigVotes(
    configId: String,
    votes: scala.collection.Map[String, scala.collection.Map[String, Short]]
  ): Future[Unit] = {
    handlers.VoteHandler.set(configId, votes.mapValues(_.toMap).toMap) handle exceptions
  }

  def deleteConfigVotes(configId: String): Future[Unit] = {
    handlers.VoteHandler.delete(configId) handle exceptions
  }
}

class SchedulerServiceImpl(
  implicit val config: SchedulerServiceConfig
) extends scheduler.SchedulerService.ThriftServer with ExceptionsHandler with ConfigServiceImpl
  with TaskServiceImpl with FileServiceImpl with RatingServiceImpl with VotesServiceImpl {
  val serverName = "Scheduler"
  val thriftPort = config.thriftPort
  override val tracerFactory = config.tracerFactory

  def getGroupBusyTerms(taskId: String, groupId: String): Future[Seq[String]] = {
    handlers.TaskHandler.getGroupBusyTerms(taskId, groupId) handle exceptions
  }

}
