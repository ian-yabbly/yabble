package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function

import org.joda.time.DateTime

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
  private val pathPatterns = List(
      "/list/{id}",
      "/list/{id}/{slug}",
      "/list/{id}/{slug}/tab/{tab}",
      "/list/{id}/comment",
      "/list/{id}/comment/{comment-id}/delete"
      )

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => redirectToList(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 1 => view(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 2 => view(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 3 => commentIndex(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 4 => commentDelete(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def redirectToList(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val list = ylistService.find(pathVars("id"))        
        redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def view(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val list = ylistService.find(pathVars("id"))        
        val tab = pathVars.get("tab").getOrElse("list-items")

        // History
        val history = collection.mutable.Buffer[HistoryItem[_, _]]()
        history.append(HistoryItem(list, None, list.user, "list", list.creationDate))
        history.appendAll(list.comments.map(v => HistoryItem(v, Some(list), v.user, "list.comment", v.creationDate)))
        history.appendAll(list.items.map(v => HistoryItem(v, Some(list), v.user, "list.item", v.creationDate)))

        history.appendAll(list.items.flatMap(i => {
          i.comments.map(c => HistoryItem(c, Some(i), c.user, "list.item.comment", c.creationDate))
        }))

        history.appendAll(list.items.flatMap(i => {
          i.votes.map(v => HistoryItem(v, Some(i), v.user, "list.item.vote", v.creationDate))
        }))
        // END History

        val context = Map(
            "list" -> list, 
            "tabId" -> tab,
            "history" -> history
            )

        htmlTemplateResponse(exchange, List("list.html", "layout/layout.html"), context)        
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def commentIndex(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")

    exchange.getRequestMethod.toLowerCase match {
      case "post" => {
        val me = requiredMe()
        val nvps = allNvps(exchange)
        val body = requiredFirstParam(nvps, "body")
        val commentId = ylistService.createYListComment(new Comment.Free(id, me.id, body))
        val list = ylistService.find(id)
        redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def commentDelete(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val commentId = pathVars("comment-id")

    ylistService.deactivateYListComment(commentId)
    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }
}

case class HistoryItem[T, U](
    val item: T,
    val refItem: Option[U],
    val user: User.Persisted,
    val kind: String,
    val eventDate: DateTime)
