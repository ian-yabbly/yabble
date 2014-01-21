package me.yabble.service.daemon

import me.yabble.common.Log
import me.yabble.common.wq.WorkQueue
import me.yabble.service._
import me.yabble.service.mail.UserListNotificationPushMailer
import me.yabble.service.model._

import org.springframework.context.Lifecycle
import org.springframework.transaction._
import org.springframework.transaction.support._

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class UserListNotificationScheduledPushDaemon(
    private val scheduleInitialDelay: Long,
    private val schedulePeriod: Long,
    private val scheduleTimeUnit: TimeUnit,
    private val userService: UserService,
    private val txnTemplate: TransactionTemplate,
    private val workQueue: WorkQueue,
    private val pushMailer: UserListNotificationPushMailer)
    
  extends Runnable
  with Lifecycle
  with Log
{
  var _isRunning = false

  val executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        override def newThread(r: Runnable): Thread = {
          val t = new Thread(r, "user-list-notification-schedule-push-daemon")
          t.setDaemon(true)
          t
        }
      })


  override def start() {
    executorService.scheduleAtFixedRate(this, scheduleInitialDelay, schedulePeriod, scheduleTimeUnit)
    _isRunning = true
  }
        
  override def stop() {
    executorService.shutdown()
    _isRunning = false
  }

  override def isRunning(): Boolean =  _isRunning

  override def run() {
    try {
      log.info("Running")
      // Find all non completed push schedules in the past
      userService.allUserListNotificationPushSchedulesForProcessing.foreach(s => {
        txnTemplate.execute(new TransactionCallback[Unit]() {
          override def doInTransaction(status: TransactionStatus) {
            log.info("Schedule [{}]", s)
            s.userNotifications.foreach(n => log.info("Notification [{}]", n))
            pushMailer.push(s.userId, s.listId, s.userNotifications)
            userService.update(new UserListNotificationPushSchedule.Update(s.id, true, s.pushDate))
          }
        })
      })
    } catch {
      case e: Exception => log.error(e.getMessage, e)
    }
  }
}
