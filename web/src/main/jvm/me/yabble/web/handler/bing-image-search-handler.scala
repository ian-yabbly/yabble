package me.yabble.web.handler

import me.yabble.common.Log
import me.yabble.common.SecurityUtils.base64Encode
import me.yabble.common.http.client.HttpClient
import me.yabble.common.http.client.GetRequest
import me.yabble.common.http.client.{Response => HttpResponse}
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.service.velocity.VelocityTemplate
import me.yabble.web.service.SessionService

import com.google.common.base.Function
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

import com.sun.net.httpserver._

import org.springframework.util.AntPathMatcher

import java.io.InputStreamReader

import scala.collection.JavaConversions._

class BingImageSearchHandler(
    val sessionService: SessionService,
    val userService: UserService,
    val template: VelocityTemplate,
    val encoding: String,
    private val httpClient: HttpClient,
    bingApiKey: String)
  extends FormHandler
  with Log
{
  private val pathPatterns = List("/bing/image", "/bing/news")
  private val bingImageSearchUrl = "https://api.datamarket.azure.com/Bing/Search/Image"
  private val bingNewsSearchUrl = "https://api.datamarket.azure.com/Bing/Search/News"
  private val bingAuth = base64Encode((":"+bingApiKey).getBytes())

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => imageSearch(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 1 => newsSearch(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def imageSearch(exchange: HttpExchange, pathVars: Map[String, String]) {
    val nvps = queryNvps(exchange)
    val query = firstParamValue(nvps, "query")

    val params = Map(
        ("Query" -> "'%s'".format(query)),
        ("$format" -> "json"),
        ("ImageFilters" -> "'Size:Large'"))

    val get = new GetRequest(bingImageSearchUrl, params)
    get.addHeader("Authorization", "Basic " + bingAuth)
    get.setDoLog(true)
    val j = httpClient.execute(get, new Function[HttpResponse, JsonObject]() {
      override def apply(response: HttpResponse): JsonObject = {
        val jsonParser = new JsonParser()
        jsonParser.parse(new InputStreamReader(response.getContent)).getAsJsonObject
      }
    })

    Utils.jsonResponse(exchange, j, 200)
  }

  def newsSearch(exchange: HttpExchange, pathVars: Map[String, String]) {
    val nvps = queryNvps(exchange)
    val query = firstParamValue(nvps, "query")

    val params = Map(
        ("Query" -> "'%s'".format(query)),
        ("$format" -> "json"))

    val get = new GetRequest(bingNewsSearchUrl, params)
    get.addHeader("Authorization", "Basic " + bingAuth)
    get.setDoLog(true)
    val j = httpClient.execute(get, new Function[HttpResponse, JsonObject]() {
      override def apply(response: HttpResponse): JsonObject = {
        val jsonParser = new JsonParser()
        jsonParser.parse(new InputStreamReader(response.getContent)).getAsJsonObject
      }
    })

    Utils.jsonResponse(exchange, j, 200)
  }
}
