package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.common.ctx.ExecutionContext
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.service.velocity.VelocityTemplate
import me.yabble.web.service._
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function
import com.google.gson._

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
  extends NewFormHandler(template, classOf[UserForm], Session.FormEntry.Type.USER)
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
          case 1 => form(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def logout(exchange: HttpExchange, pathVars: Map[String, String]) {
    val ctx = ExecutionContext.get()
    ctx.optionalAttribute("web-session-id", classOf[String]).foreach(sessionId => {
      sessionService.remove(sessionId)
    })

    val cookieExpr = new DateTime(0l)
    exchange.getResponseHeaders.add("Set-Cookie", "%s=; Path=/; Domain=%s; Expires=%s".format(
        sessionCookieName,
        sessionCookieDomain,
        DateUtils.formatDate(cookieExpr.toDate())))

    exchange.getResponseHeaders.set("Pragma", "no-cache")
    exchange.getResponseHeaders.set("Cache-Control", "no-cache")

    redirectResponse(exchange, "/")
  }

  def form(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val context = collection.mutable.Map[String, Any]()

        optionalMe().foreach(me => {
          context.put("form", getOrCreateForm())
          context.put("myLists", ylistService.allByUser(me.id))
          context.put("contribLists", ylistService.allByListUser(me.id))
        })

        htmlTemplateResponse(exchange, List("me.html", "layout/layout.html"), context.toMap)
      }

      case "post" => {
        val me = requiredMe()
        val nvps = allNvps(exchange)

        val fb = getOrCreateForm().toBuilder()

        // Bind
        optionalFirstParamValue(nvps, "email") match {
          case Some(email) => fb.setEmail(formField(email))
          case None => fb.setEmail(formField())
        }

        optionalFirstParamValue(nvps, "name") match {
          case Some(name) => fb.setName(formField(name))
          case None => fb.setName(formField())
        }

        optionalFirstParamValue(nvps, "password") match {
          case Some(password) => fb.setPassword(formField(password))
          case None => fb.setPassword(formField())
        }
        // END Bind

        var isValid = true

        if (fb.getEmail.hasValue()) {
          val email = fb.getEmail.getValue
          userService.optionalByEmail(email).filter(_.id != me.id).foreach(user => {
            // Whoops, found a different user with that email
            fb.setEmail(fb.getEmail.toBuilder().addErrorMessage(message("duplicate")))
            isValid = false
          })
        }

        if (fb.getName.hasValue()) {
          val name = fb.getName.getValue
          userService.optionalByName(name).filter(_.id != me.id).foreach(user => {
            // Whoops, found a different user with that name
            fb.setName(fb.getName.toBuilder().addErrorMessage(message("duplicate")))
            isValid = false
          })
        }

        if (!isValid) {
          val form = fb.build()
          persistForm(form)
          if (isXhr(exchange)) {
            Utils.jsonResponse(exchange, xhrJson("error", form))
          } else {
            redirectResponse(exchange, "/me")
          }
        } else {
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
          clearForm()

          if (isXhr(exchange)) {
            Utils.jsonResponse(exchange, xhrJson("success"))
          } else {
            redirectResponse(exchange, "/me")
          }
        }

      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  override protected def deserializeForm(bytes: Array[Byte]): UserForm = UserForm.parseFrom(bytes)

  override protected def createNewForm(): UserForm = optionalMe() match {
    case Some(user) => {
      UserForm.newBuilder()
          .setName(formField(user.name))
          .setEmail(formField(user.email))
          .setPassword(formField())
          .build()
    }
    case None => {
      UserForm.newBuilder()
          .setName(formField())
          .setEmail(formField())
          .setPassword(formField())
          .build()
    }
  }

  private def xhrJson(statusCode: String, form: UserForm = null): JsonObject = {
    val j = new JsonObject()
    j.addProperty("statusCode", statusCode)
    if (null != form) {
      val f = new JsonObject()

      val n = new JsonObject()
      if (form.getName.hasValue()) {
        n.addProperty("value", form.getName.getValue)
      }
      val nerrs = new JsonArray()
      form.getName.getErrorMessageList.foreach(message => {
        val m = new JsonObject()
        m.addProperty("code", message.getCode)
        nerrs.add(m)
      })
      n.add("errors", nerrs)
      f.add("name", n)

      val e = new JsonObject()
      if (form.getEmail.hasValue()) {
        e.addProperty("value", form.getEmail.getValue)
      }
      val eerrs = new JsonArray()
      form.getEmail.getErrorMessageList.foreach(message => {
        val m = new JsonObject()
        m.addProperty("code", message.getCode)
        eerrs.add(m)
      })
      e.add("errors", eerrs)
      f.add("email", e)

      val p = new JsonObject()
      if (form.getPassword.hasValue()) {
        n.addProperty("value", form.getPassword.getValue)
      }
      val perrs = new JsonArray()
      form.getPassword.getErrorMessageList.foreach(message => {
        val m = new JsonObject()
        m.addProperty("code", message.getCode)
        perrs.add(m)
      })
      p.add("errors", perrs)
      f.add("password", p)

      j.add("form", f)
    }
    return j
  }
}
