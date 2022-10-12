/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.akka.util

import java.util.concurrent.ConcurrentHashMap

import scala.reflect.runtime.universe._

trait Configuration {

  def configure[R <: AnyRef](f: => R)(implicit tag: TypeTag[R]): R = {
    val a = f
    ConfigurationStore.put(tag, a)
    a
  }

  def clearConfiguration(): Unit = ConfigurationStore.clear()

}

trait Configured {

  def configured[A: TypeTag]: A =
    ConfigurationStore.get[A]
      .getOrElse(throw new IllegalArgumentException("Unable to find: " + typeTag[A].tpe))

  def configured[A <: AnyRef](key: String, f: String => A) : A =
    ConfigurationStore.putIfAbsent(key, f)

}

private[util] object ConfigurationStore {

  private[this] val entries: ConcurrentHashMap[Any, AnyRef] = new ConcurrentHashMap[Any, AnyRef]()

  def put(key: Any, value: AnyRef): Unit = {
    val v = entries.putIfAbsent(key, value)
    if (v != null) {
      throw new IllegalArgumentException(s"There is already a mapping for $key: $v")
    }
  }

  def putIfAbsent[A <: AnyRef](key: String, f: String => A): A = {
    val v = entries.computeIfAbsent(key, aKey => f.apply(aKey.asInstanceOf[String]))
    v.asInstanceOf[A]
  }

  def get[A](implicit tag: TypeTag[A]): Option[A] = {
    Option(entries.get(tag))
      .map(v => v.asInstanceOf[A])
  }

  def clear(): Unit = entries.clear()

}
