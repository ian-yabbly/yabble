package me.yabble.web.template

import me.yabble.common.Log

import org.apache.commons.io.FilenameUtils
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.context.Context

import com.google.common.collect.Maps

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

  def renderToString(templates: List[String], context: Map[String, Any] = Map()) {
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
    m.put("Utils", classOf[Utils])
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
    engine.mergeTemplate("/%s".format(t), encoding, context, writer)
  }
}
