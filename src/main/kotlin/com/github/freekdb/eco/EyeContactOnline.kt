package com.github.freekdb.eco

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.text.DecimalFormat
import javax.swing.AbstractAction
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JRootPane
import javax.swing.KeyStroke
import javax.swing.SwingWorker
import javax.swing.WindowConstants
import kotlin.math.abs
import kotlin.system.exitProcess

/*
In progress:
- Add scaling!
  => Shortcuts Ctrl-plus and Ctrl-minus.

Improvements:
- Transparent window while selecting source?
  => https://stackoverflow.com/questions/14927980/how-to-make-a-transparent-jframe-but-keep-everything-else-the-same
- Remove window title bar and/or allow moving the window very far up?
  => https://stackoverflow.com/questions/2503659/how-do-i-move-a-java-jframe-partially-off-my-linux-desktop

Done:
✓ Change source and destination areas with two modes:
  + Select the source area.
  + Select the destination area and start duplicating those pixels.
✓ Monitor update frequency (not necessary: cpu usage).
*/

fun main() {
    EyeContactOnline().launchApplication()
}

class EyeContactOnline {
    private val frameBaseTitle = "Eye contact online 0.0.6"
    private val frameTitleSourceSelection = "$frameBaseTitle: select source area and press \"D\""
    private val fpsFormat = DecimalFormat("#.##")
    private val updateDelay = 20

    private val frame = JFrame(frameTitleSourceSelection)
    private val duplicationPanel = DuplicationPanel()
    private val screenRobot = Robot()

    private var sourceSelectionMode = true
    private var sourceRectangle = Rectangle(1920 / 2, 900, 600, 200)
    private var destinationRectangle = Rectangle(800, 600, 400, 280)
    private var scale = 1.0
    private var showFramesPerSecond = false

    fun launchApplication() {
        frame.bounds = sourceRectangle
        frame.isUndecorated = true
        frame.rootPane.windowDecorationStyle = JRootPane.INFORMATION_DIALOG
        frame.defaultCloseOperation = WindowConstants.EXIT_ON_CLOSE
        frame.contentPane.add(duplicationPanel, BorderLayout.CENTER)
        frame.isAlwaysOnTop = true
        frame.isVisible = true

        val duplicationWorker = object : SwingWorker<Void?, Void?>() {
            override fun doInBackground(): Void? {
                while (true) {
                    screenRobot.delay(updateDelay)
                    updateDuplicatedPixels()
                }
            }
        }

        duplicationWorker.execute()
    }

    fun updateDuplicatedPixels() {
        frame.repaint()

        if (!sourceSelectionMode) {
            if (showFramesPerSecond) {
                frame.title = "$frameBaseTitle (fps: ${fpsFormat.format(duplicationPanel.framesPerSecond)}) ${duplicationPanel.frameCounter}"
            } else {
                // It seems that updating the frame title helps the application to run smoothly?!?
                frame.title = frameBaseTitle
            }
        }
    }

    inner class DuplicationPanel : JPanel() {
        internal var frameCounter = 0
        internal var framesPerSecond = 0.0
        private var previousTimeMilliseconds = 0L
        private var previousFrameCounter =  0

        init {
            preferredSize = Dimension(sourceRectangle.width, sourceRectangle.height)

            registerKeyAction(KeyEvent.VK_S, "select source") { selectSourceMode() }
            registerKeyAction(KeyEvent.VK_D, "select destination") { selectDestinationMode() }
            registerKeyAction(KeyEvent.VK_F, "show fps") { toggleFramesPerSecond() }
            registerKeyAction(KeyEvent.VK_ESCAPE, "escape") { handleEscape() }
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)

            if (!sourceSelectionMode) {
                val sourceCapture = screenRobot.createScreenCapture(sourceRectangle)

                if (abs(scale - 1.0) > 0.1) {
                    // Scale image...
                    val w = sourceCapture.width
                    val h = sourceCapture.height
                    var scaledCapture = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                    val at = AffineTransform()
                    at.scale(scale, scale)
                    val scaleOp = AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR)
                    scaledCapture = scaleOp.filter(sourceCapture, scaledCapture)
                    // Scale image...

                    graphics.drawImage(scaledCapture, 0, 0, null)
                } else {
                    graphics.drawImage(sourceCapture, 0, 0, null)
                }

                updateCounters()
            }
        }

        private fun updateCounters() {
            frameCounter++

            val timeMilliseconds = System.currentTimeMillis()
            val timeDifferenceSeconds = (timeMilliseconds - previousTimeMilliseconds) / 1000.0
            if (timeDifferenceSeconds >= 1.0) {
                framesPerSecond = (frameCounter - previousFrameCounter) / timeDifferenceSeconds
                previousTimeMilliseconds = timeMilliseconds
                previousFrameCounter = frameCounter
            }
        }

        private fun registerKeyAction(keyCode: Int, actionKey: String, keyModifiers: Int = 0,
                                      action: (ActionEvent) -> Unit) {

            inputMap.put(KeyStroke.getKeyStroke(keyCode, keyModifiers), actionKey)

            actionMap.put(actionKey, object : AbstractAction(actionKey) {
                override fun actionPerformed(keyEvent: ActionEvent) {
                    action(keyEvent)
                }
            })
        }

        private fun selectSourceMode() {
            destinationRectangle = frame.bounds
            frame.bounds = sourceRectangle

            sourceSelectionMode = true
            frame.title = frameTitleSourceSelection
        }

        private fun selectDestinationMode() {
            sourceRectangle = frame.bounds
            destinationRectangle.width = (sourceRectangle.width * scale).toInt() + 10
            destinationRectangle.height = (sourceRectangle.height * scale).toInt() + 32
            frame.bounds = destinationRectangle

            sourceSelectionMode = false
            frame.title = frameBaseTitle
        }

        private fun toggleFramesPerSecond() {
            showFramesPerSecond = !showFramesPerSecond
            frame.title = frameBaseTitle
        }

        private fun handleEscape() {
            exitProcess(0)
        }
    }
}