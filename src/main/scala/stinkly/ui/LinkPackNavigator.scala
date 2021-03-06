package stinkly.ui

import scala.collection.JavaConverters._
import scala.util.Try

import scalafx.Includes._

import java.nio.file.StandardOpenOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService

import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.concurrent.Task
import javafx.geometry.{ Insets, Pos }
import javafx.scene.control.ScrollPane
import javafx.scene.image.{ Image, ImageView }
import javafx.scene.media.{ Media, MediaPlayer, MediaView }
import javafx.scene.layout._
import javafx.scene.text.Text
import javafx.stage.{ FileChooser, Window }

import com.jfoenix.controls._
import com.jfoenix.effects.JFXDepthManager
import com.typesafe.scalalogging.LazyLogging

import de.jensd.fx.glyphs.GlyphsFactory
import de.jensd.fx.glyphs.materialdesignicons.{ MaterialDesignIcon, MaterialDesignIconView }

import better.files._

import stinkly.core._

class LinkPackNavigator(database: Database,
                        executorService: ExecutorService,
                        rootStackPane: StackPane)
    extends BorderPane
    with LazyLogging {

  case class LinkPackButton(file: File) extends HBox(0) {

    val showPackButton = new JFXButton(
      dateTimeFormatter.format(LinkPack.instantOf(file).atZone(ZoneId.systemDefault()))
    )
    val packInfoButton   = new JFXButton()
    val deletePackButton = new JFXButton()

    setMaxWidth(Double.MaxValue)
    showPackButton.setMaxWidth(Double.MaxValue)
    packInfoButton.setMaxWidth(Double.MaxValue)
    deletePackButton.setMaxWidth(Double.MaxValue)

    HBox.setHgrow(showPackButton, Priority.ALWAYS)
    showPackButton.getStyleClass().add("button")
    packInfoButton.getStyleClass().add("action-button")
    deletePackButton.getStyleClass().add("action-button")

    packInfoButton.setGraphic(
      new GlyphsFactory(classOf[MaterialDesignIconView])
        .createIcon(MaterialDesignIcon.INFORMATION_OUTLINE, "1em")
    )

    deletePackButton.setGraphic(
      new GlyphsFactory(classOf[MaterialDesignIconView])
        .createIcon(MaterialDesignIcon.DELETE, "1em")
    )

    showPackButton.onAction = handle {
      showLinkPack(this)
    }

    packInfoButton.onAction = handle {
      val layout = new JFXDialogLayout()
      layout.setHeading(new Text(showPackButton.getText()))
      layout.setBody(
        new VBox(
          new Text(s"File:    ${ file.toString() }"),
          new Text(s"Size:    ${ (file.size / 1024d / 1024d).toInt } MB"),
          new Text(s"Version: ${ LinkPackManifest.fromFile(file).version }")
        )
      )

      val dialog = new JFXDialog()
      dialog.setDialogContainer(rootStackPane)
      dialog.setContent(layout)

      dialog.show()
    }

    deletePackButton.onAction = handle {
      val dialog       = new JFXDialog()
      val layout       = new JFXDialogLayout()
      val deleteButton = new JFXButton("Delete")
      deleteButton.onAction = handle {
        database.delete(file)
        dialog.close()
      }

      layout.setBody(new Text("Are you sure you want to delete this file?"))
      layout.setActions(deleteButton)

      dialog.setDialogContainer(rootStackPane)
      dialog.setContent(layout)

      dialog.show()
    }

    getChildren().addAll(showPackButton, packInfoButton, deletePackButton)

    def setSelected(selected: Boolean): Unit = {
      if (selected) {
        showPackButton.getStyleClass().remove("button")
        showPackButton.getStyleClass().add("selected")
      } else {
        showPackButton.getStyleClass().remove("selected")
        showPackButton.getStyleClass().add("button")
      }
    }
  }

  private[this] val padding = new Insets(5, 5, 5, 5)

  private[this] val linkPackScollPane = new ScrollPane()
  private[this] val linkPackList      = new VBox(15)
  private[this] val linkPane          = new ScrollPane()
  private[this] val linkList          = new VBox(15)

  private[this] val loadingSpinner = new HBox {
    private[this] val spinner     = new JFXSpinner()
    private[this] val spacerLeft  = new Region()
    private[this] val spacerRight = new Region()

    spinner.getStyleClass().add("loading-spinner")
    HBox.setHgrow(spacerLeft, Priority.ALWAYS)
    HBox.setHgrow(spacerRight, Priority.ALWAYS)

    getChildren().addAll(spacerLeft, spinner, spacerRight)
  }

  private[this] val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE MMMM d, h:mma")

  initialize()

  private[this] def initialize(): Unit = {

    setPadding(padding)

    linkPackScollPane.getStyleClass().add("link-pack-list-pane")
    linkPackList.getStyleClass().add("link-pack-list")
    linkPane.getStyleClass().add("link-list-pane")
    linkList.getStyleClass().add("link-list")

    populateLinkPackList()

    database.linkPackFiles.addListener(new ListChangeListener[File]() {
      override def onChanged(change: ListChangeListener.Change[_ <: File]): Unit = {
        populateLinkPackList()
      }
    })

    linkPackScollPane.setFitToWidth(true)
    linkPackScollPane.setContent(linkPackList)
    linkPackList.setFillWidth(true)

    linkPane.setFitToWidth(true)
    linkPane.setContent(linkList)
    linkList.setFillWidth(true)

    BorderPane.setMargin(linkPackScollPane, padding)
    BorderPane.setMargin(linkPane, padding)

    setLeft(linkPackScollPane)
    setCenter(linkPane)
  }

  private[this] def populateLinkPackList(): Unit = {
    logger.trace("Populating link pack list...")
    val linkPackFiles = database.linkPackFiles.asScala

    Platform.runLater(new Runnable() {
      override def run(): Unit = {
        linkPackList.getChildren().clear()
        val buttons = linkPackFiles.foreach { f =>
          linkPackList.getChildren().add(LinkPackButton(f))
        }
      }
    })
  }

  private[this] def showLinkPack(sourceButton: LinkPackButton): Unit = {

    logger.debug(s"Showing link pack: ${ sourceButton.file }")

    val task = new Task[Unit] {
      override protected def call(): Unit = {

        Platform.runLater(new Runnable() {
          override def run(): Unit = {

            linkPackList.getChildren().asScala.foreach {
              case button: LinkPackButton =>
                button.setSelected(button eq sourceButton)
            }

            sourceButton.getStyleClass().remove("button")
            sourceButton.getStyleClass().add("selected")

            linkList.getChildren().clear()
            linkList.getChildren().add(loadingSpinner)
          }
        })

        logger.trace(s"Loading link pack: ${ sourceButton.file }")
        val linkPack = LinkPack.fromFile(sourceButton.file)
        logger.trace(s"Finished loading link pack: ${ sourceButton.file }")

        Platform.runLater(new Runnable() {
          override def run(): Unit = {
            linkList.getChildren().clear()
            linkPack.links.foreach { link =>
              logger.trace(s"Creating LinkCard for: ${ link.source }")
              linkList.getChildren().addAll(LinkCard(link))
            }
          }
        })
      }
    }

    executorService.submit(task)
  }

  case class LinkCard(link: Link) extends HBox {

    private[this] val card             = new VBox()
    private[this] val contentContainer = new VBox()

    private[this] val spacerLeft  = new Region()
    private[this] val spacerRight = new Region()

    HBox.setHgrow(spacerLeft, Priority.ALWAYS)
    HBox.setHgrow(spacerRight, Priority.ALWAYS)

    card.getStyleClass().add("link-card")

    linkPane.widthProperty.onChange { (_, _, width) =>
      sizeMe(linkPane.width(), linkPane.height())
    }

    linkPane.heightProperty.onChange { (_, _, height) =>
      sizeMe(linkPane.width(), linkPane.height())
    }

    JFXDepthManager.setDepth(card, 2)
    sizeMe(linkPane.width(), linkPane.height())

    lazy val mediaNode = ContentNode(link, () => sizeMe(linkPane.width(), linkPane.height()))

    contentContainer.getChildren().add(mediaNode)
    card.getChildren().add(contentContainer)

    getChildren().addAll(spacerLeft, card, spacerRight)

    private[this] def sizeMe(parentWidth: Double, parentHeight: Double): Unit = {

      val prefWidth  = ((parentWidth - 50) min mediaNode.contentWidth())
      val prefHeight = ((parentHeight - 100) min mediaNode.contentHeight())

      contentContainer.maxWidthProperty.set(prefWidth)
      contentContainer.maxHeightProperty.set(prefHeight)

      mediaNode.setContentPrefWidth(prefWidth)
      mediaNode.setContentPrefHeight(prefHeight)
    }
  }

  case class ContentNode(link: Link, sizeCallback: Function0[Unit] = () => ()) extends VBox(10) {

    val controlPanel = new HBox(5)
    val playButton   = new JFXButton()
    val pauseButton  = new JFXButton()
    val stopButton   = new JFXButton()
    val progressBar  = new JFXProgressBar(0)
    val spacer       = new Region()
    val zoomButton   = new JFXButton()
    val saveAsButton = new JFXButton()

    val contentNode = Try {

      logger.debug(s"Loading link card for: ${ link.source }")

      val media = new Media(link.source)
      logger.trace(s"Created Media object for: ${ link.source }")
      val mediaPlayer = new MediaPlayer(media)
      logger.trace(s"Created MediaPlayer for: ${ link.source }")
      val mediaView = new MediaView(mediaPlayer)
      logger.trace(s"Created MediaView for: ${ link.source }")

      mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE)
      mediaPlayer.currentTimeProperty.onChange { (_, _, time) =>
        val progress = time.toMillis.toDouble / media.duration().toMillis.toDouble
        progressBar.setProgress(progress)
      }

      mediaPlayer.setOnReady(new Runnable() {
        override def run(): Unit = {
          sizeCallback()
        }
      })

      playButton.onAction = handle { mediaPlayer.play() }
      pauseButton.onAction = handle { mediaPlayer.pause() }
      stopButton.onAction = handle { mediaPlayer.stop() }

      playButton.setGraphic(
        new GlyphsFactory(classOf[MaterialDesignIconView])
          .createIcon(MaterialDesignIcon.PLAY, "2em")
      )

      pauseButton.setGraphic(
        new GlyphsFactory(classOf[MaterialDesignIconView])
          .createIcon(MaterialDesignIcon.PAUSE, "2em")
      )

      stopButton.setGraphic(
        new GlyphsFactory(classOf[MaterialDesignIconView])
          .createIcon(MaterialDesignIcon.STOP, "2em")
      )

      playButton.getStyleClass().add("playback-control-button")
      pauseButton.getStyleClass().add("playback-control-button")
      stopButton.getStyleClass().add("playback-control-button")
      progressBar.getStyleClass().add("playback-progress-bar")

      controlPanel.getChildren().addAll(playButton, pauseButton, stopButton, progressBar)

      mediaView
    }.toOption.getOrElse {

      logger.debug(s"[${ link.source }] as Media load failed. Loading ImageView...")

      val imageView = new ImageView(new Image(link.source))
      imageView.setPreserveRatio(true)
      imageView
    }

    HBox.setHgrow(spacer, Priority.ALWAYS)

    saveAsButton.getStyleClass().add("playback-control-button")
    saveAsButton.setGraphic(
      new GlyphsFactory(classOf[MaterialDesignIconView])
        .createIcon(MaterialDesignIcon.CONTENT_SAVE, "2em")
    )
    saveAsButton.onAction = handle {
      ContentNode.promptSave(getScene().getWindow(), link)
    }

    zoomButton.getStyleClass().add("playback-control-button")
    zoomButton.setGraphic(
      new GlyphsFactory(classOf[MaterialDesignIconView])
        .createIcon(MaterialDesignIcon.MAGNIFY, "2em")
    )
    zoomButton.onAction = handle {
      contentNode match {
        case iv: ImageView => {
          iv.setFitWidth(iv.getImage().width())
          iv.setFitHeight(iv.getImage().height())
        }
        case mv: MediaView => {
          mv.setFitWidth(mv.getMediaPlayer().getMedia().width().toDouble)
          mv.setFitHeight(mv.getMediaPlayer().getMedia().height().toDouble)
        }
        case _ =>
      }
    }

    controlPanel.getChildren().addAll(spacer, zoomButton, saveAsButton)

    controlPanel.setAlignment(Pos.CENTER_LEFT)
    getChildren().addAll(contentNode)

    contentNode match {
      case iv: ImageView => {
        if (!iv.getImage().isError) getChildren().addAll(controlPanel)
        else {
          iv.setImage(
            new Image(classOf[LinkPackNavigator].getResourceAsStream(s"/image-load-fail.png"))
          )
        }
      }
      case _ => getChildren().addAll(controlPanel)
    }

    def contentWidth(): Double = {
      contentNode match {
        case iv: ImageView => iv.getImage.width.doubleValue
        case mv: MediaView => mv.getMediaPlayer().getMedia().width.doubleValue
        case _             => 500
      }
    }

    def contentHeight(): Double = {
      contentNode match {
        case iv: ImageView => iv.getImage.height.doubleValue
        case mv: MediaView => mv.getMediaPlayer().getMedia().height.doubleValue
        case _             => 500
      }
    }

    def setContentPrefWidth(prefWidth: Double): Unit = {
      contentNode match {
        case iv: ImageView => iv.setFitWidth(prefWidth)
        case mv: MediaView => mv.setFitWidth(prefWidth)
        case _             => ()
      }
    }

    def setContentPrefHeight(prefHeight: Double): Unit = {
      contentNode match {
        case iv: ImageView => iv.setFitHeight(prefHeight)
        case mv: MediaView => mv.setFitHeight(prefHeight)
        case _             => ()
      }
    }
  }

  object ContentNode {

    private[this] val saveFileChooser = new FileChooser()

    def promptSave(parent: Window, link: Link): Unit = {

      logger.debug(s"Saving link content: ${ link.source }")

      saveFileChooser.setTitle("Save Link")
      saveFileChooser.setInitialFileName(link.source.drop(link.source.lastIndexOf('/') + 1))

      val selectedFile = saveFileChooser.showSaveDialog(parent)

      Option(selectedFile).foreach { f =>
        saveFileChooser.setInitialDirectory(f.getParentFile())
        f.toScala.write(link.content)(Seq(StandardOpenOption.CREATE))
      }
    }
  }
}
