package stinkly.core

import java.time.Instant
import java.util.zip.ZipFile

import com.sksamuel.scrimage.Using

import org.apache.commons.io.IOUtils

import better.files._

import cats.data.Xor

import io.circe.generic.auto._
import io.circe.parser._

import stinkly.BuildInfo

case class Link(source: String, content: Array[Byte], comment: String = "")
case class LinkPackManifest(version: String, created: Long)
case class LinkPack(manifest: LinkPackManifest, links: List[Link]) {
  val version = manifest.version
  val created = Instant.ofEpochMilli(manifest.created)
}

object Link {
  def fromUrl(url: String): Xor[Throwable, Link] = {
    Downloader
      .forUrl(url)
      .map(_.download(url))
      .getOrElse(Xor.left(new RuntimeException(s"Download failed: $url")))
  }
}

object LinkPackManifest extends Using {

  val FileName = "manifest.json"

  def apply(): LinkPackManifest =
    new LinkPackManifest(BuildInfo.gitDescribe, System.currentTimeMillis)

  def fromFile(file: File): LinkPackManifest = {
    decode[LinkPackManifest](file.contentAsString).getOrElse(LinkPackManifest())
  }

  def fromZipFile(file: File): LinkPackManifest = {
    using(new ZipFile(file.toJava)) { zipFile =>
      using(zipFile.getInputStream(zipFile.getEntry(FileName))) { entryStream =>
        decode[LinkPackManifest](new String(IOUtils.toByteArray(entryStream)))
          .getOrElse(LinkPackManifest())
      }
    }
  }
}

object LinkPack extends Using {

  def apply(links: List[Link]): LinkPack = {
    LinkPack(LinkPackManifest(), links)
  }

  def fromFile(file: File): LinkPack = {

    val tempDir: File = file.unzip()
    tempDir.toJava.deleteOnExit()

    val links = tempDir.list
      .filter(_.name != LinkPackManifest.FileName)
      .map(f => Link(f.uri.toString, f.loadBytes))
      .toList

    LinkPack(LinkPackManifest.fromFile(tempDir / LinkPackManifest.FileName), links)
  }

  def instantOf(file: File): Instant = {
    Instant.ofEpochMilli(file.nameWithoutExtension.toLong)
  }

  def fileNameFor(linkPack: LinkPack): String = {
    s"${ linkPack.created.toEpochMilli() }.stinkly"
  }

}
