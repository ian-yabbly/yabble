package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.common.ctx.ExecutionContext
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.service.proto.ServiceProtos.EntityType
import me.yabble.web.proto.WebProtos._
import me.yabble.web.handler.Handler
import me.yabble.web.handler.{Utils => HandlerUtils}
import me.yabble.web.service._

import com.google.gson._

import com.sun.net.httpserver._

import org.springframework.context.Lifecycle

import java.net.InetSocketAddress
import java.util.{List => JList}

import scala.collection.JavaConversions._

class Server(
    private val router: Router,
    private val port: Int,
    private val contextPath: String,
    filters: JList[Filter])
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

    //context.getFilters.add(new BaseFilter(sessionService, sessionCookieName))

    filters.foreach(f => context.getFilters.add(f))

    server.setExecutor(null)
    server.start()

    consoleLog.info(s"HTTP server listening on port [$port]")
    
    isRunning = true
  }

  override def stop() {
    server.stop(0)
    consoleLog.info("New Rest server is stopped")
    isRunning = false
  }
}

class BaseFilter(sessionService: SessionService, sessionCookieName: String)
  extends Filter
  with Log
{
  override def description() = "base-filter"

  override def doFilter(exchange: HttpExchange, chain: Filter.Chain) {
    var ctx: ExecutionContext = null
    try {
      ctx = ExecutionContext.getOrCreate()
      ctx.setAttribute("http-exchange", exchange)
      HandlerUtils.optionalFirstCookie(exchange, sessionCookieName).foreach(v => ctx.setAttribute("web-session-id", v))
      chain.doFilter(exchange)
    } catch {
      case e: EntityNotFoundException => e.kind match {
        case EntityType.USER => {
          optional2Option(sessionService.optional()) match {
            case Some(session) => {
              if (session.hasUserId() && session.getUserId == e.id) {
                log.info("Logging user out [{}]", e.id)
                HandlerUtils.redirectResponse(exchange, "/logout")
              } else {
                HandlerUtils.redirectResponse(exchange, "/whoops/not-found")
              }
            }
            case None => HandlerUtils.redirectResponse(exchange, "/whoops/not-found")
          }
        }
        case _ => HandlerUtils.redirectResponse(exchange, "/whoops/not-found")
      }

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
