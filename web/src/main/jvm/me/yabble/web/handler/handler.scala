package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.common.TextFormat
import me.yabble.common.TextUtils
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.proto.WebProtos._
import me.yabble.web.service._
import me.yabble.web.template.{Utils => TemplateUtils}
import me.yabble.web.template.{Format => TemplateFormat}
import me.yabble.web.template.VelocityTemplate

import com.google.common.base.Function
import com.google.gson._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.commons.lang.exception.ExceptionUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import java.net.HttpCookie

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MutableMap}

object Utils {
  private val gson = new Gson()
  def log = LoggerFactory.getLogger("me.yabble.web.server.Utils")
  def utf8 = java.nio.charset.Charset.forName("utf-8")

  def allCookies(exchange: HttpExchange): List[HttpCookie] = if (exchange.getRequestHeaders.getFirst("Cookie") == null) {
        return Nil
      } else {
        HttpCookie.parse(exchange.getRequestHeaders.getFirst("Cookie")).toList
      }

  def optionalFirstCookie(exchange: HttpExchange, name: String): Option[String] =
      optionalFirstCookie(allCookies(exchange), name)

  def optionalFirstCookie(cookies: List[HttpCookie], name: String): Option[String] =
      cookies.find(_.getName == name).map(_.getValue)

  def jsonResponse(exchange: HttpExchange, j: JsonElement, status: Int) {
    try {
      val responseBytes = gson.toJson(j).getBytes(utf8)
      exchange.getResponseHeaders.set("Content-Type", "application/json; charset=utf-8")
      exchange.sendResponseHeaders(status, responseBytes.length)
      val os = exchange.getResponseBody
      os.write(responseBytes)
      os.close()
    } catch {
      case e: Exception => {
        try {
          exchange.getResponseBody.close()
        } catch {
          case e2: Exception => {
            log.error(e2.getMessage, e2)
            throw e
          }
        }
      }
    }
  }

  def redirectResponse(exchange: HttpExchange, path: String, isPermanent: Boolean = false) {
    val status = if (isPermanent) 301 else 302
    exchange.getResponseHeaders.set("Location", path)
    exchange.sendResponseHeaders(status, 0)
  }

  def plainTextResponse(exchange: HttpExchange, text: Option[String] = None, code: Int = 200) {
    exchange.getResponseHeaders.set("Content-Type", "text/plain")
    text match {
      case Some(t) => {
        val bytes = t.getBytes(utf8)
        exchange.sendResponseHeaders(code, bytes.length)
        IOUtils.write(bytes, exchange.getResponseBody)
      }
      case None => {
        exchange.sendResponseHeaders(code, 0)
      }
    }
  }
}

trait Handler extends Log {
  val sessionService: SessionService
  val userService: IUserService
  val encoding: String

  def utf8 = java.nio.charset.Charset.forName(encoding)

  def maybeHandle(exchange: HttpExchange): Boolean

  /**
   * @return path without context and without query string
   */
  def noContextPath(exchange: HttpExchange): String = {
    val httpContext = exchange.getHttpContext
    httpContext.getPath match {
      case null => exchange.getRequestURI.getPath
      case "/" => exchange.getRequestURI.getPath
      case _ => exchange.getRequestURI.getPath.substring(httpContext.getPath.length)
    }
  }

  protected def optionalUserId(): Option[String] = o2o(sessionService.optional()) match {
    case Some(session) => {
      if (session.hasUserId) {
        Some(session.getUserId)
      } else {
        None
      }
    }
    case None => None
  }

  protected def optionalMe(): Option[User.Persisted] = o2o(sessionService.optional()) match {
    case Some(session) => {
      if (session.hasUserId) {
        Some(userService.find(session.getUserId))
      } else {
        None
      }
    }
    case None => None
  }

  protected def requiredMe(): User.Persisted = optionalMe match {
    case Some(user) => user
    case None => throw new UnauthenticatedException
  }

  protected def meOrCreate(): User.Persisted = optionalMe match {
    case Some(user) => user
    case None => {
      val uid = userService.create(new User.Free(None, None, None, None))
      sessionService.withSession(true, new Function[Session, Session]() {
        override def apply(session: Session): Session = {
          session.toBuilder().setUserId(uid).build()
        }
      })
      userService.find(uid)
    }
  }

  protected def redirectResponse(exchange: HttpExchange, path: String, isPermanent: Boolean = false) {
    Utils.redirectResponse(exchange, path, isPermanent)
  }

  protected def plainTextResponse(exchange: HttpExchange, text: Option[String] = None, code: Int = 200) {
    Utils.plainTextResponse(exchange, text, code)
  }

  protected def isRequestSecure(exchange: HttpExchange): Boolean = "https".equalsIgnoreCase(exchange.getRequestURI.getScheme)

  def queryNvps(e: HttpExchange): List[NameValuePair] = URLEncodedUtils.parse(e.getRequestURI, encoding).toList
  def postNvps(e: HttpExchange): List[NameValuePair] = URLEncodedUtils.parse(IOUtils.toString(e.getRequestBody, encoding), java.nio.charset.Charset.forName(encoding)).toList
  def allNvps(e: HttpExchange): List[NameValuePair] = queryNvps(e) ++ postNvps(e)

  def optionalFirstParamValue(nvps: List[NameValuePair], name: String): Option[String] = nvps.filter(_.getName == name)
      .map(_.getValue)
      .filter(_ != null)
      .filter(_ != "")
      .headOption

  def firstParamValue(nvps: List[NameValuePair], name: String): String = {
    val ret = optionalFirstParamValue(nvps, name)
    ret match {
      case Some(v) => v
      case None => throw new MissingParamException(name)
    }
  }

  def params(nvps: List[NameValuePair], name: String): List[String] = nvps.filter(_.getName == name)
      //.flatMap(_.getValue.split(","))
      .map(_.getValue)
      .filterNot(_ == "")
}

abstract class TemplateHandler(
    private val template: VelocityTemplate)
  extends Handler
{

  protected def htmlTemplateResponse(
      exchange: HttpExchange,
      templates: List[String],
      context: Map[String, Any] = Map(),
      status: Int = 200)
  {
    val response = try {
          template.renderToString(templates, supplementContext(exchange, context))
        } catch {
          case e: NotFoundException => throw e
          case e: Exception => {
            log.error(e.getMessage, e)
            val stackTraceStr = ExceptionUtils.getStackTrace(e)

            """
            <p style='font-size: 18px;'>%s</p>
            <pre>
              %s
            </pre>
            """.format(e.getMessage, stackTraceStr)
          }
        }

    val responseBytes = response.getBytes(encoding)
    exchange.getResponseHeaders.set("Content-Type", "text/html; charset="+encoding)
    exchange.sendResponseHeaders(status, responseBytes.length)
    IOUtils.write(responseBytes, exchange.getResponseBody)
  }

  private def supplementContext(exchange: HttpExchange, c: Map[String, Any]): Map[String, Any] = {
    val m = MutableMap(c.toSeq: _*)
    m.put("Utils", classOf[TemplateUtils])
    m.put("Format", classOf[TemplateFormat])
    m.put("TextFormat", classOf[TextFormat])
    m.put("TextUtils", classOf[TextUtils])

    m.put("__scheme", exchange.getRequestURI.getScheme)

    optionalMe() match {
      case Some(user) => {
        m.put("__optUser", Some(user))
        m.put("__user", user)
      }
      case None => {
        m.put("__optUser", None)
      }
    }

    return m.toMap
  }
}

trait FormHandler extends Handler {

  def formField(value: Option[String]): FormField = value match {
    case Some(v) => FormField.newBuilder().setValue(v).build()
    case None => FormField.newBuilder().build()
  }
}

class MissingHeaderException(name: String)
    extends RuntimeException(name)

class InvalidHeaderValueException(name: String, value: String)
    extends RuntimeException("%s=%s".format(name, value))

class MissingParamException(name: String)
    extends RuntimeException(name)

class InvalidParamValueException(name: String, value: String)
    extends RuntimeException("%s=%s".format(name, value))

class MissingJsonFieldException(name: String)
    extends RuntimeException(name)

class UnauthenticatedException extends RuntimeException

class UnauthorizedException(message: String) extends RuntimeException(message)

class UnsupportedHttpMethod(val method: String)
  extends RuntimeException(s"Unsupported HTTP method [$method]")
