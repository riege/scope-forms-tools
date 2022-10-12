/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.akka.util

object Strings {

  /**
   * Checks and answers if the given String is whitespace, empty (<code>""</code>)
   * or <code>null</code>. The definition of whitespace is used as per
   * [[Character.isWhitespace(char)]].
   * <p>
   * Examples:
   * <ul>
   * <li><code>#isBlank(null)            == true</code></li>
   * <li><code>#isBlank(&quot;&quot;)    == true</code></li>
   * <li><code>#isBlank(&quot; &quot;)   == true</code></li>
   * <li><code>#isBlank(&quot;Hi &quot;) == false</code></li>
   * </ul>
   *
   * @param str  the String to check, may be <code>null</code>
   *
   * @return <code>true</code> if the String is whitespace, empty or
   *         <code>null</code>
   *
   * @see Character#isWhitespace(char)
   */
  @inline
  def isBlank(str: String): Boolean =
    str == null || str.forall(Character.isWhitespace)

  @inline
  def isNotBlank(str: String): Boolean = !isBlank(str)

}
