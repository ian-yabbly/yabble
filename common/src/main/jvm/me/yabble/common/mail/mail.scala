package me.yabble.common.mail

class InvalidEmailAddressException(val email: String) extends RuntimeException(email)
