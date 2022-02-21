package ch.obermuhlner.starmap.projection

import kotlin.math.*

/**
 * https://mathworld.wolfram.com/StereographicProjection.html
 * with projectionRa0 = 0.0 and prjectionDe0 = 0.0
 */
class EquatorialStereographicProjection : Projection {
    override fun toXY(ra: Double, de: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double> {
        return toXY(ra-ra0, de-de0, zoomFactor)
    }

    fun toXY(ra: Double, de: Double, zoomFactor: Double): Pair<Double, Double> {
        val sinDe = sin(de)
        val cosDe = cos(de)
        val k = 2 * zoomFactor / (1 + cosDe* cos(ra))
        val x = k * cosDe * sin(ra)
        val y = k * (sinDe)
        return Pair(x, y)
    }

    override fun toRaDe(x: Double, y: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double> {
        val (ra, de) = toRaDe(x, y, zoomFactor)
        return Pair(ra+ra0, de+de0)
    }

    fun toRaDe(x: Double, y: Double, zoomFactor: Double): Pair<Double, Double> {
        val r = sqrt(x*x + y*y)
        var c = 2 * atan2(r, 2*zoomFactor)
        val de = asin((y * sin(c)) / r )
        val ra = atan2(x * sin(c), r * cos(c))
        return Pair(ra, de)
    }
}