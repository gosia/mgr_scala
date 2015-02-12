package com.mgr.scheduler

import com.twitter.logging.Logger
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.ostrich.admin.ServiceTracker

import com.mgr.thrift.scheduler

object Main {
  private val log = Logger.get(getClass)

  def main(args: Array[String]) {
    val runtime = RuntimeEnvironment(this, args)
    val server = runtime.loadRuntimeConfig[scheduler.SchedulerService.ThriftServer]()
    try {
      log.info("Starting services")
      server.start()
    } catch {
      case e: Exception =>
        log.error(e, "Failed starting services, exiting")
        ServiceTracker.shutdown()
        System.exit(1)
    }
  }
}
