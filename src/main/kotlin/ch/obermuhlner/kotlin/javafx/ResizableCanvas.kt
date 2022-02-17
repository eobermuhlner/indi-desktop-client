package ch.obermuhlner.kotlin.javafx

import javafx.scene.canvas.Canvas


class ResizableCanvas : Canvas(100.0, 100.0) {

    override fun isResizable(): Boolean {
        return true
    }

    override fun maxHeight(width: Double): Double {
        return 10000.0
    }

    override fun maxWidth(height: Double): Double {
        return 10000.0
    }

    override fun minWidth(height: Double): Double {
        return 1.0
    }

    override fun minHeight(width: Double): Double {
        return 1.0
    }

    override fun prefWidth(height: Double): Double {
        return 10000.0
    }

    override fun prefHeight(height: Double): Double {
        return 10000.0
    }

    override fun resize(width: Double, height: Double) {
        this.width = width
        this.height = height
    }
}