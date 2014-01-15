package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IUserService extends IService[User.Free, User.Persisted, User.Update] {
  def findOrCreateByEmail(email: String): User.Persisted
}

class UserService(private val userDao: UserDao)
  extends Service(userDao)
  with IUserService
  with Log
{
  override def findOrCreateByEmail(email: String): User.Persisted = {
    userDao.optionalByEmailForUpdate(email) match {
      case Some(user) => user
      case None => {
        val uid = create(new User.Free(None, Some(email), None, None))
        userDao.find(uid)
      }
    }
  }
}
