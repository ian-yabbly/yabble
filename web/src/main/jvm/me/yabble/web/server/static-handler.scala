package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate

import com.sun.net.httpserver._

import org.springframework.util.AntPathMatcher

import java.util.regex.Pattern

import scala.collection.JavaConversions._

class StaticHandler(
    val sessionService: SessionService,
    val userService: UserService,
    val template: VelocityTemplate,
    val staticBasePath: String,
    var )
  extends Handler
  with Log
{
  def VERSION_PATTERN = Pattern.compile("^/s/v-[\\p{Alnum}_-]+/(.*)$")

  private val pathPatterns = List("/s/**")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)
    log.info("Maybe handling [{}]", path)

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

    val resourcePath = if (m.matches()) {
      m.group(1)
    } else {
      path.substring("/s".length)
    }

    staticResponse(exchange, resourcePath, "text/css; charset=utf-8")
  }
}
