package me.yabble.web.server

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.http.impl.cookie.DateUtils

import org.springframework.util.AntPathMatcher

import java.io.IOException
import java.util.regex.Pattern

import scala.collection.JavaConversions._

class ImageHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val encoding: String,
    private val imageService: ImageService)
  extends Handler
{
  def VERSION_PATTERN = Pattern.compile("^/s/v-[\\p{Alnum}_-]+(/.*)$")

  private val pathPatterns = List("/i/{id}")

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
    val id = pathVars("id")
    val nvps = queryNvps(exchange)

    firstNvp(nvps, "t") match {
      case Some(transform) => {
        optional2Option(imageService.optionalByOriginalIdAndTransform(id, transform)) match {
          case Some(i) => if (isRequestSecure(exchange)) {
                redirectResponse(exchange, i.secureUrl, true)
              } else {
                redirectResponse(exchange, i.url, true)
              }
          case None => plainTextResponse(exchange, Some("Image not found [%s] [%s]".format(id, transform)), 404)
        }
      }

      case None => optional2Option(imageService.optional(id)) match {
        case Some(i) => {
          if (isRequestSecure(exchange)) {
            redirectResponse(exchange, i.secureUrl, true)
          } else {
            redirectResponse(exchange, i.url, true)
          }
        }
        case None => plainTextResponse(exchange, Some("Image not found [%s]".format(id)), 404)
      }
    }
  }
}
