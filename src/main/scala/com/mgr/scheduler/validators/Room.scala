package com.mgr.scheduler.validators

import com.mgr.scheduler.docs
import com.mgr.utils.logging.Logging

object Room extends Logging {

  def validRoomCapacity(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    val capacityMap = rooms.map(room => (room.capacity, room._id)).toMap
    val keys = capacityMap.keys.filter(group.students_num <= _)
    keys.map(capacityMap.get(_)).flatten.toSet
  }

  def validRoomLabel(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    val labelMap = rooms.map(
      room => room.labels.map(label => (label, room._id))
    ).flatten.toMap

    group.labels.map(labelMap.get(_)).flatten.toSet
  }

  def getIds(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    log.info(s"Getting valid room ids for group ${group._id}")
    validRoomCapacity(group, rooms) & validRoomLabel(group, rooms)
  }
}
