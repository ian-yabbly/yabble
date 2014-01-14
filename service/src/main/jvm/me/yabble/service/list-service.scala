package me.yabble.service

import me.yabble.common.Log
import me.yabble.service.model._
import me.yabble.service.dao._

trait IYListService extends IService[YList.Free, YList.Persisted, YList.Update]

class YListService(private val ylistDao: YListDao) extends Service(ylistDao) with Log
