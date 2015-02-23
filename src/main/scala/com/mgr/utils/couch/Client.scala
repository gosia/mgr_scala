package com.mgr.utils.couch

import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.filter.MaskCancelFilter
import com.twitter.finagle.http.Http
import com.twitter.finagle.http.Response
import com.twitter.util.Future
import java.net.URLEncoder
import net.liftweb.json
import org.apache.http.ConnectionClosedException
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1

import com.mgr.utils.couch.Implicits._
import com.mgr.utils.logging.Logging
import com.mgr.utils.UtilFuns

object Client {
  def server(host: String, port: Int): Server = Server(host, port)
}
case class Server(host: String, port: Int) {
  def db(name: String): Client = Client(host, port, name)
}

case class Client(
  host: String,
  port: Int,
  name: String
) extends Logging {
  implicit val formats = json.Serialization.formats(json.NoTypeHints)

  lazy val couchBuilder = {
    ClientBuilder()
      .codec(Http())
      .hosts(s"${this.host}:${this.port}")
      .hostConnectionLimit(1)
      .tcpConnectTimeout(3.seconds)
      .timeout(5.seconds)
  }

  def add[DocType <: Document: Manifest](doc: DocType): Future[CouchResponse] = {
    log.info(s"COUCH: ADD ${doc._id}")

    val content = json.Serialization.write(doc)
    log.info(s"COUCH: ADD CONTENT $content")
    doRequests("PUT", Some(content), doc._id) map {
      j: String => json.parse(j).extract[CouchResponse]
    }

  }

  def get[DocType <: Document: Manifest](id: String): Future[DocType] = {
    log.info(s"COUCH: GET $id")
    doRequests("GET", None, id) map {
      j: String => json.parse(j).extract[DocType]
    }
  }

  def update[DocType <: Document: Manifest](doc: DocType): Future[CouchResponse] = {
    log.info(s"COUCH: UPDATE ${doc._id}")
    val content = json.Serialization.write(doc)
    doRequests("PUT", Some(content), doc._id) map {
      j: String => json.parse(j).extract[CouchResponse]
    }
  }

  def bulkAdd[T <: Document](docs: Seq[T]): Future[Seq[CouchResponse]] = {
    log.info(s"COUCH: BULK ADD ${docs.size} items")
    val packedDocs = json.JObject(List(json.JField("docs", json.Extraction.decompose(docs))))
    val content = json.Serialization.write(packedDocs)
    doRequests("POST", Some(content), "_bulk_docs") map {
      j: String => json.parse(j).extract[List[CouchResponse]].toSeq
    }
  }

  private def setCommonHeaders(
    request: HttpRequest, method: String, body: Option[String]
  ) = {
    request.headers().set(HttpHeaders.Names.HOST, s"${this.host}:${this.port}")
    request.headers().set(HttpHeaders.Names.ACCEPT, "application/json")
    body map { b =>
      request.headers().set(
        HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(b.getBytes("UTF-8").length)
      )
    }
    if (method != "GET" && method != "HEAD")
      request.headers().set(HttpHeaders.Names.CONTENT_TYPE, "application/json")
  }

  private def sendRequest(request: HttpRequest): Future[HttpResponse] = {
    val client = this.couchBuilder.build()
    val filter = new MaskCancelFilter[HttpRequest, HttpResponse]
    filter(request, client)
  }

  protected def doRequests(method: String, body: Option[String], id: String): Future[String] = {
    val url = s"/${this.name}/${URLEncoder.encode(id, "UTF-8")}"
    val m = HttpMethod.valueOf(method)
    val request = new DefaultHttpRequest(HTTP_1_1, m, url)
    setCommonHeaders(request, method, body)
    body.map(request.setContent(_))

    UtilFuns.retry[HttpResponse, ConnectionClosedException] (3) {
      sendRequest(request)
    } map { response: HttpResponse =>
      val code = response.getStatus.getCode
      if (!List(200, 201, 202).contains(code)) {
        throw new CouchException(code.toString)
      }

      Response(response).getContentString()
    }
  }
}
