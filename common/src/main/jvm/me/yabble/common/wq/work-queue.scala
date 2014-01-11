package me.yabble.common.wq

import me.yabble.common.Log
import me.yabble.common.NotFoundException
import me.yabble.common.SecurityUtils._
import me.yabble.common.proto.CommonProtos.WorkQueueItem
import me.yabble.common.redis.RedisClient

import com.google.common.base.Function
import com.google.protobuf.ByteString

import org.joda.time.DateTime

import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException

import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit._

import scala.collection.JavaConversions._

trait WorkQueue {
  def submit(qname: String, data: Array[Byte], delayMs: Long)
  def submit(qname: String, data: Array[Byte])
  def process(qname: String, processTimeoutMs: Int, fn: (Array[Byte]) => Unit)
}

class RedisWorkQueue(private val client: RedisClient)
  extends WorkQueue
  with Log
{
  class ItemNotFoundException(val qname: String, val id: Long)
    extends NotFoundException("Work queue item [%s] [%d]".format(qname, id))

  def QUEUE_PREFIX = "wq:"

  override def submit(qname: String, data: Array[Byte], delayMs: Long) {
  }

  override def submit(qname: String, data: Array[Byte]) {
    client.work(new Function[Jedis, Long]() {
      override def apply(jedis: Jedis): Long = {
        val id = createItem(jedis, qname, data)
        addPending(jedis, qname, id)
        return id
      }
    })
  }

  override def process(qname: String, processTimeoutMs: Int, fn: (Array[Byte]) => Unit) {
    client.work(new Function[Jedis, Unit]() {
      override def apply(jedis: Jedis) {
        jedis.blpop(0, pendingName(qname)).toList match {
          case name :: idStr :: Nil => {
            val id = idStr.toLong
            try {
              val now = DateTime.now()
              val timeoutDate = now.plusMillis(processTimeoutMs)
              val item = findItem(jedis, qname, id).toBuilder()
                  .setLastInProgressDate(now.toString())
                  .setTimeoutDate(timeoutDate.toString())
                  .build()

              updateItem(jedis, qname, item)
              addInProgress(jedis, qname, id)
            } catch {
              case e: ItemNotFoundException => {
                log.error(e.getMessage, e)
                delete(qname, id)
              }
            }
          }
          case _ => error("Unexpected number of results from blpop [%s]".format(pendingName(qname)))
        }
      }
    })
  }

  private def delete(qname: String, id: Long) {
    client.work(new Function[Jedis, Unit]() {
      override def apply(jedis: Jedis) {
        jedis.lrem(u(inProgressName(qname)), 0, u(id))
        jedis.lrem(u(failName(qname)), 0, u(id))
        jedis.lrem(u(pendingName(qname)), 0, u(id))
        deleteItem(jedis, qname, id)
      }
    })
  }

  private def deleteItem(jedis: Jedis, qname: String, id: Long): Boolean = {
    jedis.hdel(u(itemsName(qname)), u(id)) == 1
  }

  private def createItem(jedis: Jedis, qname: String, value: Array[Byte]): Long = {
    val id = jedis.incr(seqName(qname))

    val itemBytes = WorkQueueItem.newBuilder()
        .setId(id)
        .setData(ByteString.copyFrom(value))
        .setCreationDate(DateTime.now().toString())
        .setAttemptCount(0l)
        .build()
        .toByteArray()

    jedis.hset(u(itemsName(qname)), u(id), itemBytes)

    return id
  }

  private def updateItem(jedis: Jedis, qname: String, item: WorkQueueItem) {
    jedis.hset(u(itemsName(qname)), u(item.getId()), item.toByteArray())
  }

  private def findItem(jedis: Jedis, qname: String, id: Long): WorkQueueItem = {
    jedis.hget(u(itemsName(qname)), u(id)) match {
      case null => throw new ItemNotFoundException(qname, id)
      case itemBytes => WorkQueueItem.parseFrom(itemBytes)
    }
  }

  private def addPending(jedis: Jedis, qname: String, id: Long) {
    jedis.rpush(u(pendingName(qname)), u(id))
  }

  private def addInProgress(jedis: Jedis, qname: String, id: Long) {
    jedis.rpush(u(inProgressName(qname)), u(id))
  }

  private def u(v: Long): Array[Byte] = u(v.toString)
  private def u(v: String): Array[Byte] = utf8Encode(v)

  private def inProgressName(qname: String) = "%s%s.in-progress".format(QUEUE_PREFIX, qname)
  private def seqName(qname: String) = "%s%s.seq".format(QUEUE_PREFIX, qname)
  private def itemsName(qname: String) = "%s%s.items".format(QUEUE_PREFIX, qname)
  private def pendingName(qname: String) = "%s%s.pending".format(QUEUE_PREFIX, qname)
  private def failName(qname: String) = "%s%s.fail".format(QUEUE_PREFIX, qname)
}

package com.yabbly.common.queue;

import com.yabbly.common.ctx.ExecutionContext;
import com.yabbly.common.ctx.ExecutionContextUtils;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.Lifecycle;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

abstract class AbstractQueueWorker(
    private val txnTemplate: TransactionTemplate,
    private val workQueue: WorkQueue,
    private val qname: String,
    private val maxRetries: Int)
  extends Lifecycle
  with Runnable
  with Log
{
  private def MAX_BACKOFF_SLEEP_TIME = 300l

  private val executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
        override def newThread(r: Runnable): Thread = {
          val t = new Thread(r, qname + "-worker")
          t.setDaemon(true)
          return t
        }
      })

  private var isRunning = false

  override def start() {
    executorService.submit(this)
    isRunning = true
  }

  override def stop() {
    executorService.shutdown()
    isRunning = false
  }

  override def isRunning() = isRunning

  override def run() {
    var backoffSleepTime = 1l

    while (true) {
      log.debug("Starting loop")

      try {
        workQueue.process(qname, MINUTES.toMillis(5), (bytes: Array[Byte]) => {
        }
            final WorkQueue.Item item = oitem.get();
            log.debug("Work item retrieved [{}]", item.getValue());

            txnTemplate.execute(new TransactionCallback<Void>() {
                public Void doInTransaction(TransactionStatus status) {
                    boolean isSuccess = false;
                    String errorMessage = null;
                    try {
                        handleWorkItem(item, status);
                        isSuccess = true;
                    } catch (FailException e) {
                        Throwable cause = e;
                        if (e.getCause() != null) {
                            cause = e.getCause();
                        }
                        status.setRollbackOnly();
                        errorMessage = String.format("%s: %s",
                                cause.getClass().getSimpleName(), cause.getMessage());
                    } catch (Throwable e) {
                        status.setRollbackOnly();
                        errorMessage = String.format("%s: %s",
                                e.getClass().getSimpleName(), e.getMessage());
                        log.error(e.getMessage(), e);
                    } finally {
                        try {
                            if (item != null) {
                                if (isSuccess) {
                                    workQueue.commit(qname, item.getId());
                                } else {
                                    if (item.getAttemptCount() >= maxRetries) {
                                        log.warn("Work queue item has failed [{}]", item.getId());
                                        handleFailure(item, errorMessage);
                                        workQueue.fail(qname, item.getId(), errorMessage);
                                    } else {
                                        log.info("Rolling back item [{}]", item.getId());
                                        workQueue.rollback(qname, item.getId(), errorMessage);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Unexpected exception [{}]", e.getMessage(), e);
                        }
                    }

                    return null;
                }
            });

        backoffSleepTime = 1l;
      } catch (Exception e) {
          try {
              log.warn(e.getMessage(), e);
              long t = Math.min(backoffSleepTime, MAX_BACKOFF_SLEEP_TIME);
              log.info("Sleeping for [{}] seconds", t);
              Thread.sleep(SECONDS.toMillis(t));
          } catch (InterruptedException e2) {
              log.warn(e2.getMessage(), e2);
          }
          backoffSleepTime *= 2;
      }
    }
  }

    protected WorkQueue getWorkQueue() { return workQueue; }

    protected abstract void handleWorkItem(WorkQueue.Item item, TransactionStatus status)
        throws Exception;

    protected abstract void handleFailure(WorkQueue.Item item, String errorMessage)
        throws Exception;
}
