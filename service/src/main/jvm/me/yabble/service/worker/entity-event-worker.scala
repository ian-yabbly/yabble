package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.wq._
import me.yabble.service.ImageService
import me.yabble.service.proto.ServiceProtos.EntityEvent

import org.springframework.transaction._
import org.springframework.transaction.support._

class EntityEventWorker(
    txnTemplate: TransactionTemplate,
    workQueue: WorkQueue)
  extends AbstractQueueWorker(txnTemplate, workQueue, "entity-event", 2)
  with Log
{
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val e = EntityEvent.parseFrom(item.getValue)
    val id = e.getEntityId

    e.getEntityType match {
      case EntityEvent.EntityType.IMAGE => {
        e.getEventType match {
          case EntityEvent.EventType.CREATE => {
            workQueue.submit("image-preview", item.getValue)
          }

          case _ => // Do nothing
        }
      }

      case _ => // Do nothing
    }
  }
}
