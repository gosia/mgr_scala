package com.mgr.scheduler.validators

import com.mgr.scheduler.docs
import com.mgr.utils.logging.Logging

object Room extends Logging {

  def validRoomCapacity(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    rooms.filter({r: docs.Room => group.students_num <= r.capacity }).map(_._id).toSet
  }

  def validRoomLabel(labels: Seq[String], rooms: Seq[docs.Room]): Set[String] = {
    rooms
      .filter({r: docs.Room => (r.labels intersect labels).nonEmpty })
      .map(_._id)
      .toSet
  }

  def getIds(group: docs.Group, rooms: Seq[docs.Room]): Seq[Set[String]] = {
    group.getRoomLabels.map({ labels =>
      validRoomCapacity(group, rooms) & validRoomLabel(labels, rooms)
    })

  }
}
