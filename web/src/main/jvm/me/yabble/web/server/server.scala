package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.common.TextFormat
import me.yabble.common.ctx.ExecutionContext
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.proto.WebProtos._
import me.yabble.web.service._
import me.yabble.web.template.{Utils => TemplateUtils}
import me.yabble.web.template.{Format => TemplateFormat}
import me.yabble.web.template.VelocityTemplate

import com.google.common.base.Function
import com.google.gson._

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import org.springframework.context.Lifecycle

import java.io.OutputStreamWriter
import java.net.HttpCookie
import java.net.InetSocketAddress
import java.util.{List => JList}

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
}

class Server(
    private val router: Router,
    private val port: Int,
    private val contextPath: String,
    private val sessionCookieName: String)
  extends Lifecycle
  with Log
{   
  var isRunning = false
  val server = HttpServer.create(new InetSocketAddress(port), 0)

  override def start() {
    consoleLog.info("Starting HTTP server...")

    val context = if ("".equals(contextPath)) {
          server.createContext("/", router)
        } else {
          server.createContext(contextPath, router)
        }

    context.getFilters.add(new BaseFilter(sessionCookieName))

    server.setExecutor(null)
    server.start()

    consoleLog.info(s"New Rest server listening on port [$port]")
    
    isRunning = true
  }

  override def stop() {
    server.stop(0)
    consoleLog.info("New Rest server is stopped")
    isRunning = false
  }
}

class BaseFilter(sessionCookieName: String)
  extends Filter
  with Log
{
  override def description() = "base-filter"

  override def doFilter(exchange: HttpExchange, chain: Filter.Chain) {
    var ctx: ExecutionContext = null
    try {
      ctx = ExecutionContext.getOrCreate()
      ctx.setAttribute("http-exchange", exchange)
      Utils.optionalFirstCookie(exchange, sessionCookieName).foreach(v => ctx.setAttribute("web-session-id", v))
      chain.doFilter(exchange)
    } catch {
      case e: Exception => {
        log.error(e.getMessage, e)
        exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(500, 0)
        exchange.getResponseBody.close()
      }
    } finally {
      if (null != ctx) { ExecutionContext.remove() }

      try {
        exchange.getResponseBody.close()
      } catch {
        case e: Exception => log.error(e.getMessage, e)
      }
    }
  }
}

class Router(private val handlers: JList[Handler])
  extends HttpHandler
  with Log
{
  override def handle(exchange: HttpExchange) {
    val uri = exchange.getRequestURI
    val httpContext = exchange.getHttpContext

    val startMs = System.currentTimeMillis()
    handlers.find(h => h.maybeHandle(exchange)) match {
      case Some(h) => // Do nothing
      case None => {
        val response = s"Unknown path [${uri.getPath}]"
        exchange.sendResponseHeaders(404, response.length)
        val os = exchange.getResponseBody
        os.write(response.getBytes)
        os.close()
      }
    }

    val t = System.currentTimeMillis() - startMs
    log.info("Timing [{}] [{}ms]", exchange.getRequestURI.getPath, t)
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
      val uid = userService.create(new User.Free(None, None, None))
      sessionService.withSession(true, new Function[Session, Session]() {
        override def apply(session: Session): Session = {
          session.toBuilder().setUserId(uid).build()
        }
      })
      userService.find(uid)
    }
  }

  protected def redirect(exchange: HttpExchange, path: String, isPermanent: Boolean = false) {
    val status = if (isPermanent) 301 else 302
    exchange.getResponseHeaders.set("Location", path)
    exchange.sendResponseHeaders(status, 0)
  }

  protected def plainTextResponse(exchange: HttpExchange, text: Option[String] = None, code: Int = 200) {
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

  protected def isRequestSecure(exchange: HttpExchange): Boolean = "https".equalsIgnoreCase(exchange.getRequestURI.getScheme)

  def queryNvps(e: HttpExchange): List[NameValuePair] = URLEncodedUtils.parse(e.getRequestURI, encoding).toList
  def postNvps(e: HttpExchange): List[NameValuePair] = URLEncodedUtils.parse(IOUtils.toString(e.getRequestBody, encoding), java.nio.charset.Charset.forName(encoding)).toList
  def allNvps(e: HttpExchange): List[NameValuePair] = queryNvps(e) ++ postNvps(e)

  def firstNvp(nvps: List[NameValuePair], names: String*): Option[String] = names.find(name => {
        nvps.find(_.getName == name).isDefined
      }).map(name => {
        nvps.find(_.getName == name).map(_.getValue).get
      })

  def requiredFirstParam(nvps: List[NameValuePair], names: String*): String = {
    val ret = firstNvp(nvps, names: _*)
    ret match {
      case Some(v) => v
      case None => throw new MissingParamException(names.mkString(", "))
    }
  }

  def params(nvps: List[NameValuePair], name: String): List[String] = nvps.filter(_.getName == name)
      .flatMap(_.getValue.split(","))
      .filterNot(_ == "")
}

trait TemplateHandler extends Handler {
  val template: VelocityTemplate

  protected def htmlTemplateResponse(
      exchange: HttpExchange,
      templates: List[String],
      context: Map[String, Any] = Map(),
      status: Int = 200)
  {
    try {
      exchange.getResponseHeaders.set("Content-Type", "text/html; charset=utf-8")
      exchange.sendResponseHeaders(status, 0)
      // TODO try/finally
      val osw = new OutputStreamWriter(exchange.getResponseBody, utf8)
      template.render(templates, osw, supplementContext(context))
      osw.close()
    } catch {
      case e: Exception => {
        log.error(e.getMessage, e)
      }
    }
  }

  private def supplementContext(c: Map[String, Any]): Map[String, Any] = {
    val m = MutableMap(c.toSeq: _*)
    m.put("Utils", classOf[TemplateUtils])
    m.put("Format", classOf[TemplateFormat])
    m.put("TextFormat", classOf[TextFormat])

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
