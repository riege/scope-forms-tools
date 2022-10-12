/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.backend

object BackendException {

  def apply(throwable: Throwable): BackendException = {
    val exceptionClass = throwable.getClass.getName
    val message = toMessage(throwable)
    val stackTrace = throwable.getStackTrace
    val cause =
      if (throwable.getCause != null) {
        apply(throwable.getCause)
      } else{
        null
      }
    new BackendException(message, cause, exceptionClass, stackTrace)
  }

  private[this] def toMessage(throwable: Throwable): String = {
    val brokenImageURI = "The URI \"data:image/"
    val message = throwable.getMessage
    if (message != null) {
      val i = message.indexOf(brokenImageURI)
      if (i > 0) {
        val j = message.lastIndexOf("on element <image>")
        if (j > 0) {
          message.substring(0, i + brokenImageURI.length + 3) + "... \"" + message.substring(j)
        } else {
          message.substring(0, Math.min(message.length, 128))
        }
      } else {
        message
      }
    } else {
      null
    }
  }

}

final class BackendException(message: String, cause: Throwable,
  val exceptionClass: String, stackTrace: Array[StackTraceElement]
) extends Exception(message, cause, false, true) {

  setStackTrace(stackTrace)

  override def toString: String = {
    val s = exceptionClass
    val message = getMessage
    if (message != null) {
      s + ": " + message
    } else {
      s
    }
  }

  override def fillInStackTrace(): Throwable = this

}
