package com.mgr.scheduler.datastructures

import com.mgr.scheduler.datastructures.Implicits._
import com.mgr.scheduler.docs
import com.mgr.thrift.scheduler

case class File(
  id: String,
  config1: Config,
  config2: Config,
  lines: Seq[Line]
) {

  def addTeacher(teacher: scheduler.Teacher): File = {
    this.copy(
      config1 = config1.addTeacher(teacher),
      config2 = config2.addTeacher(teacher),
      lines = lines.addTeacher(teacher, config1.configId, config2.configId)
    )
  }

  def addGroup(group: scheduler.Group, config: docs.Config): File = {
    val newConfig1 = {
      if (config._id == config1.configId) {
        config1.addGroup(group)
      } else {
        config1
      }
    }
    val newConfig2 = {
      if (config._id == config2.configId) {
        config2.addGroup(group)
      } else {
        config2
      }
    }

    this.copy(
      config1 = newConfig1,
      config2 = newConfig2,
      lines = lines.addGroup(group, config)
    )
  }

  def isValid: (Option[String], Boolean) = {
    val valid1 = config1.isValid
    val valid2 = config2.isValid

    val err = (valid1._1, valid2._1) match {
      case (Some(s1), Some(s2)) => Some(s1 ++ s2)
      case (Some(s1), None) => Some(s1)
      case (None, Some(s2)) => Some(s2)
      case (None, None) => None
    }
    (err, valid1._2 && valid2._2)
  }
}
