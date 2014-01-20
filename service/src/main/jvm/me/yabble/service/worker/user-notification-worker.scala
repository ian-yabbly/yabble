package me.yabble.service.worker

import me.yabble.common.Log
//import me.yabble.common.TextUtils._
import me.yabble.common.wq._
import me.yabble.service.UserService
import me.yabble.service.model.UserNotification
import me.yabble.service.model.UserNotificationType
import me.yabble.service.proto.ServiceProtos._

import org.springframework.transaction._
import org.springframework.transaction.support._

class UserNotificationWorker(
    txnTemplate: TransactionTemplate,
    workQueue: WorkQueue,
    private val userService: UserService)
  extends AbstractQueueWorker(txnTemplate, workQueue, "user-notification", 2)
  with Log
{
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val e = EntityEvent.parseFrom(item.getValue)
    val id = e.getEntityId

    //log.info("Handling event [{}] [{}]", enumToCode(e.getEntityType), enumToCode(e.getEventType))

    e.getEntityType match {
      case EntityType.YLIST_USER => {
        e.getEventType match {
          case EventType.CREATE => {
            userService.create(new UserNotification.Free(
                e.getUserId,
                UserNotificationType.YLIST_INVITE,
                Some(id),
                Some(e.getEntityType),
                Some(Notification.YListInvite.newBuilder()
                    .setListId(id)
                    .setUserId(e.getUserId)
                    .setSource(e)
                    .build()
                    .toByteArray())))
          }

          case _ => // Do nothing
        }
      }

      case _ => // Do nothing
    }
  }
}
