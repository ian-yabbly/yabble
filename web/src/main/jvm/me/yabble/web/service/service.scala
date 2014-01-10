package me.yabble.web.service

import me.yabble.common.NotFoundException

class SessionNotFoundException(id: String) extends NotFoundException(id)
