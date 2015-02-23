package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.couch.Client
import com.mgr.utils.couch.CouchResponse
import com.mgr.utils.logging.Logging

object TaskHandler extends Logging {

  val couchClient = Client("localhost", 6666, "scheduler")

  def getTaskResult(taskId: String): Future[scheduler.Timetable] = ???

  def getTaskStatus(taskId: String): Future[scheduler.TaskStatus] = {
    couchClient.get[docs.Task](taskId) map { doc => scheduler.TaskStatus.valueOf(doc.status).get }
  }

  def startTask(configId: String, algorithm: scheduler.Algorithm): Future[String] = {
    val doc = docs.Task(configId)
    couchClient.add(doc) map { _ => doc._id}
  }

}
