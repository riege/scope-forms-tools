/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

final case class UploadedImage(fileName: String, svg: Boolean, data: Array[Byte]) {

  override def hashCode(): Int = super.hashCode()

  override def equals(obj: scala.Any): Boolean = {
    obj match {
      case other: UploadedImage =>
        fileName.equals(other.fileName) &&
          svg == other.svg &&
          java.util.Arrays.equals(data, other.data)
      case _ =>
        false
    }
  }

}
