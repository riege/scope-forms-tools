/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

final case class PDFRawData(
  context: DocumentContext,
  encryptPDF: Boolean,
  pdfa: Boolean,
  formName: String,
  dataSourceParameterName: String,
  backgroundImage: Option[String] = None,
  data: Map[String, Any]
)
