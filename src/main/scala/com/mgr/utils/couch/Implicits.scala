package com.mgr.utils.couch

import com.twitter.finagle.redis.util.CBToString
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffer

object Implicits {

  implicit def stringToChannelBuffer(s: String): ChannelBuffer = StringToChannelBuffer(s)
  implicit def javaLongToLong(l: java.lang.Long): Long = l.asInstanceOf[Long]

  implicit def futureListChannelBufferToFutureListString(
    flcb: Future[List[ChannelBuffer]]
  ): Unit = flcb map { lcb => lcb.map(CBToString(_)) }

  implicit def seqStringToSeqChannelBuffer[U <: Seq[ChannelBuffer], T <: Seq[String]](s: T): U =
    s.map(StringToChannelBuffer(_)).asInstanceOf[U]

  implicit def seqChannelBufferToSeqString[U <: Seq[ChannelBuffer], T <: Seq[String]](s: U): T =
    s.map(CBToString(_)).asInstanceOf[T]

  implicit def futureJavaBooleanToFutureBoolean(fb: Future[java.lang.Boolean]): Future[Boolean] =
    fb.map(_.booleanValue)

  implicit def futureJavaLongToFutureLong(fl: Future[java.lang.Long]): Future[Long] =
    fl map javaLongToLong

  implicit def cBToString(fOC: Future[Option[ChannelBuffer]]): Future[Option[String]] =
    fOC map { option: Option[ChannelBuffer] => option.map(CBToString(_)) }

  implicit def futureSeqChannelBufferToFutureSeqString(
    fSCb: Future[Seq[ChannelBuffer]]
  ): Future[Seq[String]] = fSCb map { scb: Seq[ChannelBuffer] => scb.map(CBToString(_)) }

}
