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

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class NewYListHandler(
    val sessionService: SessionService,
    val userService: IUserService,
    val encoding: String,
    private val ylistService: IYListService,
    template: VelocityTemplate)
  extends TemplateHandler(template)
  with FormHandler
{
  private val pathPatterns = List("/new")

  override def maybeHandle(exchange: HttpExchange): Boolean = {
    val pathMatcher = new AntPathMatcher()
    val path = noContextPath(exchange)

    pathPatterns
        .zipWithIndex
        .find(t => pathMatcher.`match`(t._1, path))
        .map(t => t._2 match {
          case 0 => form(exchange, pathMatcher.extractUriTemplateVariables(t._1, path).toMap)
          case _ => error(s"Unexpected match [${t._1}]")
        })
        .isDefined
  }

  def form(exchange: HttpExchange, pathVars: Map[String, String]) {
    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val context = Map("form" -> getOrCreateForm())
        htmlTemplateResponse(exchange, List("new.html", "layout/layout.html"), context)
      }

      case "post" => {
        val nvps = allNvps(exchange)
        val formBuilder = getOrCreateForm().toBuilder()
        formBuilder.setTitle(formField(firstParamValue(nvps, "title")))
        formBuilder.setBody(formField(firstParamValue(nvps, "body")))
        val form = formBuilder.build()
        persistForm(form)

        // TODO Validation

        val me = meOrCreate()
        val lid = ylistService.create(new YList.Free(
            me.id,
            form.getTitle.getValue,
            Option(form.getBody.getValue)))

        sessionService.withSession(true, new Function[Session, Session]() {
          override def apply(session: Session): Session = {
            session.toBuilder().clearListForm().build()
          }
        })

        val list = ylistService.find(lid)

        redirectResponse(exchange, "/list/%s/%s".format(lid, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  private def getOrCreateForm(): ListForm = {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        if (!session.hasListForm()) {
          session.toBuilder().setListForm(
              ListForm.newBuilder()
                  .setTitle(FormField.newBuilder().build())
                  .setBody(FormField.newBuilder().build())
                  .build()).build()
        } else {
          session
        }
      }
    }).getListForm
  }

  private def persistForm(form: ListForm) {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        session.toBuilder().setListForm(form).build()
      }
    })
  }
}
