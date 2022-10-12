/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.backend

final class PrintException(message: String, cause: Throwable)
  extends RuntimeException(message, cause) {

  /**
   * @param message  describes why the exception occurred.
   */
  def this(message: String) {
    this(message, null)
  }

}
