package com.mattibal.akkatextindexer

import java.awt.event.{ActionEvent, ActionListener, WindowEvent, WindowListener}
import javax.swing._

import akka.actor.{Props, Actor}
import com.mattibal.akkatextindexer.TextSearcherNode.{SearchWord, StartIndexing}
import java.io.File


class SwingGuiActor extends Actor {

  val frame = new JFrame
  val searchResultTextArea = new JTextArea
  val indexingStatus = new JLabel
  val searchButton = new JButton

  var dirsScanned: Long = 0
  var filesIndexed: Long = 0
  var wordsInIndex: Long = 0

  /**
   * Initialize the Swing GUI when the actor is started
   */
  override def preStart() {

    java.awt.EventQueue.invokeAndWait(new Runnable {
      override def run(): Unit = {

        val dirToIndexLabel = new JLabel
        val dirPathTextField = new JTextField
        val browseButton = new JButton
        val startButton = new JButton
        val pauseButton = new JButton
        val stopButton = new JButton
        val searchWordLabel = new JLabel
        val searchTextField = new JTextField
        val searchResultScrollPane = new JScrollPane

        frame.setTitle("Akka Text Indexer")
        stopButton.setText("Stop")
        searchWordLabel.setText("Search word:")
        searchTextField.setText("concurrency")
        searchButton.setText("Search")
        dirToIndexLabel.setText("Dir to index:")
        dirPathTextField.setText("/")
        browseButton.setText("Browse...")
        startButton.setText("Start")
        pauseButton.setText("Pause")


        browseButton.addActionListener(new ActionListener(){
          override def actionPerformed(e: ActionEvent): Unit = {
            val fileChooser = new JFileChooser()
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY)
            val retVal = fileChooser.showOpenDialog(frame)
            if(retVal == JFileChooser.APPROVE_OPTION){
              dirPathTextField.setText(fileChooser.getSelectedFile.getAbsolutePath)
            }
          }
        })


        startButton.addActionListener(new ActionListener {
          override def actionPerformed(e: ActionEvent): Unit = {
            startButton.setEnabled(false)
            context.parent ! StartIndexing(new File(dirPathTextField.getText))
          }
        })


        searchButton.addActionListener(new ActionListener {
          override def actionPerformed(e: ActionEvent): Unit = {
            val word = searchTextField.getText
            if (word.length > 0) {
              searchButton.setEnabled(false)
              context.parent ! SearchWord(word)
            }
          }
        })


        // Set Swing layouts...
        searchResultScrollPane.setViewportView(searchResultTextArea)
        val layout = new GroupLayout(frame.getContentPane())
        frame.getContentPane().setLayout(layout)
        layout.setHorizontalGroup(
          layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
            .addGap(15, 15, 15)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
            .addComponent(startButton)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(pauseButton)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(stopButton)
            .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
            .addComponent(indexingStatus)
            .addContainerGap(500, Short.MaxValue))
            .addGroup(GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
            .addComponent(searchResultScrollPane, GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
            .addComponent(searchWordLabel)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(searchTextField)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(searchButton))
            .addGroup(layout.createSequentialGroup()
            .addComponent(dirToIndexLabel)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(dirPathTextField)
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(browseButton)))
            .addGap(16, 16, 16))))
        )
        layout.setVerticalGroup(
          layout.createParallelGroup(GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
            .addGap(9, 9, 9)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(dirToIndexLabel)
            .addComponent(dirPathTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(browseButton))
            .addGap(18, 18, 18)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(indexingStatus)
            .addComponent(startButton)
            .addComponent(pauseButton)
            .addComponent(stopButton))
            .addGap(18, 18, 18)
            .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
            .addComponent(searchWordLabel)
            .addComponent(searchTextField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
            .addComponent(searchButton))
            .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(searchResultScrollPane, GroupLayout.PREFERRED_SIZE, 318, GroupLayout.PREFERRED_SIZE)
            .addContainerGap(25, Short.MaxValue))
        )


        // I don't use EXIT_ON_CLOSE because I want to properly stop the Akka actors
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)

        frame.addWindowListener(new WindowListener {

          override def windowClosing(e: WindowEvent): Unit = context.stop(self)

          override def windowClosed(e: WindowEvent): Unit = Unit
          override def windowOpened(e: WindowEvent): Unit = Unit
          override def windowDeiconified(e: WindowEvent): Unit = Unit
          override def windowActivated(e: WindowEvent): Unit = Unit
          override def windowDeactivated(e: WindowEvent): Unit = Unit
          override def windowIconified(e: WindowEvent): Unit = Unit
        })


        frame.pack()
        frame.setVisible(true)
      }
    })

    updateIndexingStatusText
  }



  def receive = {

    case TextSearcherNode.FilesContainingWord(filePaths, word) =>
      java.awt.EventQueue.invokeAndWait(new Runnable {
        override def run(): Unit = {
          searchResultTextArea.setText(
            filePaths match {
              case None => "No files found containing word \"" + word + "\"."
              case Some(paths) => paths.size + " files found containing word \"" + word + "\":\n\n" + paths.reduce(_+"\n"+_)
            }
          )
          searchButton.setEnabled(true)
        }
      })

    case TextSearcherNode.ScanningStatusUpdate(dirs, files) => {
      dirsScanned = dirs
      filesIndexed = files
      updateIndexingStatusText
    }

    case TextSearcherNode.NumWordsInIndex(words) => {
      wordsInIndex = words
      updateIndexingStatusText
    }
  }


  def updateIndexingStatusText {
    java.awt.EventQueue.invokeAndWait(new Runnable {
      override def run(): Unit = {
        indexingStatus.setText(
          dirsScanned + " dirs scanned, " + filesIndexed + " text files indexed, " + wordsInIndex + " words in index"
        )
      }
    })
  }

}
