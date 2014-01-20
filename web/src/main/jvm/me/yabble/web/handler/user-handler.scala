package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.service.velocity.VelocityTemplate
import me.yabble.web.service._
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function

import com.sun.net.httpserver._

import org.apache.http.impl.cookie.DateUtils

import org.joda.time.DateTime;

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class UserHandler(
    val sessionService: SessionService,
    val userService: UserService,
    val encoding: String,
    private val ylistService: YListService,
    private val sessionCookieName: String,
    private val sessionCookieDomain: String,
    val template: VelocityTemplate)
  extends TemplateHandler(template)
  with FormHandler
{
  private val pathPatterns = List("/logout", "/me")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => logout(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 1 => me(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
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

    redirectResponse(exchange, "/")
  }

  def me(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val context = collection.mutable.Map[String, Any]()
        optionalMe().foreach(me => context.put("lists", ylistService.allByUser(me.id)))

        htmlTemplateResponse(exchange, List("me.html", "layout/layout.html"), context.toMap)
      }

      case "post" => {
        val me = requiredMe()
        val nvps = allNvps(exchange)

        optionalFirstParamValue(nvps, "password").foreach(password => {
          userService.updatePassword(me.id, password)
        })

        val u = me.toUpdate

        optionalFirstParamValue(nvps, "email").foreach(email => {
          u.email = Some(email)
        })

        optionalFirstParamValue(nvps, "name").foreach(name => {
          u.name = Some(name)
        })

        userService.update(u)

        // TODO
        redirectResponse(exchange, "/")
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }
}
