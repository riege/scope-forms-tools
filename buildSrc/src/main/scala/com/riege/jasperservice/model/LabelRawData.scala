/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

final case class LabelRawData(
  context: DocumentContext,
  templateName: String,
  data: Map[String, Any],
  labelNumbers: Option[List[Int]] = None
)
