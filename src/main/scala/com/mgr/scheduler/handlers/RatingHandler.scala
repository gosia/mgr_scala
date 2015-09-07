package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging

object RatingHandler extends Logging  with Couch {


  def getRating(taskId: String): Future[Int] = {

    couchClient.get[docs.Task](taskId) flatMap {
      case None => throw scheduler.ValidationException(s"Zadanie $taskId nie istnieje")
      case Some(task) =>

        val configId = task.config_id
        val timetable: Seq[docs.GroupRoomTerm] = task.timetable getOrElse Seq()



        Future.value(1)
    }
  }

}
