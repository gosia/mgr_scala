package com.mgr.utils

import com.twitter.util.Future
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe.TypeTag
import scala.reflect.runtime.universe.typeOf

object UtilFuns {

  def isSubtype[T: TypeTag](instance: Throwable): Boolean = {
    val instanceType = currentMirror.reflect(instance).symbol.toType
    instanceType <:< typeOf[T]
  }

  def retry[T, E <: Throwable](limit: Int, retryDelay: Int = 10)(code: => Future[T])(
    implicit m: TypeTag[E]
    ): Future[T] = {

    def attempt(number: Int): Future[T] = {
      number match {
        case i if i >= limit => code
        case otherwise => code rescue {
          // Note: This uses a reflection API introduced in scala 2.10 which deprecates Manifests
          // It remembers the compile time class of E so we can use it at runtime.
          // If this class is a subtype of the class we're catching, retry
          case e if isSubtype[E](e) =>
            Thread.sleep(retryDelay)
            attempt(number + 1)
        }
      }
    }

    attempt(0)
  }

}
