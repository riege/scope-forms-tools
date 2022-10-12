/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

final case class LabelDocument(
  content: Array[Byte],
  labelFile: Option[String] = None
)
