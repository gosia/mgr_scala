package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Task(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  status: String,
  timetable: Option[Seq[GroupRoomTerm]],

  algorithm: String,

  `type`: String = Task.`type`
) extends Base {

  def finish(timetable: Map[String, Seq[(String, String)]]): Task = {
    this.copy(
      status=scheduler.TaskStatus.Finished.name.toLowerCase,
      timetable=Some(
        timetable.toSeq.map({
          case (group, xs) => xs.map({
            case (room, term) => GroupRoomTerm(group, room, term)
          })
        }).flatten
      )
    )
  }

  def startProcessing(): Task = {
    this.copy(status=scheduler.TaskStatus.Processing.name.toLowerCase)
  }

  def asTaskInfo: scheduler.TaskInfo = scheduler.TaskInfo(
    this._id,
    this.config_id,
    scheduler.TaskStatus.valueOf(this.status).get,
    scheduler.Algorithm.valueOf(this.algorithm).get
  )

}

object Task extends BaseObj {
  val `type`: String = "task"

  def apply(configId: String, algorithm: scheduler.Algorithm): Task = Task(
    _id = Task.getCouchId(configId, java.util.UUID.randomUUID.toString),
    config_id = configId,
    status = scheduler.TaskStatus.NotStarted.name.toLowerCase,
    timetable = None,
    algorithm = algorithm.name.toLowerCase
  )
}
