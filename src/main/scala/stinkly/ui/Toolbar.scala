package stinkly.ui

import scalafx.Includes._

import javafx.application.Platform
import javafx.concurrent.Task
import javafx.geometry.Insets
import javafx.scene.control.{ Label, Tooltip }
import javafx.scene.layout.{ HBox, Priority, Region, StackPane, VBox }
import javafx.scene.text.Text
import javafx.stage.FileChooser

import com.jfoenix.controls._

import de.jensd.fx.glyphs.GlyphsFactory
import de.jensd.fx.glyphs.materialdesignicons.{ MaterialDesignIcon, MaterialDesignIconView }

import better.files._

import cats.data.Xor.Left

import stinkly.core._

class Toolbar(database: Database, rootStackPane: StackPane) extends JFXToolbar {

  private[this] val importLinkPackButton = new JFXButton()
  private[this] val importFileChooser    = new FileChooser()

  private[this] val showLinkPackCreatorButton = new JFXButton()
  private[this] val createLinkPackDialog      = new CreateLinkPackDialog(database, rootStackPane)

  initialize()

  private[this] def initialize(): Unit = {

    importLinkPackButton.setTooltip(new Tooltip("Import Link Pack"))
    importLinkPackButton.setGraphic(
      new GlyphsFactory(classOf[MaterialDesignIconView])
        .createIcon(MaterialDesignIcon.FILE_IMPORT, "2em")
    )

    showLinkPackCreatorButton.setTooltip(new Tooltip("Create Link Pack"))
    showLinkPackCreatorButton.setGraphic(
      new GlyphsFactory(classOf[MaterialDesignIconView])
        .createIcon(MaterialDesignIcon.NEW_BOX, "2em")
    )

    setLeftItems(importLinkPackButton)
    if (Downloader.isAvailable()) setRightItems(showLinkPackCreatorButton)

    importLinkPackButton.onAction = handle { showImportFileChooser() }
    showLinkPackCreatorButton.onMouseClicked = handle { createLinkPackDialog.show() }
  }

  private[this] def showImportFileChooser(): Unit = {
    importFileChooser.setTitle("Import Stinkly Link Pack")
    importFileChooser
      .getExtensionFilters()
      .addAll(new FileChooser.ExtensionFilter("Stinkly Link Pack", "*.stinkly"))
    val selectedFile = importFileChooser.showOpenDialog(getScene().getWindow())

    Option(selectedFile).foreach { f =>
      importFileChooser.setInitialDirectory(f.getParentFile())
      database.importLinkPackFile(f.toScala)
    }
  }
}

private[this] class CreateLinkPackDialog(database: Database, rootStackPane: StackPane)
    extends JFXDialog {

  private[this] val rootContentPane    = new StackPane()
  private[this] val contentBox         = new VBox(10)
  private[this] val titleBox           = new HBox(10)
  private[this] val titleLabel         = new Label("Paste Links Below")
  private[this] val linkTextArea       = new JFXTextArea()
  private[this] val actionButtonBox    = new HBox(10)
  private[this] val createPackButton   = new JFXButton("Create Link Pack")
  private[this] val closeButton        = new JFXButton()
  private[this] val busySpinner        = new JFXSpinner()
  private[this] val createProgressText = new Text()

  closeButton.setGraphic(
    new GlyphsFactory(classOf[MaterialDesignIconView])
      .createIcon(MaterialDesignIcon.CLOSE, "1.5em")
  )
  closeButton.onAction = handle(close())
  busySpinner.setVisible(false)

  StackPane.setMargin(createProgressText, new Insets(50, 0, 0, 0))

  contentBox.getStyleClass.add("dialog-content")
  titleLabel.getStyleClass.add("title")
  closeButton.getStyleClass.add("close-button")
  createPackButton.getStyleClass.add("action-button")

  val spacer = new Region()
  HBox.setHgrow(spacer, Priority.ALWAYS)

  titleBox.getChildren().addAll(titleLabel, spacer, closeButton)
  actionButtonBox.getChildren().addAll(createPackButton)
  contentBox.getChildren().addAll(titleBox, linkTextArea, actionButtonBox)

  rootContentPane.getChildren().addAll(contentBox, busySpinner, createProgressText)

  setOverlayClose(false)
  setDialogContainer(rootStackPane)
  setContent(rootContentPane)

  createPackButton.onAction = handle {
    val urls = linkTextArea.getText().split("\n").map(_.trim).filter(_.nonEmpty).toList
    if (urls.nonEmpty) {
      val task = new CreateLinkPackTask(urls)
      new Thread(task).start()
    }
  }

  class CreateLinkPackTask(urls: List[String]) extends Task[(List[Throwable], LinkPack)] {

    override protected def running(): Unit = disableControls(true)

    override protected def succeeded(): Unit = {
      linkTextArea.setText("")
      disableControls(false)

      val (failed, _) = getValue()

      if (failed.isEmpty) {
        close()
      } else {
        failed.foreach(throwable => println(throwable.getMessage()))
      }
    }

    override protected def failed(): Unit = disableControls(false)

    override protected def call(): (List[Throwable], LinkPack) = {

      updateProgressText(s"0 / ${ urls.size }")

      val xors = urls.zipWithIndex.map {
        case (url, ix) =>
          val l = Link.fromUrl(url)
          updateProgressText(s"${ ix + 1 } / ${ urls.size }")
          l
      }

      val (failures, links) = xors.partition(_.isLeft)
      val linkPack          = LinkPack(links.toList.map(_.toOption).flatten)

      if (links.nonEmpty) { database.create(linkPack) }

      (xors.collect { case Left(ex) => ex }, linkPack)
    }

    private[this] def updateProgressText(text: String): Unit = {
      Platform.runLater(new Runnable() {
        override def run(): Unit = {
          createProgressText.setText(text)
        }
      })
    }

    private[this] def disableControls(disabled: Boolean): Unit = {
      busySpinner.setVisible(disabled)
      createProgressText.setVisible(disabled)
      linkTextArea.setDisable(disabled)
      createPackButton.setDisable(disabled)
      closeButton.setDisable(disabled)
    }
  }
}
