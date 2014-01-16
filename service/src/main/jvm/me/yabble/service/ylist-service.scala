package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IYListService extends IService[YList.Free, YList.Persisted, YList.Update] {
  def create(f: YList.Item.Free): String
  def createComment(f: Comment.Free): String
  def createItemComment(f: Comment.Free): String

  def deactivateComment(commentId: String): Boolean
  def deactivateItemComment(commentId: String): Boolean

  def addUser(lid: String, uid: String): Boolean
  def removeUser(lid: String, uid: String): Boolean

  def createItemVote(iid: String, uid: String)
  def deleteItemVote(iid: String, uid: String)
}

class YListService(
    private val ylistDao: YListDao,
    private val ylistCommentDao: YListCommentDao,
    private val ylistItemDao: YListItemDao,
    private val ylistItemCommentDao: YListItemCommentDao,
    private val ylistItemVoteDao: YListItemVoteDao)
  extends Service(ylistDao)
  with IYListService
  with Log
{
  override def create(f: YList.Item.Free) = {
    val id = ylistItemDao.create(f)
    f.imageIds.foreach(iid => ylistItemDao.addImage(id, iid))
    id
  }

  override def createComment(f: Comment.Free) = ylistCommentDao.create(f)
  override def createItemComment(f: Comment.Free) = ylistItemCommentDao.create(f)

  override def deactivateComment(commentId: String) = ylistCommentDao.deactivate(commentId)
  override def deactivateItemComment(commentId: String) = ylistItemCommentDao.deactivate(commentId)

  override def addUser(lid: String, uid: String) = ylistDao.addUser(lid, uid)
  override def removeUser(lid: String, uid: String) = ylistDao.removeUser(lid, uid)

  override def createItemVote(iid: String, uid: String) = ylistItemVoteDao.maybeActivateOrCreate(new Vote.Free(iid, uid))
  override def deleteItemVote(iid: String, uid: String) = ylistItemVoteDao.maybeDeactivate(new Vote.Free(iid, uid))
}