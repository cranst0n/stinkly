package stinkly.core

import scala.collection.JavaConverters._

import java.io.FileOutputStream
import java.util.Comparator
import java.util.zip._

import javafx.collections.{ FXCollections, ObservableList }

import com.sksamuel.scrimage.Using

import io.circe.generic.auto._
import io.circe.syntax._

import better.files._

trait Database {

  def linkPackFiles: ObservableList[File]

  def importLinkPackFile(linkPackFile: File): File

  def create(linkPack: LinkPack): File

}

class DiskDatabase(directory: File) extends Database with Using {

  directory.createIfNotExists(true)
  val packDirectory = (directory / "packs").createIfNotExists(true)

  override val linkPackFiles = {
    val list = FXCollections.observableArrayList[File]()
    list.addAll(
      (directory / "packs").list
        .filter(f => f.extension == Some(".stinkly"))
        .toList
        .sortWith((a, b) => a.name > b.name)
        .asJava
    )
    list
  }

  override def importLinkPackFile(linkPackFile: File): File = {
    val copied = linkPackFile.copyTo(packDirectory / linkPackFile.name)
    linkPackFiles.addAll(copied)
    FXCollections.sort(linkPackFiles, new Comparator[File]() {
      override def compare(a: File, b: File): Int = {
        a.name compareTo b.name
      }
    })
    copied
  }

  override def create(linkPack: LinkPack): File = {
    val file = directory / "packs" / LinkPack.fileNameFor(linkPack)

    using(new FileOutputStream(file.toJava)) { fileOut =>
      using(new ZipOutputStream(fileOut)) { zipOut =>
        val manifestEntry = new ZipEntry(LinkPackManifest.FileName)
        zipOut.putNextEntry(manifestEntry)
        zipOut.write(linkPack.manifest.asJson.spaces2.getBytes)
        zipOut.closeEntry()

        linkPack.links.zipWithIndex.foreach {
          case (link, ix) =>
            val entry = new ZipEntry(link.source)
            entry.setComment(link.comment)

            zipOut.putNextEntry(entry)
            zipOut.write(link.content)
            zipOut.closeEntry()
        }
      }
    }

    linkPackFiles.addAll(file)
    FXCollections.sort(linkPackFiles, new Comparator[File]() {
      override def compare(a: File, b: File): Int = {
        LinkPackManifest.fromZipFile(a).created compareTo
          LinkPackManifest.fromZipFile(b).created
      }
    })

    file
  }

}
