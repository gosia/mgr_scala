package com.mgr.scheduler
package config

import com.twitter.finagle.tracing.NullTracer
import com.twitter.finagle.tracing.Tracer
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.ostrich.admin.config._

import com.mgr.thrift.scheduler.SchedulerService

trait CouchConfig {
  val couchHost: String = "localhost"
  val couchPort: Int = 6666
}

trait RedisConfig {
  val redisHost = "localhost"
  val redisPort = 6379
}

class SchedulerServiceConfig extends ServerConfig[SchedulerService.ThriftServer] {
  val thriftPort: Int = 19001
  val tracerFactory: Tracer.Factory = NullTracer.factory

  val retryDelay: Int = 10

  def apply(runtime: RuntimeEnvironment): SchedulerServiceImpl = new SchedulerServiceImpl()(this)
}
