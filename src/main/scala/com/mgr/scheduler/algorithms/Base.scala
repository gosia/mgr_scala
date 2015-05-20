package com.mgr.scheduler.algorithms

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs

abstract class Base extends Couch {
  def start(task: docs.Task): Future[Unit]
}
