package me.yabble.service

import me.yabble.service.model._
import me.yabble.service.dao._

class UserService(userDao: UserDao) {
  def find(id: String): User.Persisted = userDao.find(id)
}
