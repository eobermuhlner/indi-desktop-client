package ch.obermuhlner.starmap.projection

import ch.obermuhlner.math.clampRadiansDe
import ch.obermuhlner.math.wrapRadiansRa
import java.lang.Math.PI

class TextureProjection : Projection {
    override fun toXY(ra: Double, de: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double> {
        val x = (ra - ra0) * zoomFactor
        val y = (de - de0) * zoomFactor
        return Pair(x, y)
    }

    override fun toRaDe(x: Double, y: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double> {
        val ra = wrapRadiansRa(x/zoomFactor * 2*PI + ra0)
        val de = clampRadiansDe(y/zoomFactor * PI + de0)
        return Pair(ra, de)
    }
}