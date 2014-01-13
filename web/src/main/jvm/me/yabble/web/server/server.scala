package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils

import org.springframework.context.Lifecycle
import org.springframework.context.ResourceLoaderAware
import org.springframework.core.io.ResourceLoader

import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.util.{List => JList}

import scala.collection.JavaConversions._

class Server(
    private val router: Router,
    private val port: Int)
  extends Lifecycle
  with Log
{   
  var isRunning = false
  val server = HttpServer.create(new InetSocketAddress(port), 0)

  override def start() {
    consoleLog.info("Starting HTTP server...")

    val context = server.createContext("/", router)

    //context.getFilters.add(new YabbleFilter(sessionService, sourceVersionHash))

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

trait Handler extends Log with ResourceLoaderAware {
  val sessionService: SessionService
  val userService: UserService
  val template: VelocityTemplate
  val staticBasePath: String

  var resourceLoader: ResourceLoader

  def utf8 = java.nio.charset.Charset.forName("utf-8")

  override def setResourceLoader(resourceLoader: ResourceLoader) {
    this.resourceLoader = resourceLoader
  }

  def maybeHandle(exchange: HttpExchange): Boolean

  def noContextPath(exchange: HttpExchange): String = {
    val httpContext = exchange.getHttpContext
    if (httpContext.getPath == null) {
      exchange.getRequestURI.getPath
    } else {
      exchange.getRequestURI.getPath.substring(httpContext.getPath.length)
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

  protected def htmlTemplateResponse(
      exchange: HttpExchange,
      templates: List[String],
      context: Map[String, Any],
      status: Int = 200)
  {
    try {
      exchange.getResponseHeaders.set("Content-Type", "text/html; charset=utf-8")
      exchange.sendResponseHeaders(status, 0)
      // TODO try/finally
      val osw = new OutputStreamWriter(exchange.getResponseBody, utf8)
      template.render(templates, context, osw)
      osw.close()
      exchange.getResponseBody.close()
    } catch {
      case e: Exception => {
        log.error(e.getMessage, e)
        try {
          exchange.getResponseBody.close()
        } catch {
          case e: Exception => log.error(e.getMessage, e)
        }
      }
    }
  }

  protected def staticResponse(
      exchange: HttpExchange,
      path: String,
      contentType: String,
      status: Int = 200)
  {
    try {
      exchange.getResponseHeaders.set("Content-Type", contentType)
      val resource = resourceLoader.getResource(staticBasePath + path)
      exchange.sendResponseHeaders(status, 0)
      // TODO try/finally
      IOUtils.copy(resource.getInputStream, exchange.getResponseBody)
      resource.getInputStream.close()
      exchange.getResponseBody.close()
    } catch {
      case e: Exception => {
        log.error(e.getMessage, e)
        try {
          exchange.getResponseBody.close()
        } catch {
          case e: Exception => log.error(e.getMessage, e)
        }
      }
    }
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
