package com.github.freekdb.eco

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
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
- Resize source when resizing destination. What happens when you move the top left corner: move the source area too?
- Resize destination when zooming in or out.

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
✓ Add scaling with keyboard shortcuts Ctrl-plus and Ctrl-minus.
✓ Position destination area centered at top of the screen initially.
*/

fun main() {
    EyeContactOnline().launchApplication()
}

class EyeContactOnline {
    private val frameBaseTitle = "Eye contact online 0.0.6"
    private val frameTitleSourceSelection = "$frameBaseTitle: select source area and press \"D\""
    private val fpsFormat = DecimalFormat("#.##")
    private val updateDelay = 20
    private val screenWidth = Toolkit.getDefaultToolkit().screenSize.width
    private val zoomLevels = listOf(0.25, 0.33, 0.5, 0.67, 0.8, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0)

    private var zoomLevel = 1.0
    private var showFramesPerSecond = false
    private var sourceSelectionMode = true
    private var sourceRectangle = Rectangle(screenWidth / 2, 900, 600, 200)
    private var destinationRectangle = Rectangle(Point((screenWidth - sourceRectangle.width) / 2, 0),
                                                 destinationSizeFromSource())

    private val frame = JFrame(frameTitleSourceSelection)
    private val duplicationPanel = DuplicationPanel()
    private val screenRobot = Robot()

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

    private fun destinationSizeFromSource(): Dimension =
        Dimension((sourceRectangle.width * zoomLevel).toInt() + 10,
                  (sourceRectangle.height * zoomLevel).toInt() + 32)

    private fun updateDuplicatedPixels() {
        frame.repaint()

        if (!sourceSelectionMode) {
            if (showFramesPerSecond) {
                frame.title = "$frameBaseTitle (fps: ${fpsFormat.format(duplicationPanel.framesPerSecond)}) " +
                              "${duplicationPanel.frameCounter}"
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
        private var previousFrameCounter = 0

        init {
            preferredSize = Dimension(sourceRectangle.width, sourceRectangle.height)

            val ctrlModifier = InputEvent.CTRL_DOWN_MASK

            registerKeyAction(KeyEvent.VK_S, "select source") { selectSourceMode() }
            registerKeyAction(KeyEvent.VK_D, "select destination") { selectDestinationMode() }
            registerKeyAction(KeyEvent.VK_EQUALS, "zoom in ctrl-=", keyModifiers = ctrlModifier) { zoomIn() }
            registerKeyAction(KeyEvent.VK_ADD, "zoom in keypad", keyModifiers = ctrlModifier) { zoomIn() }
            registerKeyAction(KeyEvent.VK_MINUS, "zoom out", keyModifiers = ctrlModifier) { zoomOut() }
            registerKeyAction(KeyEvent.VK_SUBTRACT, "zoom out keypad", keyModifiers = ctrlModifier) { zoomOut() }
            registerKeyAction(KeyEvent.VK_F, "show fps") { toggleFramesPerSecond() }
            registerKeyAction(KeyEvent.VK_ESCAPE, "escape") { handleEscape() }
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)

            if (!sourceSelectionMode) {
                val sourceCapture = screenRobot.createScreenCapture(sourceRectangle)

                graphics.drawImage(scaleImage(sourceCapture), 0, 0, null)

                updateCounters()
            }
        }

        private fun scaleImage(sourceImage: BufferedImage): BufferedImage {
            val zoomActive = abs(zoomLevel - 1.0) >= 0.1

            return if (zoomActive) {
                val transform = AffineTransform.getScaleInstance(zoomLevel, zoomLevel)
                val scaleOperation = AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
                val newImage = BufferedImage(sourceImage.width, sourceImage.height, BufferedImage.TYPE_INT_ARGB)

                scaleOperation.filter(sourceImage, newImage)
            } else {
                sourceImage
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
            destinationRectangle.size = destinationSizeFromSource()
            frame.bounds = destinationRectangle

            sourceSelectionMode = false
            frame.title = frameBaseTitle
        }

        private fun zoomIn() {
            zoom(1)
        }

        private fun zoomOut() {
            zoom(-1)
        }

        private fun zoom(step: Int) {
            if (zoomLevels.contains(zoomLevel)) {
                zoomLevel = zoomLevels[(zoomLevels.indexOf(zoomLevel) + step).coerceIn(zoomLevels.indices)]
            }
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
