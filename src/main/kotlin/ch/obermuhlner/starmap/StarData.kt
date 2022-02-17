package ch.obermuhlner.starmap

import java.io.*
import java.io.FileReader as FileReader

data class Star(
    val ra: Double,
    val de: Double,
    val magnitude: Float,
    val color: Float
)

object StarData {

    fun readStars(file: File): List<Star?> {
        val reader = DataInputStream(FileInputStream(file))

        val stars = mutableListOf<Star?>()
        try {
            while (true) {
                val star = Star(
                    reader.readDouble(),
                    reader.readDouble(),
                    reader.readFloat(),
                    reader.readFloat())
                if (star.ra == Double.MIN_VALUE) {
                    stars += null
                } else {
                    stars += star
                }
            }
        } catch (ex: EOFException) {
            // ignore
        }
        return stars
    }

    fun readStarNames(hipparcosStars: List<Star?>, file: File): Map<Star, String> {
        val result = mutableMapOf<Star, String>()

        val reader = BufferedReader(FileReader(file))
        var line = reader.readLine()
        while (line != null) {
            if (line.startsWith("#")) {
                // ignore
            } else {
                val split = line.split(",")
                val hipNumber = split[0].trim().toIntOrNull()
                val name = split[1].trim().replace('_', ' ')
                if (hipNumber != null) {
                    val star = hipparcosStars[hipNumber]
                    if (star != null) {
                        result[star] = name
                    } else {
                        println("Missing star $hipNumber for $name")
                    }
                }
            }
            line = reader.readLine()
        }

        return result
    }

    fun readConstellations(hipparcosStars: List<Star?>, file: File): Map<String, List<Pair<Star, Star>>> {
        val result = mutableMapOf<String, List<Pair<Star, Star>>>()
        val reader = BufferedReader(FileReader(file))
        var line = reader.readLine()
        while (line != null) {
            if (line.startsWith("#")) {
                // ignore
            } else {
                val split = line.split(Regex("[ \t]+"))
                val name = split[0]
                val count = split[1].toInt()
                val pairList = mutableListOf<Pair<Star, Star>>()
                for (i in 0 until count) {
                    val from = split[i*2+2+0].toInt()
                    val to = split[i*2+2+1].toInt()
                    val fromStar = hipparcosStars[from]
                    val toStar = hipparcosStars[to]
                    if (fromStar == null) {
                        println("First star of connection not found: $from")
                        println("in line: $line")
                    } else if (toStar == null) {
                        println("Second star of connection not found: $to")
                        println("in line: $line")
                    } else {
                        pairList.add(Pair(fromStar, toStar))
                    }
                }
                result[name] = pairList
            }
            line = reader.readLine()
        }
        return result
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val stars = StarData.readStars(File("hip.jbin"))

    }
}