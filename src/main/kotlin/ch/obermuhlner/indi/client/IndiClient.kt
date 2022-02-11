package ch.obermuhlner.indi.client

import org.indilib.i4j.client.*
import java.util.*
import kotlin.system.exitProcess

class IndiClient(val host: String, val port: Int) {
    private lateinit var connection: INDIServerConnection

    fun connect() {
        connection = INDIServerConnection(host, port)
        connection.connect()
    }

    fun disconnect() {
        connection.disconnect()
    }

    fun listDevices(printProperties: Boolean) {
        connection.addINDIServerConnectionListener(object : INDIServerConnectionListener {
            override fun newDevice(connection: INDIServerConnection, device: INDIDevice) {
                if (printProperties) {
                    println("-------------------------------------------------------")
                    println(device.name)
                    device.addINDIDeviceListener(object: INDIDeviceListener {
                        override fun newProperty(device: INDIDevice, property: INDIProperty<*>) {
                            println(property.nameStateAndValuesAsString)
                        }

                        override fun removeProperty(device: INDIDevice, property: INDIProperty<*>) {
                        }

                        override fun messageChanged(device: INDIDevice) {
                        }
                    })
                    println()
                } else {
                    println(device.name)
                }
            }

            override fun removeDevice(connection: INDIServerConnection, device: INDIDevice) {
            }

            override fun connectionLost(connection: INDIServerConnection) {
            }

            override fun newMessage(connection: INDIServerConnection, timestamp: Date, message: String) {
            }
        })
        connection.askForDevices()
    }

    fun listProperties(deviceName: String) {
        connection.askForDevices()
        val device = connection.waitForDevice(deviceName)
        for (propertyName in device.propertyNames) {
            println(propertyName)
        }
    }

    fun getProperty(deviceName: String, propertyName: String) {
        connection.askForDevices()
        val device = connection.waitForDevice(deviceName)
        val property = device.getProperty(propertyName)
        println(property.nameStateAndValuesAsString)
    }

    fun listPropertyElements(deviceName: String, propertyName: String) {
        connection.askForDevices()
        val device = connection.waitForDevice(deviceName)
        val property = device.getProperty(propertyName)
        for (element in property.elementsAsList) {
            println(element.name)
        }
    }

    fun getPropertyElement(deviceName: String, propertyName: String, elementName: String) {
        connection.askForDevices()
        val device = connection.waitForDevice(deviceName)
        val property = device.getProperty(propertyName)
        val element = property.getElement(elementName)
        println(element.nameAndValueAsString)
    }

    fun setPropertyElement(deviceName: String, propertyName: String, elementName: String, elementValue: String) {
        connection.askForDevices()
        val device = connection.waitForDevice(deviceName)
        val property = device.getProperty(propertyName)
        val element = property.getElement(elementName)

        element.checkCorrectValue(elementValue)
        element.desiredValue = elementValue
        property.sendChangesToDriver()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val client = IndiClient("192.168.0.223", 7624)
            client.connect()

            //client.listDevices(false)
            //client.listProperties("Telescope Simulator")
            //client.getProperty("Telescope Simulator", "GEOGRAPHIC_COORD")
            //client.listPropertyElements("Telescope Simulator", "GEOGRAPHIC_COORD")
            client.getPropertyElement("Telescope Simulator", "GEOGRAPHIC_COORD", "ELEV")
            client.setPropertyElement("Telescope Simulator", "GEOGRAPHIC_COORD", "ELEV", "490")
            client.getPropertyElement("Telescope Simulator", "GEOGRAPHIC_COORD", "ELEV")

            Thread.sleep(1000)

            //client.disconnect()
            exitProcess(0)
        }
    }
}