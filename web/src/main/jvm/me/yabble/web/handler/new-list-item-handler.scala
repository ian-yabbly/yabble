package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.service._
import me.yabble.service.velocity.VelocityTemplate
import me.yabble.web.proto.WebProtos._

import com.google.common.base.Function

import com.sun.net.httpserver._

import org.springframework.util.AntPathMatcher

import scala.collection.JavaConversions._

class NewYListItemHandler(
    val sessionService: SessionService,
    val userService: UserService,
    val encoding: String,
    private val ylistService: YListService,
    private val imageService: ImageService,
    val template: VelocityTemplate)
  extends TemplateHandler(template)
  with FormHandler
{
  private val pathPatterns = List("/new/list/{list-id}/item")

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
    val listId = pathVars("list-id")

    exchange.getRequestMethod.toLowerCase match {
      case "get" => {
        val list = ylistService.find(listId)
        val context = Map(
            "form" -> getOrCreateForm(listId),
            "list" -> list)
        htmlTemplateResponse(exchange, List("new-item.html", "layout/layout.html"), context)
      }

      case "post" => {
        val nvps = allNvps(exchange)
        val formBuilder = getOrCreateForm(listId).toBuilder()
        formBuilder.setTitle(formField(optionalFirstParamValue(nvps, "title")))
        formBuilder.setBody(formField(optionalFirstParamValue(nvps, "body")))

        formBuilder.clearImageUrl();
        params(nvps, "image-url").foreach(u => formBuilder.addImageUrl(u))

        val form = formBuilder.build()
        persistForm(form)

        // TODO Validation

        log.info("List ID [{}]", form.getListId)

        val me = meOrCreate()

        val imageIds = if (null == form.getImageUrlList) {
              Nil
            } else {
              form.getImageUrlList.map(url => {
                optional2Option(imageService.maybeCreateImageFromUrl(url))
              }).flatten.toList
            }

        ylistService.create(new YList.Item.Free(
            form.getListId,
            me.id,
            Option(form.getTitle.getValue),
            Option(form.getBody.getValue),
            imageIds))

        sessionService.withSession(true, new Function[Session, Session]() {
          override def apply(session: Session): Session = {
            session.toBuilder().clearListItemForm().build()
          }
        })

        val list = ylistService.find(listId)

        redirectResponse(exchange, "/list/%s/%s".format(listId, list.slug()))
      }

      case _ => throw new UnsupportedHttpMethod(exchange.getRequestMethod)
    }
  }

  private def getOrCreateForm(listId: String): ListItemForm = {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        if (!session.hasListItemForm()) {
          session.toBuilder().setListItemForm(
              ListItemForm.newBuilder()
                  .setListId(listId)
                  .setTitle(FormField.newBuilder().build())
                  .setBody(FormField.newBuilder().build())
                  .build()).build()
        } else {
          session
        }
      }
    }).getListItemForm
  }

  private def persistForm(form: ListItemForm) {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        session.toBuilder().setListItemForm(form).build()
      }
    })
  }
}
