/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

final case class TextRawData(
  context: DocumentContext,
  formName: String,
  dataSourceParameterName: String,
  data: Map[String, Any],
  lineSeparator: Option[String],
  pageSeparator: Option[String],
  pageHeight: Option[Int] = None,
  pageWidth: Option[Int] = None,
  pageWidthInChars: Option[Int] = None,
  pageHeightInChars: Option[Int] = None,
  charHeight: Option[Float] = Some(12f)
)
