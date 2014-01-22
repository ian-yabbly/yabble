package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.service.velocity.VelocityTemplate
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function

import org.joda.time.DateTime

import com.sun.net.httpserver._

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class YListHandler(
    val sessionService: SessionService,
    val userService: UserService,
    val encoding: String,
    private val ylistService: YListService,
    template: VelocityTemplate)
  extends TemplateHandler(template)
  with FormHandler
{
  private val pathPatterns = List(
      "/list/{id}",
      "/list/{id}/{slug}",
      "/list/{id}/{slug}/tab/{tab}",
      "/x/list/{id}/comment",
      "/x/list/{id}/comment/{comment-id}/delete",
      "/x/list/{id}/invite",
      "/x/list/{id}/unvite",
      "/x/list/{id}/item/{item-id}/comment",
      "/x/list/{id}/item/{item-id}/comment/{comment-id}/delete",
      "/x/list/{id}/item/{item-id}/vote",
      "/x/list/{id}/item/{item-id}/vote/delete",
      "/list/{id}/item/{item-id}",
      "/x/list/{id}/item/{item-id}/delete",
      "/x/list/{id}/notification-preference/{value}")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case  0 => redirectToList(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  1 => view(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  2 => view(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  3 => commentIndex(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  4 => commentDelete(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  5 => invite(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  6 => unvite(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  7 => itemCommentIndex(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  8 => itemCommentDelete(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case  9 => itemVote(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 10 => itemVoteDelete(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 11 => item(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 12 => itemDelete(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 13 => notificationPreference(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
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

        val b = list.toBuilder()
        b.items = list.items.sortBy(_.votes.size).reverse

        val context = Map(
            "list" -> b.toPersisted(),
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
        val body = firstParamValue(nvps, "body")
        val commentId = ylistService.createComment(new Comment.Free(id, me.id, body))
        val list = ylistService.find(id)
        redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def commentDelete(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val commentId = pathVars("comment-id")

    ylistService.deactivateComment(commentId)
    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }

  def invite(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val nvps = allNvps(exchange)
    val email = firstParamValue(nvps, "email")

    val user = userService.findOrCreateByEmail(email)
    ylistService.addUser(id, user.id)

    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }

  def unvite(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val nvps = allNvps(exchange)
    val uid = firstParamValue(nvps, "user")

    ylistService.removeUser(id, uid)

    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }

  def itemCommentIndex(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val itemId = pathVars("item-id")

    exchange.getRequestMethod.toLowerCase match {
      case "post" => {
        val me = requiredMe()
        val nvps = allNvps(exchange)
        val body = firstParamValue(nvps, "body")
        val commentId = ylistService.createItemComment(new Comment.Free(itemId, me.id, body))
        val list = ylistService.find(id)
        redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def itemCommentDelete(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val itemId = pathVars("item-id")
    val commentId = pathVars("comment-id")

    ylistService.deactivateItemComment(commentId)
    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }

  def itemVote(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val me = requiredMe()
    val itemId = pathVars("item-id")
    ylistService.createItemVote(itemId, me.id)
    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }

  def itemVoteDelete(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val me = requiredMe()
    val itemId = pathVars("item-id")
    ylistService.deleteItemVote(itemId, me.id)
    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }

  def item(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val itemId = pathVars("item-id")
    val list = ylistService.find(id)
    val context = Map("list" -> list, "item" -> list.item(itemId))
    htmlTemplateResponse(exchange, List("item.html", "layout/layout.html"), context)        
  }
 
  def itemDelete(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val me = requiredMe()
    val itemId = pathVars("item-id")
    ylistService.deactivateItem(itemId)
    val list = ylistService.find(id)
    redirectResponse(exchange, "/list/%s/%s".format(list.id, list.slug()))
  }
 
  def notificationPreference(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val me = requiredMe()
    val value = pathVars("value").toInt
    userService.createOrUpdate(new UserListNotificationPreference.Free(me.id, id, value))

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
