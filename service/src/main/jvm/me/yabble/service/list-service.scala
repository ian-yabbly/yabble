package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IYListService extends IService[YList.Free, YList.Persisted, YList.Update] {
  def create(f: YList.Item.Free): String
  def createYListComment(f: Comment.Free): String
  def createYListItemComment(f: Comment.Free): String

  def deactivateYListComment(commentId: String): Boolean
  def deactivateYListItemComment(commentId: String): Boolean
}

class YListService(
    private val ylistDao: YListDao,
    private val ylistCommentDao: YListCommentDao,
    private val ylistItemDao: YListItemDao,
    private val ylistItemCommentDao: YListItemCommentDao)
  extends Service(ylistDao)
  with IYListService
  with Log
{
  override def create(f: YList.Item.Free) = {
    val id = ylistItemDao.create(f)
    f.imageIds.foreach(iid => ylistItemDao.addImage(id, iid))
    id
  }

  override def createYListComment(f: Comment.Free) = ylistCommentDao.create(f)
  override def createYListItemComment(f: Comment.Free) = ylistItemCommentDao.create(f)

  override def deactivateYListComment(commentId: String) = ylistCommentDao.deactivate(commentId)
  override def deactivateYListItemComment(commentId: String) = ylistItemCommentDao.deactivate(commentId)
}
