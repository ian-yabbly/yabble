package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate

import com.sun.net.httpserver._

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class IndexHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val template: VelocityTemplate)
  extends TemplateHandler
{
  private val pathPatterns = List("/")

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
    htmlTemplateResponse(exchange, List("index.html", "layout/layout.html"), Map("hello" -> "world"))
  }
}
