package com.mgr.scheduler.config

import com.twitter.conversions.time._
import com.twitter.util.Duration

object Config {
  lazy val couchHost: String = "localhost"
  lazy val couchPort: Int = 6666
  lazy val couchDb: String = "scheduler"
  lazy val couchTimeout: Duration = 7.seconds
  lazy val couchTcpTimeout: Duration = 5.seconds
}
