package me.yabble.service

import me.yabble.service.model._
import me.yabble.service.dao._

class YListService(ylistDao: YListDao) {
  def create(f: YList.Free): String = ylistDao.create(f)
  def find(id: String): YList.Persisted = ylistDao.find(id)
}
