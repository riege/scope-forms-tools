/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.backend

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.util
import java.util.Locale

import scala.collection.mutable

import com.riege.akka.util.Strings.{isBlank, isNotBlank}
import com.riege.jasperservice.FileUtils.{getExt, getFileName, sanitizeFilename, toFileNames}
import com.riege.jasperservice.model.{DocumentContext, Image, PageDimension, Report, UploadedImage}

import net.sf.jasperreports.engine.`type`.OnErrorTypeEnum
import net.sf.jasperreports.engine.{JRRenderable, JasperReport, RenderableUtil}
import net.sf.jasperreports.renderers.BatikRenderer

object FormsLoader {

  /**
   * The default image extension used. Defined as <em>'.png'</em>.
   */
  val DEFAULT_IMAGE_EXT = ".png"

  val SVG_IMAGE_EXT = ".svg"

  /**
   * String representation of the property form directory.
   * Contains the full path to the directory where forms are stored.
   */
  val PROPERTY_FORM_DIR = "formDir"

  val PROPERTY_FORM_FILE = "formFile"

  /**
   * The file extension for the forms.
   */
  val FORM_EXT = ".jasper"

  val DOT = "."

}

final case class JasperData(
  data: util.Map[String, Object],
  sources: Option[Map[String, Any]] = None
) {

  def mergeDataAndSources: util.Map[String, Object] = {
    sources
      .map(m => {
        val javaMap: util.Map[String, Object] =
          new util.HashMap[String, Object](m.size + data.size())
        javaMap.putAll(data)
        putAll(javaMap, m)
        javaMap
      })
      .getOrElse(data)
  }

  private def putAll(javaMap: util.Map[String, Object], m: Map[String, Any]): Unit = {
    m.foreach(e => {
      val k = e._1
      val v = e._2
      if (v.isInstanceOf[Map[_, _]]) {
        val newMap: util.Map[String, Object] = new util.HashMap[String, Object]()
        putAll(newMap, v.asInstanceOf[Map[String, Any]])
        javaMap.put(k, newMap)
      } else {
        javaMap.put(k, v.asInstanceOf[Object])
      }
    })
  }

}

final case class LabelTemplate(templateFile: File, template: String)

final class FormsLoader(
  val ctx: DocumentContext,
  val formsStore: String,
  val dryrun: Boolean = false
) {

  import FormsLoader._

  def toJasperData(map: Map[String, Any]): JasperData = {
    import scala.collection.JavaConverters._
    val production = ctx.production
    val sources =
      if (production && !dryrun) {
        None
      } else {
        Some(new mutable.HashMap[String, Any]())
      }
    val javaMap = new util.HashMap[String, Object](map.size)

    for ((k, v) <- map) {
      v match {
        case list: List[Map[String, _]] =>
          val newList = list.map(m => toJasperData(m))
          javaMap.put(k, newList.map(_.data).asJava)
          if (!production) {
            sources.get.put(k, newList.map(_.sources.get))
          }
        case map: Map[String, _] =>
          val newMap = toJasperData(map)
          javaMap.put(k, newMap.data)
          if (!production) {
            sources.get.put(k, newMap.sources.get)
          }
        case img: Image =>
          if (dryrun) {
            val file = getFile(img.name + img.ext.getOrElse(SVG_IMAGE_EXT), img.locale)
              .orElse(getFile(img.name + DEFAULT_IMAGE_EXT, img.locale))
            sources.get.put(k, file.map(_.getAbsolutePath).getOrElse("image not found"))
          } else {
            val image = getImage(img)
            javaMap.put(k, image._2)
            if (!production) {
              sources.get.put(k, image._1)
            }
          }
        case uploaded: UploadedImage =>
          if (dryrun) {
            sources.get.put(k, "Uploaded file: " + uploaded.fileName)
          } else {
            val image =
              if (uploaded.svg) {
                BatikRenderer.getInstance(uploaded.data)
              } else {
                RenderableUtil.getInstance(null).getRenderable(uploaded.data)
              }
            javaMap.put(k, image)
            if (!production) {
              sources.get.put(k, "Uploaded file: " + uploaded.fileName)
            }
          }
        case r: Report =>
          if (dryrun) {
            val file = getFile(r.name + FORM_EXT, r.language)
            sources.get.put(k, file.map(_.getAbsolutePath).getOrElse("form not found"))
          } else {
            val form = getForm(r)
            javaMap.put(k, form)
            if (!production) {
              sources.get.put(k, form.getProperty(FormsLoader.PROPERTY_FORM_FILE))
            }
          }
        case i: Int =>
          javaMap.put(k, int2Integer(i))
        case i: BigInt =>
          javaMap.put(k, i.bigInteger)
        case d: BigDecimal =>
          javaMap.put(k, d.bigDecimal)
        case _ =>
          javaMap.put(k, v.asInstanceOf[Object])
      }
    }

    if (production) {
      JasperData(javaMap)
    } else {
      JasperData(javaMap, sources.map(_.toMap))
    }
  }

  private[this] val imageCache = new mutable.HashMap[Image, (String, JRRenderable)]()

  private def getImage(img: Image): (String, JRRenderable) = {
    imageCache.getOrElseUpdate(img, getImage(img.name, img.ext, img.locale))
  }

  private[this] val reportCache = new mutable.HashMap[Report, JasperReport]()

  private def getForm(r: Report): JasperReport = {
    reportCache.getOrElseUpdate(r, getForm(r.name, r.language))
  }

  /**
   * Retrieves the requested form in the given language. If the language is
   * [[None]], a default form will be returned, if defined.
   *
   * @param formName  the name of the form to retrieve.
   * @param language  the language of the form to retrieve, may be [[None]]
   *                  to specify a default form.
   *
   * @return a JasperReport form object that can be filled with data by the
   *         application.
   *
   * @throws PrintException when an exception occurred retrieving
   *                        the associated form. The `PrintException` will
   *                        contain the original exception encountered if applicable.
   *
   * @throws IllegalArgumentException when the name of the form is empty or `null`.
   */
  @throws[PrintException]
  def getForm(formName: String, language: Option[Locale] = None): JasperReport = {
    require(isNotBlank(formName), "The form name must not be blank.")
    val name = sanitizeFilename(formName)
    val formNames: Seq[String] = toFileNames(name, FORM_EXT, language)
    val baseDir: String = getBaseDir
    val subDirs: Array[String] = getSubDirs
    try {
      searchForm(formNames, baseDir, subDirs)
        .getOrElse(
          throw new PrintException(
            s"Form '$formName' could not be found. Basedir: $baseDir, $ctx"
            + s", Language: $language")
        )
    } catch {
      case e: IOException =>
        throw new PrintException("Error accessing the form.", e)
      case e: ClassNotFoundException =>
        throw new PrintException("Error instantiating the form: " + e
          .getMessage, e)
    }
  }

  @throws[PrintException]
  def getPageDimension(formName: String, language: Option[Locale] = None): PageDimension = {
    val jp = getForm(formName, language)
    PageDimension(jp.getPageWidth, jp.getPageHeight)
  }

  @throws[IOException]
  @throws[ClassNotFoundException]
  private def searchForm(
    formNames: Seq[String],
    baseDir: String,
    subDirs: Array[String]
  ): Option[JasperReport] = {
    searchFile(formNames, baseDir, subDirs)
      .map(file => {
        val form = loadForm(file)
        form.setProperty(PROPERTY_FORM_DIR, file.getParentFile.toURI.toString)
        form.setProperty(PROPERTY_FORM_FILE, file.getAbsolutePath)
        form
      })
  }

  @throws[IOException]
  @throws[ClassNotFoundException]
  private[this] def loadForm(file: File): JasperReport = {
    try {
      better.files.File(file.toPath).readDeserialized[JasperReport](
        classLoaderOverride = None,
        bufferSize = 16 * 1024
      )
    } catch {
      case e: IOException =>
        throw new IOException(s"Failed to load form '$file'", e)
    }
  }

  /**
   * Retrieves the requested image.
   * <p>
   * The extension for e.g. <em>'background.jpg'</em> may be given as
   * <em>'jpg'</em> or <em>'.jpg'</em>. If the given extension is blank
   * the [[DEFAULT_IMAGE_EXT]] is used.
   *
   * @param imageName  the name of the image to retrieve.
   * @param ext        the file extension of the image to retrieve.
   * @param language   the language for a localized image file.
   *
   * @return the image found for the given parameters.
   *
   * @throws PrintException when an exception occurred retrieving the image.
   * @throws IllegalArgumentException when the name of the image is blank.
   */
  @throws[PrintException]
  def getImage(imageName: String, ext: Option[String], language: Option[Locale]): (String, JRRenderable) = {
    require(isNotBlank(imageName), "The image name must not be blank.")
    val image = ext match {
      case Some(e) =>
        getImage0(imageName, e, language)
      case None =>
        getImage0(imageName, SVG_IMAGE_EXT, language)
          .orElse(getImage0(imageName, DEFAULT_IMAGE_EXT, language))
    }
    if (image.isEmpty) {
      val imageExt = getImageExt(ext.getOrElse(DEFAULT_IMAGE_EXT))
      val defaultFile = imageName + imageExt
      val baseDir = getBaseDir
      throw new PrintException(
        s"Image '$defaultFile' could not be found. Basedir: $baseDir, $ctx")
    }
    image.get
  }

  @throws[PrintException]
  private[this] def getImage0(
    imageName: String,
    ext: String,
    language: Option[Locale])
  : Option[(String, JRRenderable)] = {
    require(isNotBlank(imageName), "The image name must not be blank.")
    val name = sanitizeFilename(imageName)
    val imageNames: Seq[String] = toFileNames(name, getImageExt(ext), language)
    val baseDir: String = getBaseDir
    val subDirs: Array[String] = getSubDirs
    try {
      searchImage(imageNames, baseDir, subDirs)
    } catch {
      case e: Exception =>
        throw new PrintException("Error reading file as an image.", e)
    }
  }

  /**
   * Searches for a [[File]] for the given filename, base-directory and
   * sub-directories using [[searchFile()]].
   * If a [[File]] is found it is read as an image. The image is then returned.
   *
   * @param imageNames  the list of the image file names to search for.
   * @param baseDir     the base-directory searched.
   * @param subDirs     the sub-directories searched.
   *
   * @return the image for the given values if found.
   *
   * @throws IOException if a file was found, but could not be read as an image.
   */
  @throws[Exception]
  private[this] def searchImage(
    imageNames: Seq[String],
    baseDir: String,
    subDirs: Array[String]
  ): Option[(String, JRRenderable)] = {
    searchFile(imageNames, baseDir, subDirs)
      .map(file => {
        val renderable =
          if (file.getName.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            BatikRenderer.getInstance(file)
          } else {
            RenderableUtil.getInstance(null).getRenderable(file, OnErrorTypeEnum.ERROR)
          }
        (file.getAbsolutePath, renderable)
      })
  }

  /**
   * Converts and returns the given extension to start with a dot. If the given
   * extension is blank the [[DEFAULT_IMAGE_EXT]] is returned.
   *
   * @return the extension (always starting with a dot) that is to be used in
   *         the image search for a given extension.
   */
  private[this] def getImageExt(ext: String): String = {
    if (isBlank(ext)) {
      DEFAULT_IMAGE_EXT
    } else if (ext.startsWith(DOT)) {
      ext
    } else {
      DOT + ext
    }
  }

  /**
   * Retrieves the label template with the given name.
   *
   * @param labelName  the name of the label template.
   *
   * @return the label template.
   *
   * @throws PrintException If no label template could be found, or
   *                        if the corresponding file could not be read due to
   *                        an [[IOException]].
   */
  @throws[PrintException]
  def getLabelTemplate(labelName: String): LabelTemplate = {
    require(isNotBlank(labelName), "The label name must not be blank.")
    val baseDir = getBaseDir
    val subDirs = getSubDirs
    try {
      searchLabelTemplate(labelName, baseDir, subDirs)
        .getOrElse(
          throw new PrintException(
            s"Label template '$labelName' could not be found. Basedir: $baseDir, $ctx")
        )
    } catch {
      case e: IOException =>
        throw new PrintException("Error reading label template file.", e)
    }
  }

  @throws[IOException]
  private[this] def searchLabelTemplate(
    labelName: String,
    baseDir: String,
    subDirs: Array[String]
  ): Option[LabelTemplate] = {
    searchFile(List(labelName), baseDir, subDirs)
      .filter(_.isFile)
      .map(f => {
        val bytes = Files.readAllBytes(f.toPath)
        LabelTemplate(f, new String(bytes, UTF_8))
      })
  }

  /**
   * Retrieves the requested image.
   *
   * @param imageName  the name of the image to retrieve.
   * @param language   language for a localized image file.
   * @return the image found for the given parameters.
   * @throws PrintException
   * when an exception occurred retrieving the image.
   * @throws IllegalArgumentException
   * when the name of the image is blank.
   */
  @throws[PrintException]
  def getImage(imageName: String, language: Option[Locale]): com.lowagie.text.Image = {
    require(isNotBlank(imageName), "The image name must not be blank.")
    val file: Option[File] = getFile(imageName, language)
    if (file.isEmpty) {
      throw new PrintException(
        s"Image '$imageName' could not be found. Basedir: $getBaseDir, $ctx")
    }
    try {
      com.lowagie.text.Image.getInstance(file.get.getAbsolutePath)
    } catch {
      case e: Exception =>
        throw new PrintException(s"Error reading file as an image: $file", e)
    }
  }

  /**
   * Retrieves the requested file.
   *
   * @param fileName  the name of the file to retrieve.
   * @param language  language of the file to retrieve.
   * @return the file found for the given parameters.
   * @throws IllegalArgumentException
   * when the name of the file is blank.
   */
  def getFile(fileName: String, language: Option[Locale]): Option[File] = {
    require(isNotBlank(fileName), "The file name must not be blank.")
    val name = sanitizeFilename(fileName)
    val filenames = toFileNames(getFileName(name), getExt(name), language)
    val baseDir = getBaseDir
    val subDirs = getSubDirs
    searchFile(filenames, baseDir, subDirs)
  }

  /**
   * Searches for a file using the given list of file names. The file is
   * searched in the given base-directory and all directories resulting when
   * appending the given sub-directory names one by one.
   * <p>
   * A file found in the deepest path is always prioritized above files that
   * can be found higher in the directory hierarchy.<br>
   * Also, inside each single directory, a file found with the localized name
   * is prioritized above a file with the default name. (That is if the
   * localized name is not null.)
   *
   * @param fileNames      the list of file names to search for.
   * @param baseDir        the base-directory.
   * @param subDirs        the names of all sub-directories.
   *
   * @return a [[Some(File)]] found for the given parameters, or [[None]] if
   *                   no file with the given names can be found in the given directories.
   */
  private def searchFile(
    fileNames: Seq[String],
    baseDir: String,
    subDirs: Array[String]
  ): Option[File] = {
    val base = new File(baseDir)
    // The first sub-dir is country code, see #getSubDirs()
    val country = new File(base, subDirs(0))
    var dirs = Seq(base, country)
    var dir = base
    var i = 1
    while (i < subDirs.length) {
      dir = new File(dir, subDirs(i))
      dirs = dirs :+ dir
      i += 1
    }
    dirs.reverse
      .view
      .flatMap(dir => fileNames.view.map(name => new File(dir, sanitizeFilename(name))))
      .find(_.isFile)
  }

  @inline
  private[this] def getSubDirs =
    Array(
      ctx.countryCode.toLowerCase(Locale.ROOT),
      ctx.organizationCode.toLowerCase(Locale.ROOT),
      ctx.legalEntityCode.toLowerCase(Locale.ROOT),
      ctx.branchCode.toLowerCase(Locale.ROOT)
    )

  def getBaseDir: String = {
    if (isBlank(formsStore) || formsStore.startsWith("@")) {
      throw new PrintException("Unable to locate forms because forms store is not defined.")
    }
    formsStore
  }

  def getIccProfilePath: String =
    getBaseDir + "/AdobeRGB1998.icc"

}
