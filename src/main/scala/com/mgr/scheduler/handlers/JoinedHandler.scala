package com.mgr.scheduler.handlers

import com.twitter.util.Future

import com.mgr.scheduler.Couch
import com.mgr.scheduler.docs
import com.mgr.scheduler.handlers.ConfigHandler._
import com.mgr.thrift.scheduler
import com.mgr.utils.logging.Logging

object JoinedHandler extends Logging with Couch {


  def removeConfigElement(
    configId: String, elementId: String, elementType: String
  ): Future[Unit] = {
    log.info(s"Removing element $elementId of type $elementType for config $configId")

    couchClient.get[docs.Config](configId) flatMap {
      case None => throw scheduler.ValidationException(s"Przydział $configId nie istnieje")
      case Some(config) =>

        ConfigHandler.removeConfigElement(configId, elementId, elementType) flatMap { _ =>
          config.theOtherConfigId match {
            case None => Future.value()
            case Some(otherConfigId) =>
              ConfigHandler.removeConfigElement(
                otherConfigId, elementId, elementType
              ) flatMap { _ =>
                FileHandler.removeElement(config.file.get, elementId, elementType)
              }
          }
        }

    }
  }

  def editConfigElement(
    configId: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    log.info(s"Editing elements for config $configId")

    couchClient.get[docs.Config](configId) flatMap {
      case None => throw scheduler.ValidationException(s"Przydział $configId nie istnieje")
      case Some(config) =>
        ConfigHandler.editConfigElement(configId, terms, rooms, teachers, groups) flatMap { _ =>
          config.theOtherConfigId match {
            case None => Future.value()
            case Some(otherConfigId) =>
              ConfigHandler.editConfigElement(
                otherConfigId, terms, rooms, teachers, Seq()
              ) flatMap { _ =>
                FileHandler.editElements(config.file.get, config, teachers, groups)
              }
          }
        }
    }

  }

  def addConfigElement(
    configId: String,
    terms: Seq[scheduler.Term],
    rooms: Seq[scheduler.Room],
    teachers: Seq[scheduler.Teacher],
    groups: Seq[scheduler.Group]
  ): Future[Unit] = {
    log.info(s"Adding elements for config $configId")

    couchClient.get[docs.Config](configId) flatMap {
      case None => throw scheduler.ValidationException(s"Przydział $configId nie istnieje")
      case Some(config) =>
        ConfigHandler.addConfigElement(configId, terms, rooms, teachers, groups) flatMap { _ =>
          config.theOtherConfigId match {
            case None => Future.value()
            case Some(otherConfigId) =>
              ConfigHandler.addConfigElement(
                otherConfigId, terms, rooms, teachers, Seq()
              ) flatMap { _ =>
                FileHandler.addElements(config.file.get, config, teachers, groups)
              }
          }
        }
    }

  }

}
