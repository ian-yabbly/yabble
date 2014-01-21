package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

import org.joda.time.DateMidnight
import org.joda.time.DateTime

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

  // User list notification preferences
  def optionalUserListNotificationPreferenceByUserAndList(uid: String, lid: String): Option[UserListNotificationPreference.Persisted]
  // END User list notification preferences

  // User Attributes
  def create(f: Attribute.Free): String
  def update(u: Attribute.Update): Int
  def deactivateAttribute(id: String): Boolean
  def activateAttribute(id: String): Boolean
  // END User Attributes

  // User list notification push schedules
  def update(u: UserListNotificationPushSchedule.Update): Int
  def scheduleUserListNotificationPush(unid: String, uid: String, lid: String, maxPushesPerDay: Int)
  def allUserListNotificationPushSchedulesForProcessing(): List[UserListNotificationPushSchedule.Persisted]
  def addUserNotificationToUserListNotificationPushSchedule(ulnpsid: String, unid: String)
  // END User list notification push schedules
}

class UserServiceImpl(
    private val userDao: UserDao,
    private val userAuthDao: UserAuthDao,
    private val userNotificationDao: UserNotificationDao,
    private val userNotificationPushDao: UserNotificationPushDao,
    private val userListNotificationPushScheduleDao: UserListNotificationPushScheduleDao,
    private val userListNotificationPreferenceDao: UserListNotificationPreferenceDao,
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

  // User list notification preferences
  override def optionalUserListNotificationPreferenceByUserAndList(uid: String, lid: String): Option[UserListNotificationPreference.Persisted] = {
    userListNotificationPreferenceDao.optionalByUserAndList(uid, lid)
  }
  // END User list notification preferences

  // User Attributes
  override def create(f: Attribute.Free) = userAttributeDao.create(f)
  override def update(u: Attribute.Update) = userAttributeDao.update(u)
  override def deactivateAttribute(id: String) = userAttributeDao.deactivate(id)
  override def activateAttribute(id: String) = userAttributeDao.activate(id)
  // END User Attributes

  // User list notification push schedules
  override def update(u: UserListNotificationPushSchedule.Update) = userListNotificationPushScheduleDao.update(u)

  override def scheduleUserListNotificationPush(unid: String, uid: String, lid: String, maxPushesPerDay: Int) {
    val ulnpsid = userListNotificationPushScheduleDao.optionalByUserAndListAndCompleted(uid, lid, false) match {
      case Some(s) => s.id

      case None => {
        val now = DateTime.now().plusMinutes(1) // Fudge a little here to avoid race condition
        val secondsInWindow = 16*60*60;
        val secondsPerFrame = secondsInWindow/maxPushesPerDay

        var pushDate = DateMidnight.now().toDateTime().plusHours(5)
        while (pushDate.isBefore(now)) {
          pushDate = pushDate.plusSeconds(secondsInWindow)
        }

        userListNotificationPushScheduleDao.create(new UserListNotificationPushSchedule.Free(uid, lid, pushDate))
      }
    }

    userListNotificationPushScheduleDao.addNotification(ulnpsid, unid)
  }

  override def allUserListNotificationPushSchedulesForProcessing(): List[UserListNotificationPushSchedule.Persisted] =
      userListNotificationPushScheduleDao.allForProcessing()

  override def addUserNotificationToUserListNotificationPushSchedule(ulnpsid: String, unid: String) {
    userListNotificationPushScheduleDao.addNotification(ulnpsid, unid)
  }
  // END User list notification push schedules
}
