package me.yabble.common

import com.google.common.base.Optional

object Predef {


  implicit def optional2Option[T](o: Optional[T]): Option[T] = if (o.isPresent) {
        Some(o.get)
      } else {
        None
      }

  def o2o[T](o: Optional[T]): Option[T] = optional2Option(o)

  implicit def option2Optional[T](o: Option[T]): Optional[T] = o match {
        case Some(v) => Optional.of(v)
        case None => Optional.absent()
      }

  def o2o[T](o: Option[T]) = option2Optional(o)
}
