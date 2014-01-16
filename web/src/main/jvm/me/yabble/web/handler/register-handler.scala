package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.web.template.VelocityTemplate
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function

import com.sun.net.httpserver._

import org.apache.http.impl.cookie.DateUtils

import org.joda.time.DateTime;

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class RegisterHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val encoding: String,
    template: VelocityTemplate)
  extends TemplateHandler(template)
  with FormHandler
{
  private val pathPatterns = List("/register")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => register(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def register(exchange: HttpExchange, pathVars: Map[String, String]) {
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
        htmlTemplateResponse(exchange, List("register.html", "layout/layout.html"), context)        
      }

      case "post" => {
        val nvps = allNvps(exchange)
        val formBuilder = getOrCreateForm().toBuilder()
        formBuilder.setEmail(formField(optionalFirstParamValue(nvps, "email")))
        formBuilder.setName(formField(optionalFirstParamValue(nvps, "name")))
        formBuilder.setPassword(formField(optionalFirstParamValue(nvps, "password")))

        // TODO Validation
        var isValid = true
        if (!formBuilder.getEmail.hasValue()) {
          val b = formBuilder.getEmail.toBuilder()
          b.clearErrorMessage()
          b.addErrorMessage(Message.newBuilder().setCode("required").build())
          formBuilder.setEmail(b.build())
          isValid = false
        }

        val form = formBuilder.build()
        persistForm(form)

        if (!isValid) {
          redirectResponse(exchange, "/register")
          return
        }

        userService.optionalByEmail(form.getEmail.getValue) match {
          case Some(user) => {
            redirectResponse(exchange, "/register")
          }
            
          case None => {
            val uid = userService.create(new User.Free(Option(form.getName.getValue), Option(form.getEmail.getValue), None, None))

            val session = sessionService.withSession(true, new Function[Session, Session]() {
              override def apply(session: Session): Session = {
                session.toBuilder()
                    .clearRegisterForm()
                    .setUserId(uid)
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
        }
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  private def getOrCreateForm(): RegisterForm = {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        if (!session.hasRegisterForm()) {
          session.toBuilder().setRegisterForm(
              RegisterForm.newBuilder()
                  .setEmail(FormField.newBuilder().build())
                  .setName(FormField.newBuilder().build())
                  .setPassword(FormField.newBuilder().build())
                  .build()).build()
        } else {
          session
        }
      }
    }).getRegisterForm
  }

  private def persistForm(form: RegisterForm) {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        session.toBuilder().setRegisterForm(form).build()
      }
    })
  }
}
