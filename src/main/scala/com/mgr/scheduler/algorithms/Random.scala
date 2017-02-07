package com.mgr.scheduler.algorithms

import scala.util.{Random => ScalaRandom}

import com.twitter.util.Future

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs


case class Random() extends RandomBase {
  def orderGroups(groups: Seq[docs.Group], rt: RoomTimes): Future[Seq[docs.Group]] = {
    Future.value(ScalaRandom.shuffle(groups))
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
