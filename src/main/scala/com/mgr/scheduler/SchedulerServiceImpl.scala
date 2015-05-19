package com.mgr.scheduler

import com.twitter.util.Future

import com.mgr.scheduler.config.SchedulerServiceConfig
import com.mgr.thrift.scheduler

class SchedulerServiceImpl(
  implicit val config: SchedulerServiceConfig
) extends scheduler.SchedulerService.ThriftServer {
  val serverName = "Scheduler"
  val thriftPort = config.thriftPort
  override val tracerFactory = config.tracerFactory

  def exceptions: PartialFunction[Throwable, Nothing] = {
    case e: scheduler.SchedulerException => throw e
    case e =>
      val stacktrace = e.getStackTraceString
      val message = s"Unexpected exception - ${e.toString}:${e.getMessage}\n$stacktrace"
      log.warning(message)
      throw scheduler.SchedulerException(message)
  }

  def createConfig(
    info: scheduler.ConfigBasicInfo,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    handlers.ConfigHandler.createConfig(info, terms, rooms, teachers, groups) handle exceptions
  }

  def getConfigInfo(configId: String): Future[scheduler.ConfigInfo] = {
    handlers.ConfigHandler.getConfigInfo(configId) handle exceptions
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
    handlers.ConfigHandler.addConfigElement(
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
    handlers.ConfigHandler.editConfigElement(
      configId, terms, rooms, teachers, groups
    ) handle exceptions
  }

  def removeConfigElement(
    configId: String, elementId: String, elementType: String
  ): Future[Unit] = {
    handlers.ConfigHandler.removeConfigElement(configId, elementId, elementType) handle exceptions
  }

  def copyConfigElements(
    toConfigId: String, fromConfigId: String, elementsType: String)
  : Future[Unit] = {
    handlers.ConfigHandler.copyConfigElements(
      toConfigId, fromConfigId, elementsType
    ) handle exceptions
  }

  def importData(configId: String, data: String): Future[Unit] = {
    handlers.ConfigHandler.importData(configId, data) handle exceptions
  }

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

  def getGroupBusyTerms(taskId: String, groupId: String): Future[Seq[String]] = {
    handlers.TaskHandler.getGroupBusyTerms(taskId, groupId) handle exceptions
  }

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

}
