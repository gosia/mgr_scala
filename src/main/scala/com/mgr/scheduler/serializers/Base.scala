package com.mgr.scheduler.serializers

import com.mgr.scheduler.datastructures
import com.mgr.utils.logging.Logging

abstract class Base extends Logging {
  def toFileDef: datastructures.File
  def toLineSeq: Seq[datastructures.Line]

  def fromLine(line: datastructures.Line): String
  def fromLineSeq(lines: Seq[datastructures.Line]): String = lines map fromLine mkString "\n"
}
