package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IYListService extends IService[YList.Free, YList.Persisted, YList.Update] {
  def create(f: YList.Item.Free): String
}

class YListService(
    private val ylistDao: YListDao,
    private val ylistItemDao: YListItemDao)
  extends Service(ylistDao)
  with IYListService
  with Log
{
  override def create(f: YList.Item.Free) = ylistItemDao.create(f)
}
