package me.yabble.service.mail

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.common.proto.CommonProtos.Email
import me.yabble.common.wq.WorkQueue
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.service.proto.ServiceProtos.UserCommunication
import me.yabble.service.velocity.VelocityTemplate

import org.joda.time.LocalDate

class UserListNotificationPushMailer(
    private val fromEmail: String,
    private val fromName: String,
    private val template: VelocityTemplate,
    private val userService: UserService,
    private val ylistService: YListService,
    private val workQueue: WorkQueue)
  extends Log
{

  private def rootContext = Map(
      "LocalDate" -> classOf[LocalDate])

  def push(uid: String, listId: String, ns: List[UserNotification.Persisted]) {
    val user = userService.find(uid)
    val list = ylistService.find(listId)

    val context = rootContext + ("__user" -> user) + ("notifications" -> ns) + ("list" -> list)

    val emailBuilder = Email.newBuilder()
        .setFrom(fromEmail)
        .setFromName(fromName)
        .setSubject(sanatizeSubject("There have been %d updates on your yabble \"%s\"".format(ns.size, list.title)))
    emailBuilder.addTo(user.email.get)
    emailBuilder.setHtmlBody(emailHtmlBody(context))

    val bytes = UserCommunication.newBuilder()
        .setUserId(user.id)
        .setEmail(emailBuilder.build())
        .build()
        .toByteArray()

    workQueue.submit("user-communication", bytes)
  }

  private def emailHtmlBody(context: Map[String, Any]): String = {
    template.renderToString(List("notification/digest", "layout").map(htmlMailPath(_)), context)
  }

  private def sanatizeSubject(s: String) = replaceNewlinesWithSpace(s)

  private def htmlMailPath(name: String) = "mail/%s.html".format(name)
}
