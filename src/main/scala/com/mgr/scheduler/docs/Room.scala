package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Room(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  terms: Seq[String],
  labels: Seq[String],

  `type`: String = "room"
) extends Base {

  def isValid(validTerms: Set[String], validLabels: Set[String]): Boolean =
    (terms.toSet - validTerms).isEmpty && (labels.toSet - validLabels).isEmpty

}

object Room {
  def apply(configId: String, room: scheduler.Room): Room = Room(
    _id = Base.getCouchId(configId, room.id),
    config_id = configId,
    terms = room.terms map { Base.getCouchId(configId, _) },
    labels = room.labels map { Base.getCouchId(configId, _) }
  )
}
