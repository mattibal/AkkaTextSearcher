package com.mattibal.akkatextindexer

import akka.actor.{Terminated, Props, Actor}
import akka.routing.{RoundRobinRoutingLogic, Router, ActorRefRoutee}
import com.mattibal.akkatextindexer.TextSearcherNode.{NumWordsInIndex, FilesContainingWord, SearchWord}
import com.mattibal.akkatextindexer.WordIndex.{SendNumWordsInIndex, AddWordToIndex}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

/**
 * This actor has the responsability to create the index data structure and to route messages of operations on
 * the index to the pool of worker actors
 */
class WordIndexMaster extends Actor {

  /**
   * This maps the word to a set of strings that identifies file paths
   * The inner TrieMap is should actually be a Set, but since it still doesn't exist in Scala, I used a Map...
   */
  val index = new TrieMap[String, TrieMap[String, Boolean]]

  /**
   * True if has been scheduled a send of a status update to the parent (so to the GUI)
   */
  var statusUpdateScheduled = false

  var router = {
    val routees = Vector.fill(8){
      val routee = context.actorOf(Props(classOf[WordIndexWorker], index))
      context.watch(routee) // watch if the child worker actor terminates
      ActorRefRoutee(routee)
    }
    Router(RoundRobinRoutingLogic(), routees)
  }


  def receive = {
    case msg: AddWordToIndex => {
      router.route(msg, sender())
      if (!statusUpdateScheduled) {
        import context.dispatcher
        context.system.scheduler.scheduleOnce(10 milliseconds, self, SendNumWordsInIndex)
        statusUpdateScheduled = true
      }
    }

    case msg: SearchWord =>
      router.route(msg, sender())

    case Terminated(terminatedActor) => {
      // Handle a worker actor termination (caused for example by an exception)
      router = router.removeRoutee(terminatedActor)
      val newActor = context.actorOf(Props(classOf[WordIndexWorker], index))
      context.watch(newActor)
      router = router.addRoutee(newActor)
    }

    case SendNumWordsInIndex => {
      context.parent ! NumWordsInIndex(index.size)
      statusUpdateScheduled = false
    }
  }
}



object WordIndex {

  case class AddWordToIndex(word: String, filePath: String)

  case object SendNumWordsInIndex

}




class WordIndexWorker(index: TrieMap[String, TrieMap[String, Boolean]]) extends Actor {

  def receive = {
    case AddWordToIndex(word, filePath) =>
      val filesContaining = index.getOrElseUpdate(word, new TrieMap[String, Boolean])
      filesContaining.put(filePath, true)
    case SearchWord(word) =>
      val filesSet = index.get(word).map(_.readOnlySnapshot().keySet)
      sender ! FilesContainingWord(filesSet, word)
  }

}