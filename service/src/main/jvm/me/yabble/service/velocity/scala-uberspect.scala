package me.yabble.service.velocity

import org.apache.velocity.util.introspection._

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.collection.JavaConversions._

class ScalaUberspect extends UberspectImpl {
  def logger = LoggerFactory.getLogger("me.yabble.web.template.ScalaUberspect")

  override def getIterator(obj: Object, info: Info): java.util.Iterator[_] = obj match {
    case l: scala.collection.Iterable[_] => asJavaIterable(l).iterator()
    case _ => super.getIterator(obj, info)
  }

/*
  override def getPropertyGet(obj: Object, identifier: String, info: Info): VelPropertyGet = {
    logger.info("object [{}]", obj)
    logger.info("identifier [{}]", identifier)
    logger.info("info [{}]", info)
    obj.
    super.getPropertyGet(obj, identifier, info)
  }
*/
}
