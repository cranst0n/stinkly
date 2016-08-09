package stinkly.core

import scala.sys.process._
import scala.util.Try

import java.io.ByteArrayOutputStream

import com.typesafe.scalalogging.LazyLogging

import cats.data.Xor

trait Downloader {
  def matches(url: String): Boolean
  def download(url: String): Xor[Throwable, Link]
}

object Downloader extends LazyLogging {

  def curl(url: String, options: String*): Array[Byte] = {
    logger.debug(s"""curl-ing ${ options.mkString(" ") } '$url'""")
    val baos = new ByteArrayOutputStream()
    (s"curl -s $url" #> baos).!
    baos.toByteArray
  }

  def isAvailable(): Boolean = {
    val res = Try(curl("google.com", "-m 1")).toOption.isDefined
    logger.debug(s"Downloader.isAvailable(): $res")
    res
  }

  def forUrl(url: String): Option[Downloader] = {
    val res = Available.find(_.matches(url))
    logger.debug(s"Downloader.forUrl($url): ${ res.getClass().getName() }")
    res
  }
  private[core] val Available =
    List(ImgurDownloader, RedditUploadsDownloader, SimpleImageDownloader)
}

object SimpleImageDownloader extends Downloader with LazyLogging {

  override def matches(url: String): Boolean = {
    val res = supportedExtensions.exists(ext => url.endsWith(ext))
    logger.trace(s"SimpleImageDownloader.matches($url): $res")
    res
  }

  override def download(url: String): Xor[Throwable, Link] = {
    Xor.catchNonFatal {
      val fileName = url.drop(url.lastIndexOf('/') + 1)
      Link(s"$fileName", Downloader.curl(url), url)
    }
  }

  private[this] val supportedExtensions = List(".gif", ".jpeg", ".jpg", ".mp4", ".png")
}

object ImgurDownloader extends Downloader with LazyLogging {

  def matches(url: String): Boolean = {
    val res = List(SimpleURL).exists(_.findFirstIn(url.drop(url.indexOf("imgur"))).isDefined)
    logger.trace(s"ImgurDownloader.matches($url): $res")
    res
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

object RedditUploadsDownloader extends Downloader with LazyLogging {

  override def matches(url: String): Boolean = {
    val res =
      List(SimpleURL).exists(_.findFirstIn(url.drop(url.indexOf("i.reddituploads"))).isDefined)
    logger.trace(s"RedditUploadsDownloader.matches($url): $res")
    res
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
