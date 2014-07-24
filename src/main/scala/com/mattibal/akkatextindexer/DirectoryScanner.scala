package com.mattibal.akkatextindexer

import java.io.{FileFilter, File}

import akka.actor._
import akka.routing.{RandomRoutingLogic, Router, ActorRefRoutee}
import com.mattibal.akkatextindexer.DirectoryScanner.{ScanDirectory, FileHasBeenIndexed, SendStatusUpdate}
import scala.concurrent.duration._


/**
 * This is the master actor that routes directory scan requests to several workers, to distribute processing
 * to multiple cores.
 *
 * Inspired by: http://doc.akka.io/docs/akka/snapshot/scala/routing.html
 */
class DirectoryScannerMaster(indexMaster: ActorRef) extends Actor {

  var router = {
    val routees = Vector.fill(8){
      val routee = context.actorOf(Props(new DirectoryScannerWorker(indexMaster)).withDispatcher("indexing-dispatcher"))
      context.watch(routee) // watch if the child worker actor terminates
      ActorRefRoutee(routee)
    }
    Router(RandomRoutingLogic(), routees) // routes to a random actor: this is probably the best way to evenly distribute
    // the load when the time for handling each message is variable and unknown
  }

  /**
   * A counter of all directories scanned, used to be displayed on the GUI
   */
  var scannedDirs: Long = 0

  /**
   * A counter of all text files indexed (by FileIndexer actors created by the many DirectoryScannerWorker)
   */
  var indexedFiles: Long = 0

  /**
   * True if has been scheduled a send of a status update to the parent (so to the GUI)
   */
  var statusUpdateScheduled = false


  def receive = {

    case msg: ScanDirectory => {
      router.route(msg, self) // warning! this router changes the sender

      scannedDirs += 1
      scheduleStatusUpdateIfNecessary
    }

    case Terminated(terminatedActor) => // Handle a worker actor termination (caused for example by an exception)
      router = router.removeRoutee(terminatedActor)
      val newActor = context.actorOf(Props(new DirectoryScannerWorker(indexMaster)).withDispatcher("indexing-dispatcher"))
      context.watch(newActor)
      router = router.addRoutee(newActor)

    case SendStatusUpdate => {
      context.parent ! TextSearcherNode.ScanningStatusUpdate(scannedDirs, indexedFiles)
      statusUpdateScheduled = false
    }

    case FileHasBeenIndexed => {
      indexedFiles += 1
      scheduleStatusUpdateIfNecessary
    }
  }

  /**
   * Schedule a sending of status update to the parent (so to the GUI) after an interval of time.
   *
   * I do this to don't update the GUI on every single dir or file scanned, otherwise it will create an inuseful bottleneck
   */
  def scheduleStatusUpdateIfNecessary = {
    if (!statusUpdateScheduled) {
      import context.dispatcher
      context.system.scheduler.scheduleOnce(100 milliseconds, self, SendStatusUpdate)
      statusUpdateScheduled = true
    }
  }
}




/**
 * This actor retrieves the list of files contained in the directory specified in the message.
 * For every directory contained, it tell to the master (that will reroute to one of this child actors) to scan that directory.
 * For every regular file it checks if the extension is .txt, and in that case it........
 */
class DirectoryScannerWorker(indexMaster: ActorRef) extends Actor {

  def receive = {

    case DirectoryScanner.ScanDirectory(dir) => {
      val files = dir.listFiles(new FileFilter {
        override def accept(pathname: File): Boolean = {
          if(pathname.isDirectory){
            true
          } else {
            pathname.getName.endsWith(".txt")
          }
        }
      })
      files.foreach{ file =>
        if(file.isDirectory) {
          context.parent ! DirectoryScanner.ScanDirectory(file)
        } else {
          val fileIndexer = context.actorOf(Props(new FileIndexer(file, indexMaster)).withDispatcher("indexing-dispatcher")) // this will start indexing the file
          context.watch(fileIndexer) // be notified when the file indexer actor terminates
        }
      }
    }

    case Terminated(terminatedActor) => {
      // A text file scanning has finished (successfully or unsuccessfully...)
      context.parent ! FileHasBeenIndexed
    }
  }

}





object DirectoryScanner {

  /**
   * Start the scanning of the directory specified in the constructor
   */
  case class ScanDirectory(directory: File)

  /**
   * Tell to send the status update to the parent
   */
  case object SendStatusUpdate

  case object FileHasBeenIndexed

}






