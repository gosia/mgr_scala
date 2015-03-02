package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler

object Dispatcher extends Base {

  val classMapper = Map(
    scheduler.Algorithm.Random.name.toLowerCase -> Random
  )

  def start(task: docs.Task): Future[Unit] = {
    classMapper.get(task.algorithm).get().start(task)
  }
}
