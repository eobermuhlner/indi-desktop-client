package ch.obermuhlner.starmap.projection

import kotlin.math.*

class StereographicProjection : Projection {
    var projectionCenterRa: Double = 0.0
        set(value) {
            field = value
            sinProjectionCenterRa = sin(value)
        }
        get() = field

    var projectionCenterDe: Double = 0.0
        set(value) {
            field = value
            sinProjectionCenterDe = sin(value)
            cosProjectionCenterDe = cos(value)
        }
        get() = field


    private var sinProjectionCenterRa: Double = 0.0
    private var sinProjectionCenterDe: Double = 0.0
    private var cosProjectionCenterDe: Double = 1.0

    override fun toXY(ra: Double, de: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double> {
        val sinDe = sin(de)
        val cosDe = cos(de)
        val k = 2*zoomFactor / (1 + sinProjectionCenterDe *sinDe + cosProjectionCenterDe *cosDe*cos(ra - projectionCenterRa))
        val x = k * cosDe * sin(ra - projectionCenterRa)
        val y = k * (cosProjectionCenterDe * sinDe + sinProjectionCenterDe * cosDe * cos(ra - projectionCenterRa))
        return Pair(x, y)
    }

    override fun toRaDe(x: Double, y: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double> {
        val r = sqrt(x*x + y*y)
        var c = 2 * atan2(r, 2*zoomFactor)
        val de = asin(cos(c) * sinProjectionCenterDe + (y * sin(c) * cosProjectionCenterDe) / r )
        val ra = projectionCenterRa + atan2(x * sin(c), r * cosProjectionCenterDe * cos(c) - y * sinProjectionCenterDe * sin(c))
        return Pair(ra, de)
    }
}