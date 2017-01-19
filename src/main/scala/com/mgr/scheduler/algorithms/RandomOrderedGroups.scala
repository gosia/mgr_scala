package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.datastructures.RoomTimes
import com.mgr.scheduler.docs
import com.mgr.scheduler.validators

case class RandomOrderedGroups() extends RandomBase {

  def orderGroups(groups: Seq[docs.Group], rt: RoomTimes): Future[Seq[docs.Group]] = {

    def mapF(group: docs.Group): (docs.Group, Int) = {
      val validRoomIds = validators.Room.getIds(group, rt.rooms)
      val validTermIds = validators.Term.getIds(group, rt.allTerms, rt.timetable, rt.teacherMap)

      val validRoomTimes = rt.remainingRoomTimes.filter({
        case (roomId, termId) => validRoomIds.contains(roomId) && validTermIds.contains(termId)
      })

      (group, validRoomTimes.size)
    }

    Future.value(groups.map(mapF).sortWith(_._2 < _._2).map(_._1))
  }

}
