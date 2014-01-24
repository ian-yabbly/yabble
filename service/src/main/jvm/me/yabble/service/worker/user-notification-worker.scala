package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.TextUtils._
import me.yabble.common.wq._
import me.yabble.service._
import me.yabble.service.model.UserNotification
import me.yabble.service.model.UserNotificationType
import me.yabble.service.proto.ServiceProtos._

import org.springframework.transaction._
import org.springframework.transaction.support._

class UserNotificationWorker(
    txnTemplate: TransactionTemplate,
    workQueue: WorkQueue,
    private val userService: UserService,
    private val ylistService: YListService)
  extends AbstractQueueWorker(txnTemplate, workQueue, "user-notification", 2)
  with Log
{
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val e = EntityEvent.parseFrom(item.getValue)
    val id = e.getEntityId

    log.info("Handling event [{}] [{}]", enumToCode(e.getEntityType), enumToCode(e.getEventType))

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

      case EntityType.YLIST_ITEM => {
        e.getEventType match {
          case EventType.CREATE => {
            val list = ylistService.findByItem(id)
            list.optionalItem(id).foreach(item => {
              (list.users :+ list.user).filter(_.id != item.user.id).foreach(user => {
                userService.create(new UserNotification.Free(
                    user.id,
                    UserNotificationType.YLIST_ITEM_CREATE,
                    Some(id),
                    Some(e.getEntityType),
                    Some(Notification.YListItem.newBuilder()
                        .setListId(list.id)
                        .setListItemId(id)
                        .setSource(e)
                        .build()
                        .toByteArray())))
                })
            })
          }

          case _ => // Do nothing
        }
      }

      case EntityType.YLIST_ITEM_COMMENT => {
        e.getEventType match {
          case EventType.CREATE => {
            val list = ylistService.findByItemComment(id)
            list.optionalItemComment(id).foreach(comment => {
              val item = list.itemByComment(id)

              (list.users :+ list.user).filter(_.id != comment.user.id).foreach(user => {
                userService.create(new UserNotification.Free(
                    user.id,
                    UserNotificationType.YLIST_ITEM_COMMENT_CREATE,
                    Some(id),
                    Some(e.getEntityType),
                    Some(Notification.YListItemComment.newBuilder()
                        .setListId(list.id)
                        .setListItemId(item.id)
                        .setListItemCommentId(id)
                        .setSource(e)
                        .build()
                        .toByteArray())))
                })
            })
          }

          case _ => // Do nothing
        }
      }

      case _ => // Do nothing
    }
  }
}
