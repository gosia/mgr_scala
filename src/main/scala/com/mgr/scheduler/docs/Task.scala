package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Task(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  status: String,

  `type`: String = Task.`type`
) extends Base

object Task extends BaseObj {
  val `type`: String = "task"

  def apply(configId: String): Task = Task(
    _id = Task.getCouchId(configId, java.util.UUID.randomUUID.toString),
    config_id = configId,
    status = scheduler.TaskStatus.NotStarted.name.toLowerCase
  )
}
