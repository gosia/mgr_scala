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

  // dbs
  val recipeDb = "recipes"
  val recipeVersionDb = "recipe_versions"
  val recipeTransDb = "recipe_trans"

  val ingredientDb = "ingredient"
  val ingredientVersionDb = "ingredient_versions"
  val ingredientTransDb = "ingredient_trans"

  val skillDb = "skill"
  val skillVersionDb = "skill_versions"
  val skillTransDb = "skill_trans"
}

trait RedisConfig {
  val redisHost = "localhost"
  val redisPort = 6379
}

class SchedulerServiceConfig extends ServerConfig[SchedulerService.ThriftServer]
  with CouchConfig with RedisConfig
{
  val thriftPort: Int = 19001
  val tracerFactory: Tracer.Factory = NullTracer.factory

  val retryDelay: Int = 10

  def apply(runtime: RuntimeEnvironment): SchedulerServiceImpl = new SchedulerServiceImpl()(this)
}
