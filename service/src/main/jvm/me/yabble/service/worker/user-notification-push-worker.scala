package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.common.wq._
import me.yabble.common.proto.CommonProtos.Email
import me.yabble.service._
import me.yabble.service.model.UserNotification
import me.yabble.service.model.UserNotificationType._
import me.yabble.service.proto.ServiceProtos._

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.context.Context

import org.springframework.transaction._
import org.springframework.transaction.support._

import java.io.StringWriter
import java.io.Writer

import scala.collection.JavaConversions._

case class MC(context: Map[String, Any], subject: String)

class UserNotificationPushWorker(
    txnTemplate: TransactionTemplate,
    private val workQueue: WorkQueue,
    private val userService: IUserService,
    private val ylistService: IYListService,
    private val fromEmail: String,
    private val fromName: String,
    private val velocityEngine: VelocityEngine,
    private val encoding: String)
  extends AbstractQueueWorker(txnTemplate, workQueue, "user-notification-push", 2)
  with Log
{
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val e = EntityEvent.parseFrom(item.getValue)
    val id = e.getEntityId

    if (e.getEntityType == EntityType.USER_NOTIFICATION && e.getEventType == EventType.CREATE) {
      val n = userService.findNotification(id)

      val optMc = n.kind match {
        case LIST_INVITE => {
          val nli = Notification.ListInvite.parseFrom(n.data.get)
          val list = ylistService.find(nli.getListId)
          Some(MC(Map("list" -> list), sanatizeSubject("You've been invited: %s".format(list.title))))
        }
        case _ => None
      }
      // Maybe generate the email to send

      optMc.foreach(mc => {
        val emailBuilder = Email.newBuilder()
            .setFrom(fromEmail)
            .setFromName(fromName)
            .setSubject(mc.subject)
        emailBuilder.addTo(n.user.email.get)
        emailBuilder.setHtmlBody(emailHtmlBody(n, mc.context))

        val bytes = UserCommunication.newBuilder()
            .setUserId(n.user.id)
            .setEmail(emailBuilder.build())
            .build()
            .toByteArray()

        workQueue.submit("user-communication", bytes)
      })
    }
  }

  private def emailHtmlBody(n: UserNotification.Persisted, context: Map[String, Any]): String = {
    var writer: Writer = null
    try {
      writer = new StringWriter()
      val vctx = new VelocityContext(context)
      velocityEngine.mergeTemplate("/mail/notification/%s.html".format(enumToCode(n.kind)), encoding, vctx, writer)
      writer.toString()
    } finally {
      if (writer != null) { writer.close() }
    }
  }

  private def sanatizeSubject(s: String) = replaceNewlinesWithSpace(s)
}
