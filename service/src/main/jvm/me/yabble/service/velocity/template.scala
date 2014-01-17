package me.yabble.service.velocity

import me.yabble.common.Log

import org.apache.commons.collections.ExtendedProperties
import org.apache.commons.io.FilenameUtils
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.context.Context
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.apache.velocity.util.introspection._

import com.google.common.collect.Maps

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.io.InputStream
import java.io.StringWriter
import java.io.Writer
import java.util.{Map => JMap}
import java.util.Properties

import scala.collection.JavaConversions._

class VelocityTemplate(
    private val encoding: String,
    velocityConfig: Properties,
    private val rootContext: JMap[String, Any])
  extends Log
{
  val engine = new VelocityEngine(velocityConfig)
  engine.init()

  def renderToString(templates: List[String], context: Map[String, Any] = Map()): String = {
    val writer = new StringWriter()
    try {
      render(templates, writer, context)
      writer.toString()
    } finally {
      if (null != writer) { writer.close() }
    }
  }

  /**
   * templates should be given in order of desired evaluation.
   */
  def render(templates: List[String], out: Writer, context: Map[String, Any] = Map()) {
    val m: JMap[String, Any] = Maps.newHashMap()
    rootContext.foreach(t => m.put(t._1, t._2))
    context.foreach(t => m.put(t._1, t._2))
    val vctx = new VelocityContext(m)

    templates match {
      case Nil => // Do nothing

      case t1 :: Nil => renderToWriter(t1, vctx, out)

      case t1 :: t2 :: Nil => {
        val content = renderHead(t1, vctx)
        renderTail(t2, content, vctx, out)
      }

      case ts => {
        var content = renderHead(ts.head, vctx)
        ts.tail.init.foreach(t => {
          content = renderMiddle(t, content, vctx)
        })
        renderTail(ts.last, content, vctx, out)
      }
    }
  }

  private def renderHead(t: String, context: Context): String = renderToString(t, context)

  private def renderMiddle(t: String, content: String, context: Context): String = {
    context.put("__content", content)
    renderToString(t, context)
  }

  private def renderTail(t: String, content: String, context: Context, out: Writer) {
    context.put("__content", content)
    renderToWriter(t, context, out)
  }

  private def renderToString(t: String, context: Context): String = {
    val writer = new StringWriter()
    try {
      renderToWriter(t, context, writer)
      writer.toString()
    } finally {
      if (null != writer) { writer.close() }
    }
  }

  private def renderToWriter(t: String, context: Context, writer: Writer) {
    val path = if (t.startsWith("/")) t else "/"+t
    engine.mergeTemplate(path, encoding, context, writer)
  }
}

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

class BasePathClasspathResourceLoader(private var basePath: String) extends ClasspathResourceLoader {
  def logger = LoggerFactory.getLogger("me.yabble.web.template.BasePathClasspathResourceLoader")

  def this() = this(null)

  override def getResourceStream(name: String): InputStream = {
    val p = if (name.startsWith("/")) basePath+name else basePath+"/"+name
    //logger.info("Getting resource stream for [{}] [{}]", name, p)
    super.getResourceStream(p)
  }

  override def init(eprops: ExtendedProperties) {
    basePath = eprops.getString("base-path")
    super.init(eprops)
  }
}
