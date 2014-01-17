package me.yabble.web.template

import org.springframework.context.MessageSource

import java.util.Locale

class TemplateMessageSource(messageSource: MessageSource) {
  private def locale = Locale.US

  def getMessage(code: String): String = getMessage(code, code)

  def getMessage(code: String, fallback: String): String = {
    messageSource.getMessage(code, null, code, locale)
  }
}
