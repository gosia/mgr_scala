package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers.RatingHandler
import com.mgr.thrift.scheduler

object Dispatcher extends Base {

  val classMapper = Map(
    scheduler.Algorithm.Random.name.toLowerCase -> Random,
    scheduler.Algorithm.RandomOrderedGroups.name.toLowerCase -> RandomOrderedGroups
  )

  def start(task: docs.Task): Future[Unit] = {
    classMapper.get(task.algorithm).get().start(task) map { _ =>
      RatingHandler.countRatingHelper(task._id)
    }
  }
}
