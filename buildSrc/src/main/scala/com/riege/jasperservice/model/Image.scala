/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.model

import java.util.Locale

final case class Image(
  name: String,
  ext: Option[String] = None,
  locale: Option[Locale] = None
)
