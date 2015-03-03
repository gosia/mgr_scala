package com.mgr.scheduler

import com.twitter.ostrich.admin._

trait AbstractSpec {
  lazy val env = RuntimeEnvironment(this, Array("-f", "config/test.scala"))
  lazy val scheduler = {
    val out = env.loadRuntimeConfig[SchedulerServiceImpl]()
    ServiceTracker.shutdown()
    out
  }
  implicit lazy val config = scheduler.config
}
