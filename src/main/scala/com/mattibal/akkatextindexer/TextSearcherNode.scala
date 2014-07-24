package com.mattibal.akkatextindexer

import java.io.File

import akka.actor._
import com.mattibal.akkatextindexer.TextSearcherNode._

import scala.collection.concurrent.TrieMap


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

  override def preStart(): Unit = {
    // Create some initial actors
    guiActor = context.actorOf(Props[SwingGuiActor], "swingGuiActor")
    currIndex = context.actorOf(Props(new WordIndexMaster()))
    directoryScanner = context.actorOf(Props(new DirectoryScannerMaster(currIndex)))
  }

  def receive = {
      case StartIndexing(directory) =>
        println("starting indexing")
        currIndex = context.actorOf(Props(new WordIndexMaster())) // throw away the old index and create a new one
        directoryScanner = context.actorOf(Props(new DirectoryScannerMaster(currIndex)))
        directoryScanner ! DirectoryScanner.ScanDirectory(directory)
      case msg: SearchWord =>
        currIndex ! msg
      case msg: FilesContainingWord =>
        guiActor ! msg
      case msg: ScanningStatusUpdate =>
        guiActor ! msg
      case msg: NumWordsInIndex =>
        guiActor ! msg
  }

}




object TextSearcherNode {

  /**
   * Send this message to the TextSearcherNode actor to start indexing the specified directory or subdirectory
   */
  case class StartIndexing(directory: File)
  case object PauseIndexing
  case object StopIndexing
  case object IndexingCompleted

  case class SearchWord(word: String)
  case class FilesContainingWord(filePaths: Option[collection.Set[String]], word: String)

  case class ScanningStatusUpdate(numDirectoriesScanned: Long, numFilesIndexed: Long)
  case class NumWordsInIndex(numWords: Long)
}
