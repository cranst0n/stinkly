package stinkly.core

import scala.sys.process._
import scala.util.Try

import java.io.ByteArrayOutputStream

import cats.data.Xor

trait Downloader {
  def matches(url: String): Boolean
  def download(url: String): Xor[Throwable, Link]
}

object Downloader {

  def curl(url: String): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    (s"curl -s $url" #> baos).!
    baos.toByteArray
  }

  def isAvailable(): Boolean = {
    Try(curl("www.google.com")).toOption.isDefined
  }

  def forUrl(url: String): Option[Downloader] = Available.find(_.matches(url))
  private[core] val Available =
    List(ImgurDownloader, RedditUploadsDownloader, SimpleImageDownloader)
}

object SimpleImageDownloader extends Downloader {

  override def matches(url: String): Boolean = {
    supportedExtensions.exists(ext => url.endsWith(ext))
  }

  override def download(url: String): Xor[Throwable, Link] = {
    Xor.catchNonFatal {
      val fileName = url.drop(url.lastIndexOf('/') + 1)
      Link(s"$fileName", Downloader.curl(url), url)
    }
  }

  private[this] val supportedExtensions = List(".gif", ".jpeg", ".jpg", ".mp4", ".png")
}

object ImgurDownloader extends Downloader {

  def matches(url: String): Boolean = {
    List(SimpleURL).exists(_.findFirstIn(url.drop(url.indexOf("imgur"))).isDefined)
  }

  override def download(url: String): Xor[Throwable, Link] = {
    url.drop(url.indexOf("imgur")) match {
      case SimpleURL(id, extension) => downloadSingle(id, Option(extension))
      case _                        => Xor.left(new RuntimeException(s"Unsupported URL: $url"))
    }
  }

  private[this] def downloadSingle(id: String, extension: Option[String]): Xor[Throwable, Link] = {
    val compatibleExtension = extension.map { ext =>
      if (ext startsWith ".gif") ".mp4"
      else ext
    }.getOrElse(".jpeg")

    val url = s"""http://i.imgur.com/$id$compatibleExtension"""

    Xor.catchNonFatal {
      Link(s"$id$compatibleExtension", Downloader.curl(url), url)
    }
  }

  private[this] val SimpleURL = """imgur\.com/(\w+)(.[a-zA-Z]+)?""".r
}

object RedditUploadsDownloader extends Downloader {

  override def matches(url: String): Boolean = {
    List(SimpleURL).exists(_.findFirstIn(url.drop(url.indexOf("i.reddituploads"))).isDefined)
  }

  override def download(url: String): Xor[Throwable, Link] = {
    url.drop(url.indexOf("i.reddituploads")) match {
      case SimpleURL(id) => {
        Xor.catchNonFatal {
          Link(id, Downloader.curl(url), url)
        }
      }
      case _ => Xor.left(new RuntimeException(s"Unsupported URL: $url"))
    }
  }

  private[this] val SimpleURL = """i\.reddituploads\.com/(.+)""".r
}
