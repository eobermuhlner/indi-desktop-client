package ch.obermuhlner.starmap.projection

interface Projection {
    fun toXY(ra: Double, de: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double>
    fun toRaDe(x: Double, y: Double, ra0: Double, de0: Double, zoomFactor: Double): Pair<Double, Double>
}
