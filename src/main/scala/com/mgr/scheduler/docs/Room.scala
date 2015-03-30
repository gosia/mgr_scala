package com.mgr.scheduler.docs

import com.mgr.thrift.scheduler

final case class Room(
  _id: String,
  _rev: Option[String] = None,
  config_id: String,

  terms: Seq[String],
  labels: Seq[String],
  capacity: Int,

  `type`: String = Room.`type`
) extends Base {

  def isValid(validTerms: Set[String], validLabels: Set[String]): (Option[String], Boolean) = {
    if ((terms.toSet -- validTerms).isEmpty && (labels.toSet -- validLabels).isEmpty) {
      (None, true)
    } else {
      val unknownTerms = (terms.toSet -- validTerms).mkString(", ")
      val unknownLabels = (labels.toSet -- validLabels).mkString(", ")
      (
        Some(
          s"Room $getRealId is not valid " +
          s"(unknown labels: <$unknownLabels>, unknown terms: <$unknownTerms>)"
        ), false
      )
    }
  }

  def toTxt: String = s"room $getRealId"

  def asThrift: scheduler.Room = scheduler.Room(
    id=this.getRealId,
    terms=this.terms.map(Term.getRealId(_)),
    labels=this.labels.map(Label.getRealId(_)),
    capacity=this.capacity.toShort
  )
}

object Room extends BaseObj {
  val `type`: String = "room"

  def apply(configId: String, room: scheduler.Room): Room = Room(
    _id = Room.getCouchId(configId, room.id),
    config_id = configId,
    terms = room.terms map { Term.getCouchId(configId, _) },
    labels = room.labels map { Label.getCouchId(configId, _) },
    capacity = room.capacity
  )
}
