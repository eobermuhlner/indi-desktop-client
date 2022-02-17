package ch.obermuhlner.starmap.hipparchos

import java.io.*

object ConvertHipparchos {


    @JvmStatic
    fun main(args: Array<String>) {
        convert(File("stardata/tycho2/tyc2.dat"))
    }

    private fun convert(file: File) {
        val reader = BufferedReader(FileReader(file))
        var writer = DataOutputStream(FileOutputStream(File("tycho2.jbin")))
        var index = 0
        var totalDelta = 0
        var line = reader.readLine()
        while (line != null) {
            val hip = line.substring(0, 6).trim().toInt()
            val ra = line.substring(15, 28).trim().toDouble()
            val de = line.substring(29, 42).trim().toDouble()
            val par = line.substring(43, 50).trim().toDouble()
            val mag = line.substring(129, 136).trim().toFloat()
            val bv = line.substring(152, 158).trim().toFloat()

            if (index > hip) {
                throw RuntimeException("Impossible hipparchos number $hip at index $index")
            }
            while (index < hip) {
                totalDelta++
                writer.writeDouble(Double.MIN_VALUE)
                writer.writeDouble(Double.MIN_VALUE)
                writer.writeFloat(Float.MIN_VALUE)
                writer.writeFloat(Float.MIN_VALUE)
                index++
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
