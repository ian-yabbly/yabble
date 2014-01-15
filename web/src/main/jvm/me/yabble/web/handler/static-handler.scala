package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.http.impl.cookie.DateUtils

import org.springframework.context.ResourceLoaderAware
import org.springframework.core.io.ResourceLoader
import org.springframework.util.AntPathMatcher

import java.io.IOException
import java.util.regex.Pattern

import scala.collection.JavaConversions._

class StaticHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val staticBaseResourcePath: String,
    val encoding: String)
  extends Handler
  with ResourceLoaderAware
{
  def VERSION_PATTERN = Pattern.compile("^/s/v-[\\p{Alnum}_-]+(/.*)$")

  private val pathPatterns = List("/s/**")

  private var resourceLoader: ResourceLoader = null
  override def setResourceLoader(resourceLoader: ResourceLoader) {
    this.resourceLoader = resourceLoader
  }

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => index(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def index(exchange: HttpExchange, pathVars: Map[String, String]) {
    val path = noContextPath(exchange)
    val m = VERSION_PATTERN.matcher(path)

    var isVersioned = false
    val resourcePath = if (m.matches()) {
          isVersioned = true
          m.group(1)
        } else {
          path.substring("/s".length)
        }

    val contentType = if (path.toLowerCase().endsWith(".css")) {
          Some("text/css; charset=%s".format(encoding))
        } else if (path.toLowerCase().endsWith(".less")) {
          Some("text/plain")
        } else if (path.toLowerCase().endsWith(".js")) {
          Some("application/javascript; charset=%s".format(encoding))
        } else if (path.toLowerCase().endsWith(".gif")) {
          Some("image/gif")
        } else if (path.toLowerCase().endsWith(".jgp")) {
          Some("image/jpeg")
        } else {
          None
        }

    try {
      val startMs = System.currentTimeMillis()
      contentType.foreach(t => exchange.getResponseHeaders.set("Content-Type", t))
      if (isVersioned) {
        exchange.getResponseHeaders.set("Cache-Control", "max-age=31536000, public")
      }

      val resource = resourceLoader.getResource(staticBaseResourcePath + resourcePath)

      try {
        exchange.getResponseHeaders.set("Last-Modified", DateUtils.formatDate(new java.util.Date(resource.lastModified())))
      } catch {
        case e: IOException => log.warn(e.getMessage, e)
      }

      exchange.sendResponseHeaders(200, resource.contentLength())
      // TODO try/finally
      IOUtils.copy(resource.getInputStream, exchange.getResponseBody)
      resource.getInputStream.close()
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
