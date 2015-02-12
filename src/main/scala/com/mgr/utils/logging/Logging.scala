package com.mgr.utils.logging

import java.util.logging.Logger

trait Logging {
  @transient
  lazy val log = Logger.getLogger(getClass.getSimpleName)
}
