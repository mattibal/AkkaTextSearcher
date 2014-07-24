package com.mattibal.akkatextindexer

import java.io.File
import java.nio.CharBuffer
import java.nio.charset.Charset

import akka.actor.{ActorRef, Actor, Props}
import akka.io.File.ReadResult
import akka.io.FileSlurp

/**
 * This actor when started will begin to read a file, tokenize words, and add these to the index.
 *
 * This implementation uses a very efficient asynchronous non-blocking file IO
 */
class FileIndexer(file : File, indexMaster : ActorRef) extends Actor {

  val charBuffer = CharBuffer.allocate(1000)
  val decoder = Charset.forName("UTF-8").newDecoder
  var word = new StringBuilder

  override def preStart() {
    val path = java.nio.file.Paths.get(file.getAbsolutePath)

    // start the FileSlurp actor. It will send to myself chunks of the file of max 256 bytes
    context.actorOf(Props(classOf[FileSlurp], path, self, 256))

  }

  def receive = {
    case ReadResult(byteString, bytesRead, position) =>
      byteString.asByteBuffers.foreach(decoder.decode(_, charBuffer, false))
      readChars
    case FileSlurp.Done =>
      sendWordIfPresent
      context.stop(self)
  }

  def readChars {
    charBuffer.flip()
    while(charBuffer.hasRemaining){
      val char = charBuffer.get()
      if(char.isLetterOrDigit){
        word.append(char.toLower)
      } else {
        sendWordIfPresent
      }
    }
    if(word.length > 100){
      // The "word" is too long... I drop it!
      word = new StringBuilder
    }
  }

  def sendWordIfPresent {
    if(word.length > 0) {
      indexMaster ! WordIndex.AddWordToIndex(word.toString(), file.getAbsolutePath)
      word = new StringBuilder
    }
  }

}

