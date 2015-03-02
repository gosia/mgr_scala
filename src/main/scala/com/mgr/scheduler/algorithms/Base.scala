package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.docs
import com.mgr.utils.couch.Client

abstract class Base {
  val couchClient = Client("localhost", 6666, "scheduler")

  def start(task: docs.Task): Future[Unit]
}
