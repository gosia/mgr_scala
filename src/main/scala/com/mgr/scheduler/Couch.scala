package com.mgr.scheduler

import com.mgr.scheduler.config.Config
import com.mgr.utils.couch.Client

trait Couch {
  lazy val couchClient = Client(
    Config.couchHost,
    Config.couchPort,
    Config.couchDb,
    timeout = Config.couchTimeout,
    tcpTimeout = Config.couchTcpTimeout
  )
}
