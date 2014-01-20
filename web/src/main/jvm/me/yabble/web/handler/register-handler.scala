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

class RegisterHandler(
    val sessionService: SessionService,
    val userService: UserService,
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
    // If the user can login, get them outta here
    optionalMe() match {
      case Some(me) => {
        if (userService.canLogin(me.id)) {
          redirect(exchange)
          return
        }
      }

      case None => // Do nothing
    }

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
        try {
          formBuilder.setEmail(formField(optionalFirstParamValue(nvps, "email")))
          formBuilder.setName(formField(optionalFirstParamValue(nvps, "name")))
          formBuilder.setPassword(formField(optionalFirstParamValue(nvps, "password")))

          // TODO Validation
          var isValid = true
          if (!formBuilder.getEmail.hasValue()) {
            val b = formBuilder.getEmail.toBuilder()
            b.clearErrorMessage()
            b.addErrorMessage(message("required"))
            formBuilder.setEmail(b.build())
            isValid = false
          }

          if (formBuilder.getEmail.hasValue) {
            userService.optionalByEmail(formBuilder.getEmail.getValue) match {
              case Some(user) => {
                formBuilder.setEmail(formBuilder.getEmail.toBuilder().addErrorMessage(message("duplicate")).build())
                isValid = false
              }

              case None => // Do nothing
            }
          }

          if (formBuilder.getName.hasValue) {
            userService.optionalByName(formBuilder.getName.getValue) match {
              case Some(user) => {
                formBuilder.setName(formBuilder.getName.toBuilder().addErrorMessage(message("duplicate")).build())
                isValid = false
              }

              case None => // Do nothing
            }
          }

          val optPassword = if (formBuilder.getPassword.hasValue) {
            if (userService.isPasswordValid(formBuilder.getPassword.getValue)) {
              Some(formBuilder.getPassword.getValue)
            } else {
              log.info("Invalid password [{}]", formBuilder.getPassword.getValue)
              formBuilder.setPassword(formBuilder.getPassword.toBuilder().addErrorMessage(message("invalid")).build())
              isValid = false
              None
            }
          } else {
            None
          }

          if (!isValid) {
            redirectResponse(exchange, "/register")
            return
          }
              
          try {
            val name = if (formBuilder.getName.hasValue) Some(formBuilder.getName.getValue) else None
            val email = if (formBuilder.getEmail.hasValue) Some(formBuilder.getEmail.getValue) else None
            val uid = userService.create(new User.Free(name, email, None, None))

            optPassword.foreach(password => {
              userService.updatePassword(uid, password)
            })

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
          } catch {
            case e: Exception => {
              log.error(e.getMessage, e)
              redirectResponse(exchange, "/register")
            }
          }
        } finally {
          persistForm(formBuilder.build())
        }
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
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
