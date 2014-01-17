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

class LoginHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val encoding: String,
    template: VelocityTemplate)
  extends TemplateHandler(template)
  with FormHandler
{
  private val pathPatterns = List("/login", "/forgot")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => login(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case 1 => forgot(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def login(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val nvps = allNvps(exchange)

        optionalFirstParamValue(nvps, "r").foreach(p => {
          sessionService.withSession(true, new Function[Session, Session]() {
            override def apply(session: Session): Session = {
              session.toBuilder()
                  .setAfterLoginRedirectPath(p)
                  .build()
            }
          })
        })

        val context = Map("form" -> getOrCreateForm())
        htmlTemplateResponse(exchange, List("login.html", "layout/layout.html"), context)        
      }

      case "post" => {
        val nvps = allNvps(exchange)
        val formBuilder = getOrCreateForm().toBuilder()
        formBuilder.setName(formField(optionalFirstParamValue(nvps, "name")))
        formBuilder.setPassword(formField(optionalFirstParamValue(nvps, "password")))
        val form = formBuilder.build()
        persistForm(form)

        // TODO Validation

        userService.optionalByNameOrEmail(form.getName.getValue) match {
          case Some(user) => {
            val session = sessionService.withSession(true, new Function[Session, Session]() {
              override def apply(session: Session): Session = {
                session.toBuilder()
                    .clearLoginForm()
                    .setUserId(user.id)
                    .build()
              }
            })

            if (session.hasAfterLoginRedirectPath()) {
              val path = session.getAfterLoginRedirectPath
              sessionService.withSession(true, new Function[Session, Session]() {
                override def apply(session: Session): Session = {
                  session.toBuilder()
                      .clearAfterLoginRedirectPath()
                      .build()
                }
              })
              redirectResponse(exchange, path)
            } else {
              redirectResponse(exchange, "/")
            }
          }

          case None => {
            redirectResponse(exchange, "/login")
          }
        }
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def forgot(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "post" => {
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  private def getOrCreateForm(): LoginForm = {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        if (!session.hasLoginForm()) {
          session.toBuilder().setLoginForm(
              LoginForm.newBuilder()
                  .setName(FormField.newBuilder().build())
                  .setPassword(FormField.newBuilder().build())
                  .build()).build()
        } else {
          session
        }
      }
    }).getLoginForm
  }

  private def persistForm(form: LoginForm) {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        session.toBuilder().setLoginForm(form).build()
      }
    })
  }
}
