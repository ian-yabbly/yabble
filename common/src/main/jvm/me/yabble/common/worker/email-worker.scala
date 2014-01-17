package me.yabble.common.worker

import me.yabble.common.Log
import me.yabble.common.mail.MailgunMailer
import me.yabble.common.wq._
import me.yabble.common.proto.CommonProtos.Email

import org.springframework.transaction._
import org.springframework.transaction.support._
    
class EmailWorker(
    txnTemplate: TransactionTemplate,
    private val workQueue: WorkQueue,
    private val mailer: MailgunMailer)
  extends AbstractQueueWorker(txnTemplate, workQueue, "email", 2)
  with Log
{ 
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val email = Email.parseFrom(item.getValue)
    mailer.send(email)
  }         
}         
