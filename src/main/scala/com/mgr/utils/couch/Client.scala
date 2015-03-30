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
) extends RequestUtils {

  def add[DocType <: Document : Manifest](doc: DocType): Future[CouchResponse] = {
    log.info(s"COUCH: ADD ${doc._id}")
    val content = json.Serialization.write(doc)
    doDocumentRequest("PUT", Some(content), doc._id) map {
      j: String => json.parse(j).extract[CouchResponse]
    }
  }

  def get[DocType <: Document : Manifest](id: String): Future[DocType] = {
    log.info(s"COUCH: GET $id")
    doDocumentRequest("GET", None, id) map {
      j: String => json.parse(j).extract[DocType]
    }
  }

  def update[DocType <: Document : Manifest](doc: DocType): Future[CouchResponse] = {
    log.info(s"COUCH: UPDATE ${doc._id}")
    val content = json.Serialization.write(doc)
    doDocumentRequest("PUT", Some(content), doc._id) map {
      j: String => json.parse(j).extract[CouchResponse]
    }
  }

  def delete[DocType <: Document : Manifest](doc: DocType): Future[CouchResponse] = {
    log.info(s"COUCH: DELETE ${doc._id}")
    doDocumentRequest("DELETE", None, doc._id, doc._rev) map {
      j: String => json.parse(j).extract[CouchResponse]
    }
  }

  def bulkAdd[T <: Document](docs: Seq[T]): Future[Seq[CouchResponse]] = {
    log.info(s"COUCH: BULK ADD ${docs.size} items")
    val content = json.Serialization.write(Map("docs" -> docs))
    doBulkRequest("POST", Some(content)) map {
      j: String => json.parse(j).extract[List[CouchResponse]].toSeq
    }
  }

  def view(viewName: String): ViewQueryBuilder =
    ViewQueryBuilder(this.host, this.port, this.name, viewName)
}

case class ViewQueryBuilder(
  host: String,
  port: Int,
  name: String,
  viewName: String,

  keys: Option[Seq[Any]] = None,
  startkey: Option[Any] = None,
  startkey_docid: Option[String] = None,
  endkey: Option[Any] = None,
  endkey_docid: Option[String] = None,
  limit: Option[Int] = None,
  reduce: Option[Boolean] = None,
  include_docs: Option[Boolean] = None
) extends RequestUtils {

  def startkey(startkey: Any): ViewQueryBuilder = this.copy(startkey=Some(startkey))
  def endkey(endkey: Any): ViewQueryBuilder = this.copy(endkey=Some(endkey))
  def limit(limit: Int): ViewQueryBuilder = this.copy(limit=Some(limit))
  def reduce(reduce: Boolean): ViewQueryBuilder = this.copy(reduce=Some(reduce))
  def includeDocs: ViewQueryBuilder = this.copy(include_docs=Some(true))

  def execute: Future[ViewResult] = {
    log.info(s"COUCH: VIEW $viewName")
    doViewRequest(viewName, queryBody, queryParams) map {
      j: String => json.parse(j).extract[ViewResult]
    }
  }

  private def queryParams: String = {
    val queryMap = json.Extraction.decompose(this).asInstanceOf[json.JObject]
      .values.asInstanceOf[Map[String, AnyRef]]

    queryMap
      .filterKeys(!Set("keys", "host", "port", "name", "viewName").contains(_))
      .filter({
        case (k, None) => false
        case (k, v) => true
      }) map {
      case (mapkey, value) =>
        val cleanedValue: String = mapkey match {
          case "startkey_docid" => value.toString
          case _ => json.Serialization.write(value)
        }
        "%s=%s".format(
          URLEncoder.encode(mapkey.toString, "UTF-8"),
          URLEncoder.encode(cleanedValue, "UTF-8")
        )
      } mkString "&"
  }
  private def queryBody: Option[String] = {
    keys map { keyList: Seq[Any] => {
      Some(json.Serialization.write(Map("keys" -> keyList)))
    }} getOrElse None
  }
}

trait RequestUtils extends Logging {

  val host: String
  val port: Int
  val name: String

  lazy val couchBuilder = {
    ClientBuilder()
      .codec(Http())
      .hosts(s"${this.host}:${this.port}")
      .hostConnectionLimit(1)
      .tcpConnectTimeout(3.seconds)
      .timeout(5.seconds)
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

  protected def doDocumentRequest(
    method: String, body: Option[String], id: String, rev: Option[String] = None
  ): Future[String] = {
    val revQuery = rev.map(r => s"?rev=$r").getOrElse("")
    doRequest(
      (method: String, body: Option[String]) =>
        s"/${this.name}/${URLEncoder.encode(id, "UTF-8")}$revQuery"
    )(method, body)
  }

  protected def doBulkRequest(method: String, body: Option[String]): Future[String] = {
    doRequest(
      (method: String, body: Option[String]) => s"/${this.name}/_bulk_docs"
    )(method, body)
  }

  protected def doViewRequest(
    viewName: String, body: Option[String], params: String
  ): Future[String] = {
    doRequest(
      (_: String, _: Option[String]) =>
        s"/${this.name}/_design/${viewName.split("/")(0)}/_view/${viewName.split("/")(1)}?$params"
    )(body.map(_ => "POST").getOrElse("GET"), body)
  }

  protected def doRequest(
    getId: (String, Option[String]) => String
  )(method: String, body: Option[String]): Future[String] = {
    val url = getId(method, body)
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
