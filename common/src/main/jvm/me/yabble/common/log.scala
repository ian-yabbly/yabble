package me.yabble.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

trait Log {
  lazy val log = LoggerFactory.getLogger(getClass)
  lazy val consoleLog = LoggerFactory.getLogger("CONSOLE")
}
