/*
 * Copyright (c) 2022 Riege Software International. All rights reserved.
 * Use is subject to license terms.
 */

package com.riege.jasperservice.backend

import java.awt.Point
import java.io.{BufferedInputStream, BufferedOutputStream, FileInputStream, InputStream, OutputStream}
import java.util.Locale

import com.riege.jasperservice.FileUtils
import com.riege.jasperservice.backend.PDFProducer.PDF_PERMISSIONS

import better.files._
import com.lowagie.text.pdf.{PdfReader, PdfStamper, PdfTemplate}
import com.lowagie.text.{DocWriter, Rectangle}
import org.apache.batik.anim.dom.SAXSVGDocumentFactory
import org.apache.batik.bridge.{BridgeContext, DocumentLoader, GVTBuilder, UserAgentAdapter}
import org.apache.batik.util.XMLResourceDescriptor

object PDFUtils {

  private[this] val BUFFER_SIZE = 32 * 1024

  /**
   * Generates a new content which equals the given content, but with the
   * specified image underlayed under each page of the PDF.
   *
   * @param fileStore
   * the path to read and store PDFs.
   * @param password
   * the password to protect the PDF document with.
   * @param pdfFileName
   * byte array of the original PDF document.
   * @param imageName
   * the name of the background image to underlay.
   * @param language
   * the locale to load the image for.
   * @return the file name of the new PDF-content with underlayed background image.
   * @throws Exception
   * if the image could not be loaded, the given content can not
   * be read as a PDF by iText-PdfReader, or the new content with
   * underlayed background could not be generated.
   */
  @throws[Exception]
  def addBackgroundImage(
    fl: FormsLoader,
    fileStore: String,
    password: String,
    pdfFileName: String,
    imageName: String,
    language: Locale
  ): String = {
    val lang = Option(language)
    val pdfFile = File(fileStore, pdfFileName)
    val resultName = FileUtils.newTmpFileName
    val resultFile = File(fileStore, resultName)
    for {
      pdfInput <- new BufferedInputStream(pdfFile.newFileInputStream, BUFFER_SIZE).autoClosed
      resultOutput <- new BufferedOutputStream(resultFile.newFileOutputStream(), BUFFER_SIZE).autoClosed
    } {
      if (imageName.toLowerCase(Locale.ROOT).endsWith(".svg")) {
        addSVGBackgroundImage(fl, password, pdfInput, imageName, lang, resultOutput)
      } else {
        addRasterBackgroundImage(fl, password, pdfInput, imageName, lang, resultOutput)
      }
    }
    resultName
  }

  private def addRasterBackgroundImage(
    fl: FormsLoader,
    password: String,
    pdfInput: InputStream,
    imageName: String,
    language: Option[Locale],
    resultOutput: OutputStream
  ): Unit = {
    val image = fl.getImage(imageName, language)
    val ownerPassword = DocWriter.getISOBytes(password)
    // create a PdfReader for the given content
    val reader = new PdfReader(pdfInput, ownerPassword)
    // construct sizes and calculate the point to where the lower right
    // corner of the image is drawn on the PDF-page.
    val imageSize = new Rectangle(image.getWidth, image.getHeight)
    val pdfSize = reader.getPageSize(1)
    val p = calculateLowerRight(pdfSize, imageSize)
    image.setAbsolutePosition(0, 0)
    image.scaleAbsolute(p.x, p.y)
    val pageCount = reader.getNumberOfPages
    val stamper = new PdfStamper(reader, resultOutput)
    if (reader.isEncrypted) {
      stamper.setEncryption(null, ownerPassword, PDF_PERMISSIONS, true)
      stamper.setFullCompression()
    }
    var i = 1
    while (i <= pageCount) {
      stamper.getUnderContent(i).addImage(image)
      i += 1
    }
    // closing the stamper will write result into our output stream
    stamper.close()
  }

  private def addSVGBackgroundImage(
    fl: FormsLoader,
    password: String,
    pdfInput: InputStream,
    imageName: String,
    language: Option[Locale],
    resultOutput: OutputStream
  ): Unit = {
    val file = fl.getFile(imageName, language)
        .getOrElse(throw new PrintException(
          s"Image '$imageName' could not be found. Basedir: ${fl.getBaseDir}, ${fl.ctx}"))
    // Disables warnings from batik.
    System.setProperty("org.apache.batik.warn_destination", "false")
    val parser = XMLResourceDescriptor.getXMLParserClassName
    val factory = new SAXSVGDocumentFactory(parser)
    val userAgent = new UserAgentAdapter
    val loader = new DocumentLoader(userAgent)
    val ctx = new BridgeContext(userAgent, loader)
    ctx.setDynamicState(BridgeContext.STATIC)
    try {
      for (
        is <- new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE).autoClosed
      ) {
        val svgImage = factory.createSVGDocument(file.toURI.toString, is)
        val graphicsNode = new GVTBuilder().build(ctx, svgImage)
        val ownerPassword = DocWriter.getISOBytes(password)
        val reader = new PdfReader(pdfInput, ownerPassword)
        val pdfSize = reader.getPageSize(1)
        val width = pdfSize.getWidth
        val height = pdfSize.getHeight
        val stamper = new PdfStamper(reader, resultOutput)
        if (reader.isEncrypted) {
          stamper.setEncryption(null, ownerPassword, PDF_PERMISSIONS, true)
          stamper.setFullCompression()
        }
        val pageCount = reader.getNumberOfPages
        val t = PdfTemplate.createTemplate(stamper.getWriter,
          width, height
        )
        val g2d = t.createGraphicsShapes(width, height)
        graphicsNode.paint(g2d)
        g2d.dispose()
        var i = 1
        while (i <= pageCount) {
          stamper.getUnderContent(i).addTemplate(t, 0, 0)
          i += 1
        }
        stamper.close()
      }
    } finally {
      ctx.dispose()
    }
  }

  /**
   * Calculates the lower right corner in the targets rectangle space, that
   * can be used to best insert the sources rectangle with maximum possible
   * scaling, whilst not stretching the sources rectangle.
   * <p>
   * It is assumed that the top left corner of the source is drawn at the top
   * left corner in the targets space.
   *
   * @param targetRect
   * the rectangle covering of the source.
   * @param sourceRect
   * the rectangle covering the target, that is to be layed over
   * the source.
   * @return the lower right corner in the targets space, to which the source
   *         rectangle should be scaled to have maximum size without being
   *         stretched.
   */
  private def calculateLowerRight(targetRect: Rectangle, sourceRect: Rectangle): Point = {
    val tWidth = targetRect.getWidth
    val tHeight = targetRect.getHeight
    val sWidth = sourceRect.getWidth
    val sHeight = sourceRect.getHeight
    val targetRatio = tWidth / tHeight
    val sourceRatio = sWidth / sHeight
    var tX = 0
    var tY = 0
    if (sourceRatio > targetRatio) {
      tX = tWidth.toInt
      tY = (tWidth * sHeight / sWidth + 0.5f).toInt
    } else if (sourceRatio < targetRatio) {
      tX = (tHeight * sWidth / sHeight + 0.5f).toInt
      tY = tHeight.toInt
    } else {
      tX = tWidth.toInt
      tY = tHeight.toInt
    }
    new Point(tX, tY)
  }

}
