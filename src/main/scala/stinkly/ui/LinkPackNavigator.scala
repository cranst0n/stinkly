package stinkly.ui

import scala.collection.JavaConverters._
import scala.util.Try

import scalafx.Includes._

import java.nio.file.StandardOpenOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter

import javafx.application.Platform
import javafx.collections.ListChangeListener
import javafx.concurrent.Task
import javafx.geometry.{ Insets, Pos }
import javafx.scene.control.ScrollPane
import javafx.scene.image.{ Image, ImageView }
import javafx.scene.media.{ Media, MediaPlayer, MediaView }
import javafx.scene.layout._
import javafx.stage.{ FileChooser, Window }

import com.jfoenix.controls._
import com.jfoenix.effects.JFXDepthManager
import com.typesafe.scalalogging.LazyLogging

import de.jensd.fx.glyphs.GlyphsFactory
import de.jensd.fx.glyphs.materialdesignicons.{ MaterialDesignIcon, MaterialDesignIconView }

import better.files._

import stinkly.core._

class LinkPackNavigator(database: Database, rootStackPane: StackPane)
    extends BorderPane
    with LazyLogging {

  case class LinkPackButton(file: File) extends JFXButton {

    setMaxWidth(Double.MaxValue)
    getStyleClass().add("button")

    val instant    = LinkPack.instantOf(file)
    val dateString = dateTimeFormatter.format(instant.atZone(ZoneId.systemDefault()))
    setText(dateString)
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

  private[this] val dateTimeFormatter = DateTimeFormatter.ofPattern("EEEE MMMM d, K:mma")

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

    linkPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)
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
          val packButton = LinkPackButton(f)
          packButton.onAction = handle { showLinkPack(f) }
          linkPackList.getChildren().add(packButton)
        }
      }
    })
  }

  private[this] def showLinkPack(file: File): Unit = {

    logger.debug(s"Showing link pack: $file")

    val task = new Task[Unit] {
      override protected def call(): Unit = {

        Platform.runLater(new Runnable() {
          override def run(): Unit = {
            linkList.getChildren().clear()
            linkList.getChildren().add(loadingSpinner)
          }
        })

        logger.trace(s"Loading link pack: $file")
        val linkPack = LinkPack.fromFile(file)
        logger.trace(s"Finished loading link pack: $file")

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

    new Thread(task).start()
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
      sizeMe(linkPane.width())
    }

    JFXDepthManager.setDepth(card, 2)
    sizeMe(linkPane.width())

    lazy val mediaNode = ContentNode(link, () => sizeMe(linkPane.width()))

    contentContainer.getChildren().add(mediaNode)
    card.getChildren().add(contentContainer)

    getChildren().addAll(spacerLeft, card, spacerRight)

    private[this] def sizeMe(parentWidth: Double): Unit = {

      val prefWidth = ((parentWidth - 50) min mediaNode.contentWidth())

      contentContainer.maxWidthProperty.set(prefWidth)
      contentContainer.prefWidthProperty.set(prefWidth)
      contentContainer.minWidthProperty.set(prefWidth)

      mediaNode.setContentPrefWidth(prefWidth)
    }
  }

  case class ContentNode(link: Link, sizeCallback: Function0[Unit] = () => ()) extends VBox(10) {

    val controlPanel = new HBox(5)
    val playButton   = new JFXButton()
    val pauseButton  = new JFXButton()
    val stopButton   = new JFXButton()
    val progressBar  = new JFXProgressBar(0)
    val spacer       = new Region()
    val saveAsButton = new JFXButton

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
    controlPanel.getChildren().addAll(spacer, saveAsButton)

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

    def setContentPrefWidth(prefWidth: Double): Unit = {
      contentNode match {
        case iv: ImageView => iv.setFitWidth(prefWidth)
        case mv: MediaView => mv.setFitWidth(prefWidth)
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
