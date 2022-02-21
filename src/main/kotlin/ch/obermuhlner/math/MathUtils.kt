package ch.obermuhlner.math

import java.lang.Math.PI

fun wrap(value: Double, min: Double, max: Double): Double {
    return if (value > max) {
        value - max
    } else if (value < min) {
        value + min
    } else {
        value
    }
}

fun clamp(value: Double, min: Double, max: Double): Double {
    return if (value < min) {
        min
    } else if (value > max) {
        max
    } else {
        value
    }
}

fun wrapRadiansRa(ra: Double) = wrap(ra, 0.0, PI)

fun clampRadiansDe(de: Double) = clamp(de, -PI, PI)