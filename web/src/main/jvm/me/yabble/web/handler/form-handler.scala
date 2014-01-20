package me.yabble.web.handler

import me.yabble.common.Predef._
import me.yabble.common.Log
import me.yabble.common.TextFormat
import me.yabble.common.TextUtils
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.web.proto.WebProtos._
import me.yabble.web.service._
import me.yabble.service.velocity.VelocityTemplate
import me.yabble.web.template.{Utils => TemplateUtils}
import me.yabble.web.template.{Format => TemplateFormat}

import com.google.common.base.Function
import com.google.gson._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.sun.net.httpserver._

import org.apache.commons.io.IOUtils
import org.apache.commons.lang.exception.ExceptionUtils
import org.apache.http.NameValuePair
import org.apache.http.client.utils.URLEncodedUtils

import java.net.HttpCookie

import scala.collection.JavaConversions._
import scala.collection.mutable.{Map => MutableMap}

abstract class NewFormHandler[F <: com.google.protobuf.Message](
    template: VelocityTemplate,
    private val formClass: Class[F],
    private val formType: Session.FormEntry.Type)
  extends TemplateHandler(template)
{
  protected def deserializeForm(bytes: Array[Byte]): F
  protected def createNewForm(): F

  protected def getOrCreateForm(): F = {
    val optForm = optionalSession() match {
      case Some(session) => {
        session.getFormList()
            .map(formBytes => Session.FormEntry.parseFrom(formBytes.toByteArray()))
            .find(_.getType == formType)
            .map(formEntry => deserializeForm(formEntry.getSerializedForm.toByteArray()))
      }
      case None => None
    }

    optForm match {
      case Some(form) => form
      case None => {
        val form = createNewForm()
        persistForm(form)
        form
      }
    }
  }

  protected def persistForm(form: F) {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        val b = session.toBuilder()
        clearAll(b)
        b.addForm(Session.FormEntry.newBuilder()
              .setType(formType)
              .setSerializedForm(form.toByteString())
              .build())
            .build()
      }
    })
  }

  protected def clearForm() {
    sessionService.withSession(true, new Function[Session, Session]() {
      override def apply(session: Session): Session = {
        val b = session.toBuilder()
        clearAll(b)
        b.build()
      }
    })
  }

  private def clearAll(b: Session.Builder) {
    val forms = b.getFormList.filterNot(_.getType == formType)
    b.clearForm()
    forms.foreach(f => b.addForm(f))
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

  protected def formField(value: Option[String] = None): FormField = value match {
    case Some(v) => FormField.newBuilder().setValue(v).build()
    case None => FormField.newBuilder().build()
  }

  protected def formField(value: String): FormField = formField(Option(value))

  protected def message(code: String, params: List[String] = Nil, displayValue: Option[String] = None) = {
    val b = Message.newBuilder().setCode(code)
    params.foreach(p => b.addParam(p))
    displayValue.foreach(v => b.setDisplayValue(v))
    b.build()
  }
}
