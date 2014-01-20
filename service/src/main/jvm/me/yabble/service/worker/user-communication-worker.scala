package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.common.mail.MailgunMailer
import me.yabble.common.wq._
import me.yabble.service.UserService
import me.yabble.service.model.UserNotification
import me.yabble.service.model.UserNotificationType
import me.yabble.service.proto.ServiceProtos._

import org.springframework.transaction._
import org.springframework.transaction.support._

class UserCommunicationWorker(
    txnTemplate: TransactionTemplate,
    workQueue: WorkQueue,
    private val userService: UserService,
    private val mailer: MailgunMailer)
  extends AbstractQueueWorker(txnTemplate, workQueue, "user-communication", 2)
  with Log
{
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val com = UserCommunication.parseFrom(item.getValue)

    if (com.hasEmail()) {
      workQueue.submit("email", com.getEmail.toByteArray())
    }
  }
}
