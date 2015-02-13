package com.mgr.scheduler

import com.twitter.ostrich.stats.Stats
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
    case e =>
      log.warning(s"Unexpected exception - ${e.toString}:${e.getMessage}")
      // Exception messages which are null are not useful. Get the exception class instead.
      val message: String = if (e.getMessage != null) e.getMessage else e.toString
      throw e
  }

  def createConfig(
    id: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    Future.Unit handle exceptions
  }

}
