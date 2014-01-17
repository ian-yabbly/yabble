package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IUserService extends IService[User.Free, User.Persisted, User.Update] {
  def canLogin(uid: String): Boolean
  def updatePassword(uid: String, clear: String)
  def findOrCreateByEmail(email: String): User.Persisted
  def optionalByNameOrEmail(nameOrEmail: String): Option[User.Persisted]
  def optionalByEmail(email: String): Option[User.Persisted]

  // User notifications
  def create(f: UserNotification.Free): String
  def findNotification(id: String): UserNotification.Persisted
  // END User notifications
}

class UserService(
    private val userDao: UserDao,
    private val userAuthDao: UserAuthDao,
    private val userNotificationDao: UserNotificationDao)
  extends Service(userDao)
  with IUserService
  with Log
{
  override def canLogin(uid: String) = {
    val user = find(uid)
    userAuthDao.optionalByUser(uid).isDefined && user.email.orElse(user.name).isDefined
  }

  override def updatePassword(uid: String, clear: String) {
    userAuthDao.optionalByUser(uid) match {
      case Some(auth) => {
        userAuthDao.update(new User.Auth.Update(auth.id, clear, None, None))
      }

      case None => {
        userAuthDao.create(new User.Auth.Free(uid, clear))
      }
    }
  }

  override def findOrCreateByEmail(email: String): User.Persisted = {
    userDao.optionalByEmailForUpdate(email) match {
      case Some(user) => user
      case None => {
        val uid = create(new User.Free(None, Some(email), None, None))
        userDao.find(uid)
      }
    }
  }

  override def optionalByNameOrEmail(nameOrEmail: String) = {
    userDao.optionalByEmail(nameOrEmail).orElse(userDao.optionalByName(nameOrEmail))
  }

  override def optionalByEmail(email: String) = userDao.optionalByEmail(email)

  // User notifications
  override def create(f: UserNotification.Free) = userNotificationDao.create(f)
  override def findNotification(id: String) = userNotificationDao.find(id)
  // END User notifications
}
