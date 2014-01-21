package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait UserService extends IService[User.Free, User.Persisted, User.Update] {
  def canLogin(uid: String): Boolean
  def updatePassword(uid: String, clear: String)
  def findOrCreateByEmail(email: String): User.Persisted
  def optionalByNameOrEmail(nameOrEmail: String): Option[User.Persisted]
  def optionalByEmail(email: String): Option[User.Persisted]
  def optionalByName(name: String): Option[User.Persisted]
  //def isNameValid(name: String): Boolean

  def isPasswordValid(password: String): Boolean = {
    password.length >= 4
  }

  // User notifications
  def create(f: UserNotification.Free): String
  def findNotification(id: String): UserNotification.Persisted
  // END User notifications

  // User Attributes
  def create(f: Attribute.Free): String
  def update(u: Attribute.Update): Int
  def deactivateAttribute(id: String): Boolean
  def activateAttribute(id: String): Boolean
  // END User Attributes
}

class UserServiceImpl(
    private val userDao: UserDao,
    private val userAuthDao: UserAuthDao,
    private val userNotificationDao: UserNotificationDao,
    private val userAttributeDao: UserAttributeDao)
  extends Service(userDao)
  with UserService
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

  override def optionalByName(name: String) = userDao.optionalByName(name)

  // User notifications
  override def create(f: UserNotification.Free) = userNotificationDao.create(f)
  override def findNotification(id: String) = userNotificationDao.find(id)
  // END User notifications

  // User Attributes
  def create(f: Attribute.Free) = userAttributeDao.create(f)
  def update(u: Attribute.Update) = userAttributeDao.update(u)
  def deactivateAttribute(id: String) = userAttributeDao.deactivate(id)
  def activateAttribute(id: String) = userAttributeDao.activate(id)
  // END User Attributes
}
