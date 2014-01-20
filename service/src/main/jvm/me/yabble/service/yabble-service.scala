package com.yabble.service

import me.yabble.common.Log
import me.yabble.service._
import me.yabble.service.model._
import me.yabble.service.dao._

trait YabbleService {
  def mergeUsers(srcUid: String, destUid: String)
}

class YabbleServiceImpl(
    private val userService: UserService,
    private val ylistSerivce: YListService)
  extends YabbleService
  with Log
{
  override def mergeUsers(srcUid: String, destUid: String) {
  }
}
