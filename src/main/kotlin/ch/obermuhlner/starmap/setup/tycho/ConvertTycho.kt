package ch.obermuhlner.starmap.setup.tycho

import ch.obermuhlner.starmap.data.StarData
import java.io.*

/**
 * Downloaded from:
 * http://cdsarc.u-strasbg.fr/viz-bin/cat/I/259#/browse
 *
 * Unpack the tyc2.dat*.gz
 * and concatenate the text files into tyc2.dat
 *
 *  153-164  F12.8   deg     RAdeg    Observed Tycho-2 Right Ascension, ICRS
 *  166-177  F12.8   deg     DEdeg    Observed Tycho-2 Declination, ICRS
 *  111-116  F6.3    mag     BTmag    [2.183,16.581]? Tycho-2 BT magnitude (7)
 *  124-129  F6.3    mag     VTmag    [1.905,15.193]? Tycho-2 VT magnitude (7)
 *  143-148  I6      ---     HIP      [1,120404]? Hipparcos number
 *
 * V   = VT - 0.090*(BT-VT)
 * B-V = 0.850*(BT-VT
 */
object ConvertTycho {


    @JvmStatic
    fun main(args: Array<String>) {
        convert(File("stardata/tycho2/tyc2.dat"))
    }

    private fun convert(file: File) {
        val hipparchosStars = StarData.readStars(File("hip.jbin"))

        val reader = BufferedReader(FileReader(file))
        var writer = DataOutputStream(FileOutputStream(File("tycho2.jbin")))
        var index = 0
        var totalDelta = 0
        var line = reader.readLine()
        while (line != null) {
            val hip = line.substring(142, 148).trim().toIntOrNull()
            val raDeg = line.substring(152, 164).trim().toDoubleOrNull() ?: Double.NaN
            val deDeg = line.substring(165, 177).trim().toDoubleOrNull() ?: Double.NaN
            val btMag = line.substring(110, 116).trim().toFloatOrNull() ?: Float.NaN
            val vtMag = line.substring(124, 129).trim().toFloatOrNull() ?: Float.NaN

            val ra = Math.toRadians(raDeg)
            val de = Math.toRadians(deDeg)

            val mag = if (btMag.isNaN()) {
                vtMag
            } else if (vtMag.isNaN()) {
                btMag
            } else {
                (btMag - 0.090*(btMag-vtMag)).toFloat()
            }
            val bv = if (btMag.isNaN()) {
                Float.NaN
            } else if (vtMag.isNaN()) {
                Float.NaN
            } else {
                (0.850*(btMag-vtMag)).toFloat()
            }

            if (hip != null) {
                // compare with hip
                val hipStar = hipparchosStars[hip]!!
                val deltaRa = ra - hipStar.ra
                val deltaDe = de - hipStar.de
                val deltaMag = mag - hipStar.magnitude
                val deltaColor = bv - hipStar.color
                println("HIP $hip deltaRa=$deltaRa deltaDe=$deltaDe deltaMag=$deltaMag deltaColor=$deltaColor")
            }

            writer.writeDouble(ra)
            writer.writeDouble(de)
            writer.writeFloat(mag)
            writer.writeFloat(bv)

            line = reader.readLine()
            index++
        }

        println("TOTAL empty star slots $totalDelta")
        writer.close()
    }
}
