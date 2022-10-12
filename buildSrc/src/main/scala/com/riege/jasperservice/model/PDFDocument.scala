/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

final case class PDFDocument(
  contentFile: String,
  contentWithBackgroundFile: Option[String] = None,
  formFile: Option[String] = None,
  sources: Option[Map[String, Any]] = None
)
