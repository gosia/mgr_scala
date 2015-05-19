package com.mgr.scheduler.datastructures

case class File(
  config1: Config,
  config2: Config
) {

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
