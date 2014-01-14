package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function

import com.sun.net.httpserver._

import org.apache.http.impl.cookie.DateUtils

import org.joda.time.DateTime;

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class UserHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val template: VelocityTemplate,
    val encoding: String,
    private val sessionCookieName: String,
    private val sessionCookieDomain: String)
  extends TemplateHandler
  with FormHandler
{
  private val pathPatterns = List("/logout")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => logout(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def logout(exchange: HttpExchange, pathVars: Map[String, String]) {
    Utils.optionalFirstCookie(exchange, sessionCookieName) match {
      case Some(c) => {
        val cookieExpr = new DateTime(0l)
        exchange.getResponseHeaders.add("Set-Cookie", "%s=; Path=/; Domain=%s; Expires=%s".format(
            sessionCookieName,
            sessionCookieDomain,
            DateUtils.formatDate(cookieExpr.toDate())))
      }
      case None => // Nothing to do
    }

    exchange.getResponseHeaders.set("Pragma", "no-cache")
    exchange.getResponseHeaders.set("Cache-Control", "no-cache")

    redirect(exchange, "/")
  }
}
