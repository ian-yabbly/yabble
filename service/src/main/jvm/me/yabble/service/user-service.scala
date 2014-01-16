package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IUserService extends IService[User.Free, User.Persisted, User.Update] {
  def create(f: UserNotification.Free): String
  def findOrCreateByEmail(email: String): User.Persisted
  def optionalByNameOrEmail(nameOrEmail: String): Option[User.Persisted]
  def optionalByEmail(email: String): Option[User.Persisted]
}

class UserService(
    private val userDao: UserDao,
    private val userNotificationDao: UserNotificationDao)
  extends Service(userDao)
  with IUserService
  with Log
{
  override def create(f: UserNotification.Free) = userNotificationDao.create(f)

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
}
