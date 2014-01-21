package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.common.wq._
import me.yabble.common.proto.CommonProtos.Email
import me.yabble.service._
import me.yabble.service.model.UserNotification
import me.yabble.service.model.UserNotificationType._
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

case class MC(context: Map[String, Any], subject: String)

class UserNotificationPushWorker(
    txnTemplate: TransactionTemplate,
    private val workQueue: WorkQueue,
    private val userService: UserService,
    private val ylistService: YListService,
    private val fromEmail: String,
    private val fromName: String,
    private val template: VelocityTemplate)
  extends AbstractQueueWorker(txnTemplate, workQueue, "user-notification-push", 2)
  with Log
{
  private def rootContext = Map(
      "LocalDate" -> classOf[LocalDate])

  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val e = EntityEvent.parseFrom(item.getValue)
    val id = e.getEntityId

    if (e.getEntityType == EntityType.USER_NOTIFICATION && e.getEventType == EventType.CREATE) {
      val n = userService.findNotification(id)

      val optMc = n.kind match {
        case YLIST_INVITE => {
          val nli = Notification.YListInvite.parseFrom(n.data.get)
          val list = ylistService.find(nli.getListId)
          Some(MC(Map("list" -> list), "You've been invited: %s".format(list.title)))
        }

        case YLIST_ITEM_CREATE => {
          val list = ylistService.findByItem(n.refId.get)
          list.optionalItem(n.refId.get) match {
            case Some(listItem) => {
              userService.optionalUserListNotificationPreferenceByUserAndList(n.user.id, list.id).map(_.maxNotificationPushesPerDay).getOrElse(3) match {
                case -1 => { // Push immediately
                  listItem.title.orElse(listItem.body) match {
                    case Some(t) => Some(MC(Map("list" -> list), "There's a new item in your Yabble \"%s\"".format(t)))
                    case None => Some(MC(Map("list" -> list), "An new item has been created in your Yabble \"%s\"".format(list.title)))
                  }
                }

                case 0 => { // Never push
                  None
                }

                case c: Int => { // Push up to n times a day
                  userService.scheduleUserListNotificationPush(n.id, n.user.id, list.id, c)
                  None
                }
              }
            }

            case None => None
          }
        }

        case _ => None
      }
      // Maybe generate the email to send

      optMc.foreach(mc => {
        val emailBuilder = Email.newBuilder()
            .setFrom(fromEmail)
            .setFromName(fromName)
            .setSubject(sanatizeSubject(mc.subject))
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
    val ctx = context ++ rootContext + ("__user" -> n.user) + ("notifications" -> List(n))
    template.renderToString(List("notification/digest", "layout").map(htmlMailPath(_)), ctx)
  }

  private def sanatizeSubject(s: String) = replaceNewlinesWithSpace(s)

  private def htmlMailPath(name: String) = "mail/%s.html".format(name)
}
