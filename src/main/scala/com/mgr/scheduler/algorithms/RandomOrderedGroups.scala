package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs
import com.mgr.scheduler.validators

case class RandomOrderedGroups() extends RandomBase {

  def orderGroups(groups: Seq[docs.Group], rt: RoomTimes): Future[Seq[docs.Group]] = {

    def mapF(group: docs.Group): (docs.Group, Int) = {

      val validRoomTimesByNum = getValidRoomTimesByNum(group, rt)

      (group, validRoomTimesByNum.size)
    }

    Future.value(groups.map(mapF).sortWith(_._2 < _._2).map(_._1))
  }

  def getRoomTimes(
    group: docs.Group, rt: RoomTimes,
    taskId: String,
    groupsMap: Map[String, docs.Group],
    teachersMap: Map[String, docs.Teacher],
    roomsMap: Map[String, docs.Room],
    termsMap: Map[String, docs.Term],
    labelsMap: Map[String, docs.Label]
  ): Seq[(String, String)] = drawRoomTimes(group, rt)

}
