package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.http.impl.cookie.DateUtils

import org.springframework.util.AntPathMatcher

import java.io.IOException
import java.util.regex.Pattern

import scala.collection.JavaConversions._

class ImageHandler(
    val sessionService: SessionService,
    val userService: UserService,
    val encoding: String,
    private val imageService: ImageService)
  extends Handler
{
  def VERSION_PATTERN = Pattern.compile("^/s/v-[\\p{Alnum}_-]+(/.*)$")

  private val pathPatterns = List( "/i/user/{id}", "/i/{id}")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => user(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 1 => index(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def user(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val nvps = queryNvps(exchange)

    optionalFirstParamValue(nvps, "t") match {
      case Some(transform) => {
        userService.optional(id) match {
          case Some(user) => user.image match {
            case Some(image) => imageTransformResponse(exchange, image.id, transform)
            case None => imageTransformResponse(exchange, imageService.getDefaultProfileImage().id, transform)
          }
          case None => imageTransformResponse(exchange, imageService.getDefaultProfileImage().id, transform)
        }
      }
      case None => {
        userService.optional(id) match {
          case Some(user) => user.image match {
            case Some(image) => imageResponse(exchange, image)
            case None => imageResponse(exchange, imageService.getDefaultProfileImage())
          }
          case None => imageResponse(exchange, imageService.getDefaultProfileImage())
        }
      }
    }
  }

  def index(exchange: HttpExchange, pathVars: Map[String, String]) {
    val id = pathVars("id")
    val nvps = queryNvps(exchange)

    optionalFirstParamValue(nvps, "t") match {
      case Some(transform) => imageTransformResponse(exchange, id, transform)
      case None => imageResponse(exchange, id)
    }
  }

  private def imageTransformResponse(exchange: HttpExchange, id: String, transform: String) {
    optional2Option(imageService.optionalByOriginalIdAndTransform(id, transform)) match {
      case Some(image) => imageResponse(exchange, image)
      case None => notFoundResponse(exchange)
    }
  }

  private def notFoundResponse(exchange: HttpExchange) {
    plainTextResponse(exchange, Some("Image not found"), 404)
  }

  private def imageResponse(exchange: HttpExchange, id: String) {
    try {
      imageResponse(exchange, imageService.find(id))
    } catch {
      case e: NotFoundException => notFoundResponse(exchange)
    }
  }

  private def imageResponse(exchange: HttpExchange, image: Image.Persisted) {
    if (isRequestSecure(exchange)) {
      redirectResponse(exchange, image.secureUrl, true)
    } else {
      redirectResponse(exchange, image.url, true)
    }
  }
}
