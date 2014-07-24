package com.mattibal.akkatextindexer

import java.io.File

import akka.actor._
import com.mattibal.akkatextindexer.TextSearcherNode._
import scala.concurrent.duration._


/**
 * This is the application main, it just creates an Akka ActorSystem and launches a TextSearcherNode actor
 */
object TextSearcherNodeApp extends App {

  akka.Main.main(Array(classOf[TextSearcherNode].getName))
}



/**
 * This is the root actor that should run on every node (computer/JVM) where you want to run the AkkaTextSearcher app.
 * This is a sort of "singleton" that supervises all the process of indexing and searching.
 */
class TextSearcherNode extends Actor {

  var guiActor : ActorRef = null
  var directoryScanner : ActorRef = null
  var currIndex : ActorRef = null

  var reindexerScheduler: Option[Cancellable] = None

  override def preStart(): Unit = {
    // Create some initial actors
    guiActor = context.actorOf(Props[SwingGuiActor], "swingGuiActor")
    createIndexActor()
  }

  def createScannerActor() {
    directoryScanner = context.actorOf(Props(new DirectoryScannerMaster(currIndex)))
  }

  def createIndexActor() {
    currIndex = context.actorOf(Props(new WordIndexMaster()))
  }


  def receive = {

      case StartIndexing(directory) => {
        context.stop(currIndex)
        createIndexActor()
        createScannerActor()
        directoryScanner ! DirectoryScanner.ScanDirectory(directory)

        // Schedule a reindexing every a certain time interval
        import context.dispatcher
        reindexerScheduler = Some(context.system.scheduler.scheduleOnce(5 minutes, self, StartIndexing(directory)))
      }

      case msg: SearchWord =>
        currIndex ! msg
      case msg: FilesContainingWord =>
        guiActor ! msg
      case msg: ScanningStatusUpdate =>
        guiActor ! msg
      case msg: NumWordsInIndex =>
        guiActor ! msg
      case msg : IndexingPausingControl =>
        directoryScanner ! msg

      case StopIndexing => {
        context.stop(directoryScanner)
        reindexerScheduler.foreach(_.cancel())
      }
  }

}




object TextSearcherNode {

  /**
   * Send this message to the TextSearcherNode actor to start indexing the specified directory or subdirectory
   */
  case class StartIndexing(directory: File)
  case object StopIndexing
  case object IndexingCompleted

  case class SearchWord(word: String)
  case class FilesContainingWord(filePaths: Option[collection.Set[String]], word: String)

  case class ScanningStatusUpdate(numDirectoriesScanned: Long, numFilesIndexed: Long)
  case class NumWordsInIndex(numWords: Long)

  trait IndexingPausingControl
  case object PauseIndexing extends IndexingPausingControl
  case object ResumeIndexing extends IndexingPausingControl
}
