package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.common.ctx.ExecutionContext
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.proto.WebProtos._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import org.springframework.context.Lifecycle

import java.io.OutputStreamWriter
import java.net.HttpCookie
import java.net.InetSocketAddress
import java.util.{List => JList}

import scala.collection.JavaConversions._

object Utils {

  def allCookies(exchange: HttpExchange): List[HttpCookie] = if (exchange.getRequestHeaders.getFirst("Cookie") == null) {
        return Nil
      } else {
        HttpCookie.parse(exchange.getRequestHeaders.getFirst("Cookie")).toList
      }

  def optionalFirstCookie(exchange: HttpExchange, name: String): Option[String] =
      optionalFirstCookie(allCookies(exchange), name)

  def optionalFirstCookie(cookies: List[HttpCookie], name: String): Option[String] =
      cookies.find(_.getName == name).map(_.getValue)
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
  }
}

trait Handler extends Log {
  val sessionService: SessionService
  val userService: UserService

  def utf8 = java.nio.charset.Charset.forName("utf-8")

  def maybeHandle(exchange: HttpExchange): Boolean

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

  protected def redirect(exchange: HttpExchange, path: String) {
    exchange.getResponseHeaders.set("Location", path)
    exchange.sendResponseHeaders(302, 0)
  }
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
      template.render(templates, osw, context)
      osw.close()
    } catch {
      case e: Exception => {
        log.error(e.getMessage, e)
      }
    }
  }
}

trait FormHandler extends Handler {
  val encoding: String

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
