/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice

import java.util.{Locale, UUID}

import com.riege.akka.util.Strings

import better.files.File

object FileUtils {

  @inline
  def sanitizeFilename(name: String): String = {
    name.replaceAll("[^a-zA-Z0-9\\.\\-]", "_").replace("..", "_")
  }

  @inline
  def toFileNames(
    basename: String,
    ext: String,
    language: Option[Locale]
  ): Seq[String] = {
    language
      .map(locale => {
        val l = locale.getLanguage
        val c = locale.getCountry
        if (Strings.isNotBlank(c)) {
          Seq(basename + '_' + l + '_' + c + ext, basename + '_' + l + ext, basename + ext)
        } else {
          Seq(basename + '_' + l + ext, basename + ext)
        }
      })
      .getOrElse(Seq(basename + ext))
  }

  @inline
  def getFileName(fileName: String): String = {
    val i = fileName.lastIndexOf('.')
    if (i != -1) {
      fileName.substring(0, i)
    } else {
      fileName
    }
  }

  @inline
  def getExt(fileName: String): String = {
    val i = fileName.lastIndexOf('.')
    if (i != -1) {
      fileName.substring(i)
    } else {
      ""
    }
  }

  @inline
  def newTmpFileName: String = UUID.randomUUID().toString

  def cleanUp(fileStore: String): Unit = {
    File(fileStore)
      .globRegex("""[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r)
      .foreach(_.delete(swallowIOExceptions = true))
  }

}
