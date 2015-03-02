package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class GroupRoomTerm(
  group: String,
  room: String,
  term: String
)

final case class Task(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  status: String,
  timetable: Option[Seq[GroupRoomTerm]],

  `type`: String = Task.`type`
) extends Base {

  def finish(timetable: Map[String, (String, String)]): Task = {
    this.copy(
      status=scheduler.TaskStatus.Finished.name.toLowerCase,
      timetable=Some(
        timetable.toSeq.map({case (group, (room, term)) => GroupRoomTerm(group, room, term)})
      )
    )
  }

  def startProcessing(): Task = {
    this.copy(status=scheduler.TaskStatus.Processing.name.toLowerCase)
  }

}

object Task extends BaseObj {
  val `type`: String = "task"

  def apply(configId: String): Task = Task(
    _id = Task.getCouchId(configId, java.util.UUID.randomUUID.toString),
    config_id = configId,
    status = scheduler.TaskStatus.NotStarted.name.toLowerCase,
    timetable = None
  )
}
