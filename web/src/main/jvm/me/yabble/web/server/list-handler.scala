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

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class YListHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val template: VelocityTemplate,
    val encoding: String,
    private val ylistService: IYListService)
  extends TemplateHandler
  with FormHandler
{
  private val pathPatterns = List("/list/{id}/{slug}", "/list/{id}")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => view(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 1 => redirectToList(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def view(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val list = ylistService.find(pathVars("id"))        
        val context = Map("list" -> list)
        htmlTemplateResponse(exchange, List("list.html", "layout/layout.html"), context)        
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def redirectToList(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val list = ylistService.find(pathVars("id"))        
        redirect(exchange, "/list/%s/%s".format(list.id, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }
}
