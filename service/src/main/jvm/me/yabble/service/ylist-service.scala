package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait YListService extends IService[YList.Free, YList.Persisted, YList.Update] {
  def create(f: YList.Item.Free): String
  def createComment(f: Comment.Free): String
  def createItemComment(f: Comment.Free): String
  def activateItem(id: String)
  def deactivateItem(id: String)

  def deactivateComment(commentId: String): Boolean
  def deactivateItemComment(commentId: String): Boolean

  def addUser(lid: String, uid: String): Boolean
  def removeUser(lid: String, uid: String): Boolean

  def createItemVote(iid: String, uid: String)
  def deleteItemVote(iid: String, uid: String)

  def mergeUsers(srcUid: String, destUid: String)

  def findByItem(itemId: String): YList.Persisted
  def findByItemComment(commentId: String): YList.Persisted
  def findByItemVote(voteId: String): YList.Persisted
  def allByUser(uid: String): List[YList.Persisted]
  def allByListUser(uid: String): List[YList.Persisted]

  def findItem(itemId: String): YList.Item.Persisted
}

class YListServiceImpl(
    private val ylistDao: YListDao,
    private val ylistCommentDao: YListCommentDao,
    private val ylistItemDao: YListItemDao,
    private val ylistItemCommentDao: YListItemCommentDao,
    private val ylistItemVoteDao: YListItemVoteDao)
  extends Service(ylistDao)
  with YListService
  with Log
{
  override def create(f: YList.Item.Free) = {
    val id = ylistItemDao.create(f)
    f.imageIds.foreach(iid => ylistItemDao.addImage(id, iid))
    id
  }

  override def activateItem(id: String) = ylistItemDao.activate(id)
  override def deactivateItem(id: String) = ylistItemDao.deactivate(id)

  override def createComment(f: Comment.Free) = ylistCommentDao.create(f)
  override def createItemComment(f: Comment.Free) = ylistItemCommentDao.create(f)

  override def deactivateComment(commentId: String) = ylistCommentDao.deactivate(commentId)
  override def deactivateItemComment(commentId: String) = ylistItemCommentDao.deactivate(commentId)

  override def addUser(lid: String, uid: String) = {
    ylistDao.addUser(lid, uid)
  }

  override def removeUser(lid: String, uid: String) = ylistDao.removeUser(lid, uid)

  override def createItemVote(iid: String, uid: String) = ylistItemVoteDao.maybeActivateOrCreate(new Vote.Free(iid, uid))
  override def deleteItemVote(iid: String, uid: String) = ylistItemVoteDao.maybeDeactivate(new Vote.Free(iid, uid))

  override def mergeUsers(srcUid: String, destUid: String) {
    ylistDao.mergeUsers(srcUid, destUid)
    ylistItemDao.mergeUsers(srcUid, destUid)
    ylistCommentDao.mergeUsers(srcUid, destUid)
    ylistItemCommentDao.mergeUsers(srcUid, destUid)
    ylistItemVoteDao.mergeUsers(srcUid, destUid)
  }

  override def findByItem(itemId: String): YList.Persisted = ylistDao.findByItem(itemId)
  override def findByItemComment(commentId: String): YList.Persisted = ylistDao.findByItemComment(commentId)
  override def findByItemVote(voteId: String): YList.Persisted = ylistDao.findByItemVote(voteId)
  override def allByUser(uid: String) = ylistDao.allByUser(uid)
  override def allByListUser(uid: String) = ylistDao.allByListUser(uid)

  override def findItem(itemId: String) = ylistItemDao.find(itemId)
}
