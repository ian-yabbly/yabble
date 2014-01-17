package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.common.wq._
import me.yabble.common.proto.CommonProtos.Email
import me.yabble.service._
import me.yabble.service.model.User
import me.yabble.service.proto.ServiceProtos._
import me.yabble.service.velocity.VelocityTemplate

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.context.Context

import org.joda.time.LocalDate

import org.springframework.transaction._
import org.springframework.transaction.support._

import java.io.StringWriter
import java.io.Writer

import scala.collection.JavaConversions._

class UserPushWorker(
    txnTemplate: TransactionTemplate,
    private val workQueue: WorkQueue,
    private val ylistService: IYListService,
    private val fromEmail: String,
    private val fromName: String,
    private val template: VelocityTemplate)
  extends AbstractQueueWorker(txnTemplate, workQueue, "user-push", 2)
  with Log
{
  private def rootContext = Map(
      "LocalDate" -> classOf[LocalDate])

  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val push = UserPush.parseFrom(item.getValue)

    if (push.hasListLink()) {
      val list = ylistService.find(push.getListLink.getListId)
      list.user.email match {
        case Some(email) => {
          val emailBuilder = Email.newBuilder()
              .setFrom(fromEmail)
              .setFromName(fromName)
              .setSubject(sanatizeSubject(list.title))
          emailBuilder.addTo(email)
          emailBuilder.setHtmlBody(emailHtmlBody(list.user, "list-link", Map("list" -> list)))
          
          val bytes = UserCommunication.newBuilder()
              .setUserId(list.user.id)
              .setEmail(emailBuilder.build())
              .build()
              .toByteArray()

          workQueue.submit("user-communication", bytes)
        }

        case None => log.info("Cannot mail list link because user email is not present [{}]", list.id)
      }
    } else {
      log.error("Unexpected UserPush type")
    }
  }

  private def emailHtmlBody(user: User.Persisted, name: String, context: Map[String, Any]): String = {
    val ctx = context ++ rootContext + ("__user" -> user)
    template.renderToString(List(name, "layout").map(htmlMailPath(_)), ctx)
  }

  private def sanatizeSubject(s: String) = replaceNewlinesWithSpace(s)

  private def htmlMailPath(name: String) = "mail/%s.html".format(name)
}
