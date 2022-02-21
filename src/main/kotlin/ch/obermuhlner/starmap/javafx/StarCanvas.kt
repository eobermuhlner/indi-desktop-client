package ch.obermuhlner.starmap.javafx

import ch.obermuhlner.kotlin.javafx.ResizableCanvas
import ch.obermuhlner.starmap.data.Star
import ch.obermuhlner.starmap.projection.Projection
import ch.obermuhlner.starmap.projection.TextureProjection
import javafx.scene.paint.Color

class StarCanvas(
    val hipparcosStars: List<Star?>,
    val constellations: Map<String, List<Pair<Star, Star>>>,
    val starNames: Map<Star, String>,
    var projection: Projection = TextureProjection()
) : ResizableCanvas() {

    var ra0: Double = 0.0
    var de0: Double = 0.0
    var zoomFactor = 1.0

    fun draw(limitMagnitude: Double = Math.log10(zoomFactor)+4.0, labelDarkestMagnitude: Double = limitMagnitude+4.0, labelMinZoomFactor: Double = 10.0) {
        val gc = graphicsContext2D

        fun toX(x: Double) = x + width/2
        fun toY(y: Double) = height - (y + height/2)

        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, width, height)

        gc.stroke = Color.BLUE
        for (constellation in constellations) {
            for (starPair in constellation.value) {
                val (x1, y1) = toXY(starPair.first.ra, starPair.first.de)
                val (x2, y2) = toXY(starPair.second.ra, starPair.second.de)
                gc.strokeLine(toX(x1), toY(y1), toX(x2), toY(y2))
            }
        }

        gc.fill = Color.WHITE
        for (star in hipparcosStars) {
            if (star != null) {
                val (xx, yy) = toXY(star.ra, star.de)

                val x = toX(xx)
                val y = toY(yy)

                if (x > 0 && x < width && y > 0 && y < height) {
                    val radius = -star.magnitude + limitMagnitude
                    if (radius > 0) {
                        gc.fillOval(x - radius / 2, y - radius / 2, radius, radius)
//                        if (zoomFactor > labelMinZoomFactor /*&& star.magnitude < labelDarkestMagnitude*/) {
//                            //gc.strokeText(star.magnitude.toString(), x, y)
//                            val name = starNames[star]
//                            if (name != null) {
//                                gc.strokeText(name, x, y)
//                            }
//                        }
                    }
                }
            }
        }
    }

    private fun toXY(ra: Double, de: Double) = projection.toXY(ra, de, ra0, de0, zoomFactor)

    private fun toRaDe(x: Double, y: Double) = projection.toRaDe(x, y, ra0, de0, zoomFactor)

    /*
    Convert b-v color into RGB color:
    https://stackoverflow.com/questions/21977786/star-b-v-color-index-to-apparent-rgb-color#:~:text=It's%20a%20number%20astronomers%20assign,white%2Forange%20stars%20in%20between.

    Convert b-v to Temperature Kelvin:
    var t = 4600 * ((1 / ((0.92 * bv) + 1.7)) +(1 / ((0.92 * bv) + 0.62)) );




    def bv2rgb(bv):
  if bv < -0.4: bv = -0.4
  if bv > 2.0: bv = 2.0
  if bv >= -0.40 and bv < 0.00:
    t = (bv + 0.40) / (0.00 + 0.40)
    r = 0.61 + 0.11 * t + 0.1 * t * t
    g = 0.70 + 0.07 * t + 0.1 * t * t
    b = 1.0
  elif bv >= 0.00 and bv < 0.40:
    t = (bv - 0.00) / (0.40 - 0.00)
    r = 0.83 + (0.17 * t)
    g = 0.87 + (0.11 * t)
    b = 1.0
  elif bv >= 0.40 and bv < 1.60:
    t = (bv - 0.40) / (1.60 - 0.40)
    r = 1.0
    g = 0.98 - 0.16 * t
  else:
    t = (bv - 1.60) / (2.00 - 1.60)
    r = 1.0
    g = 0.82 - 0.5 * t * t
  if bv >= 0.40 and bv < 1.50:
    t = (bv - 0.40) / (1.50 - 0.40)
    b = 1.00 - 0.47 * t + 0.1 * t * t
  elif bv >= 1.50 and bv < 1.951:
    t = (bv - 1.50) / (1.94 - 1.50)
    b = 0.63 - 0.6 * t * t
  else:
    b = 0.0
  return (r, g, b)


     */

}