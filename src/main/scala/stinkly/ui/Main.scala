package stinkly.ui

import scala.util.Random

import java.net.URL

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.layout.{ Priority, StackPane, VBox }
import javafx.stage.Stage

import better.files._

import stinkly.BuildInfo
import stinkly.core.DiskDatabase

class Main extends Application {

  override def start(stage: Stage): Unit = {

    val database = new DiskDatabase(File.home / ".stinkly")

    val rootStackPane     = new StackPane()
    val pane              = new VBox()
    val toolbar           = new Toolbar(database, rootStackPane)
    val linkPackNavigator = new LinkPackNavigator(database, rootStackPane)

    toolbar.getStyleClass().add("toolbar")

    VBox.setVgrow(linkPackNavigator, Priority.ALWAYS)
    pane.getChildren().addAll(toolbar, linkPackNavigator)
    rootStackPane.getChildren().addAll(pane)

    val scene = CSS.install(new Scene(rootStackPane, 1200, 800))

    stage.setTitle(s"Stinkly - v${ BuildInfo.gitDescribe }")
    stage.getIcons().add(getAppIcon())
    stage.setScene(scene)

    stage.show()
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

object Main {

  def main(args: Array[String]): Unit = {
    Application.launch(classOf[Main], args: _*)
  }
}
