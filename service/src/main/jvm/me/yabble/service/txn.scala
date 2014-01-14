package me.yabble.service

import me.yabble.common.Log

import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation

import org.springframework.transaction._
import org.springframework.transaction.support._

class TxnMethodInterceptor(private val txnManager: PlatformTransactionManager)
  extends MethodInterceptor
  with Log
{
  override def invoke(invocation: MethodInvocation): AnyRef = {
    val txnDef = new DefaultTransactionDefinition()
    txnDef.setName("yabble-txn")
    txnDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED)

    val status = txnManager.getTransaction(txnDef)
    val ret = try {
      invocation.proceed()
    } catch {
      case e: Exception => {
        txnManager.rollback(status)
        throw e
      }
    }
    txnManager.commit(status)
    ret
  }
}
