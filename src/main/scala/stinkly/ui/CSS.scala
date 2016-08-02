package stinkly.ui

import javafx.scene.Scene

object CSS {

  lazy val JFoenixDesign = loadCss("/resources/css/jfoenix-design.css")
  lazy val JFoenixFonts  = loadCss("/resources/css/jfoenix-fonts.css")

  lazy val StinklyCss = loadCss("/css/stinkly.css")

  lazy val All = List(JFoenixDesign, JFoenixFonts, StinklyCss)

  def install(scene: Scene): Scene = {
    All.foreach(scene.getStylesheets().add)
    scene
  }

  private[this] def loadCss(path: String) = {
    CSS.getClass().getResource(path).toExternalForm()
  }
}
