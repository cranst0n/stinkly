package stinkly.ui

import scala.util.Random

import scalafx.Includes._

import java.net.URL
import java.util.concurrent.{ Executors, ThreadFactory }

import javafx.application.{ Application, Platform }
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.{ Priority, StackPane, VBox }
import javafx.stage.{ Stage, WindowEvent }

import com.typesafe.scalalogging.LazyLogging

import better.files._

import stinkly.BuildInfo
import stinkly.core.DiskDatabase

class Main extends Application with LazyLogging {

  private[this] val database = new DiskDatabase(File.home / ".stinkly")
  private[this] val executorService = Executors.newCachedThreadPool(new ThreadFactory() {
    override def newThread(runnable: Runnable): Thread = {
      val thread = new Thread(runnable)
      thread.setDaemon(true)
      thread
    }
  })

  override def start(stage: Stage): Unit = {

    logger.debug(s"Starting application...")

    val rootStackPane     = new StackPane()
    val pane              = new VBox()
    val toolbar           = new Toolbar(database, executorService, rootStackPane)
    val linkPackNavigator = new LinkPackNavigator(database, executorService, rootStackPane)

    toolbar.getStyleClass().add("toolbar")

    VBox.setVgrow(linkPackNavigator, Priority.ALWAYS)
    pane.getChildren().addAll(toolbar, linkPackNavigator)
    rootStackPane.getChildren().addAll(pane)

    val scene = CSS.install(new Scene(rootStackPane, 1200, 800))

    stage.setTitle(s"Stinkly - v${ BuildInfo.gitDescribe }")
    stage.getIcons().add(getAppIcon())
    stage.setScene(scene)
    stage.onCloseRequest = handle { e: WindowEvent =>
      logger.trace(s"Calling Platform.exit()")
      Platform.exit()
    }

    stage.show()
  }

  override def stop(): Unit = {
    logger.debug(s"Stopping application...")
    logger.trace(s"Shutting down executor service.")
    executorService.shutdownNow()
    logger.trace(s"Calling System.exit()")
    System.exit(0)
  }

  private[this] def getAppIcon(): Image = {

    def findAvailableIcons(x: Int = 0): List[URL] = {
      Option(classOf[Main].getResource(s"/app-icon-$x.png"))
        .orElse(Option(classOf[Main].getClassLoader().getResource(s"app-icon-$x.png")))
        .map(path => path :: findAvailableIcons(x + 1))
        .getOrElse(Nil)
    }

    val availableIcons = findAvailableIcons()
    val appIconPath    = availableIcons(Random.nextInt(availableIcons.size))
    new Image(appIconPath.toString)
  }
}

object Main extends LazyLogging {

  def main(args: Array[String]): Unit = {
    Application.launch(classOf[Main], args: _*)
  }
}
