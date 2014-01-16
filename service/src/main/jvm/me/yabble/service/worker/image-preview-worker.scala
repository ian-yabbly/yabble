package me.yabble.service.worker

import me.yabble.common.Log
import me.yabble.common.wq._
import me.yabble.service.ImageService
import me.yabble.service.proto.ServiceProtos.EntityEvent

import org.springframework.transaction._
import org.springframework.transaction.support._

class ImagePreviewWorker(
    txnTemplate: TransactionTemplate,
    workQueue: WorkQueue,
    private val imageService: ImageService)
  extends AbstractQueueWorker(txnTemplate, workQueue, "image-preview", 2)
  with Log
{
  override protected def handleWorkItem(item: WorkQueue.Item, status: TransactionStatus) {
    val e = EntityEvent.parseFrom(item.getValue)
    val imageId = e.getEntityId
    imageService.maybeSetImagePreviewData(imageId)
  }
}
