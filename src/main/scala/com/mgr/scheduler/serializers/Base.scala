package com.mgr.scheduler.serializers

import com.mgr.scheduler.datastructures
import com.mgr.utils.logging.Logging

abstract class Base extends Logging {
  def toFileDef: datastructures.File
}
