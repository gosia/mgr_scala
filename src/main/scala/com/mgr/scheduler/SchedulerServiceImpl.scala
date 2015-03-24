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

  def getConfigs(): Future[Seq[scheduler.ConfigBasicInfo]] = {
    handlers.ConfigHandler.getConfigs() handle exceptions
  }

  def addConfigElement(
    configId: String,
    mode: scheduler.Mode,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    handlers.ConfigHandler.addConfigElement(configId, terms, rooms, teachers, groups) handle exceptions
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

}
