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
    val userService: UserService,
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
        try {
          formBuilder.setName(formField(optionalFirstParamValue(nvps, "name")))
          formBuilder.setPassword(formField(optionalFirstParamValue(nvps, "password")))

          var isValid = true
          if (!formBuilder.getName.hasValue) {
            formBuilder.setName(formBuilder.getName().toBuilder().addErrorMessage(message("required")))
            isValid = false
          }

          if (!formBuilder.getPassword.hasValue) {
            formBuilder.setPassword(formBuilder.getPassword().toBuilder().addErrorMessage(message("required")))
            isValid = false
          }

          val optUser = userService.optionalByNameOrEmail(formBuilder.getName.getValue) match {
            case Some(user) => {
              Some(user)
            }
            case None => {
              formBuilder.setName(formBuilder.getName().toBuilder().addErrorMessage(message("not-found")))
              isValid = false
              None
            }
          }

          if (!isValid) {
            redirectResponse(exchange, "/login")
            return
          }

          // optUser must be Some(user) here
          val user = optUser.get

          val optSession = optionalSession()
          optSession.filter(_.hasUserId)
              .filter(_.getUserId != user.id)
              .foreach(session => {
                val sessionUser = userService.find(session.getUserId)
                if (sessionUser.email.orElse(sessionUser.name).isEmpty) {
                  // Anonymous user... do a merge
                  yabbleService.mergeUsers(sessionUser.id, user.id)
                }
              })

          val session = sessionService.withSession(true, new Function[Session, Session]() {
            override def apply(session: Session): Session = {
              session.toBuilder()
                  .clearLoginForm()
                  .setUserId(user.id)
                  .build()
            }
          })

          redirect(exchange)
        } finally {
          val form = formBuilder.build()
          persistForm(form)
        }
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  def forgot(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "post" => {
        val nvps = allNvps(exchange)
        val formBuilder = getOrCreateForm().toBuilder()
        try {
          formBuilder.setName(formField(optionalFirstParamValue(nvps, "name")))

          var isValid = true
          if (!formBuilder.getName.hasValue) {
            formBuilder.setName(formBuilder.getName().toBuilder().addErrorMessage(message("required")))
            isValid = false
          }

          val optUser = userService.optionalByNameOrEmail(formBuilder.getName.getValue) match {
            case Some(user) => {
              Some(user)
            }
            case None => {
              formBuilder.setName(formBuilder.getName().toBuilder().addErrorMessage(message("not-found")))
              isValid = false
              None
            }
          }

          if (!isValid) {
            redirectResponse(exchange, "/login")
            return
          }

          // Send a forgot password mail
        } finally {
          val form = formBuilder.build()
          persistForm(form)
        }
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

  private def redirect(exchange: HttpExchange) {
    optional2Option(sessionService.optional()) match {
      case Some(session) => {
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

      case None => redirectResponse(exchange, "/")
    }
  }
}
