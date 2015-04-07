package com.mgr.scheduler.validators

import com.mgr.scheduler.docs
import com.mgr.utils.logging.Logging

object Room extends Logging {

  def validRoomCapacity(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    val capacityMap = rooms.map(room => (room.capacity, room._id)).toMap
    val keys = capacityMap.keys.filter(group.students_num <= _)
    keys.map(capacityMap.get).flatten.toSet
  }

  def validRoomLabel(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    val labelMap: Map[String, Seq[String]] = rooms.map(
      room => room.labels.map(label => (label, room._id))
    ).flatten.groupBy(_._1).mapValues(_.map(x => x._2))

    group.labels.map(labelMap.get).flatten.flatten.toSet
  }

  def getIds(group: docs.Group, rooms: Seq[docs.Room]): Set[String] = {
    validRoomCapacity(group, rooms) & validRoomLabel(group, rooms)
  }
}
