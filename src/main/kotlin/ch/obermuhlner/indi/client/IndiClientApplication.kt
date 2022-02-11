package ch.obermuhlner.indi.client

import ch.obermuhlner.kotlin.javafx.*
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.event.EventHandler
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import org.indilib.i4j.Constants
import org.indilib.i4j.client.*
import java.util.*

class IndiClientApplication : Application() {
    var devicesTabPane = tabpane {}
    private val mapDeviceToVbox = mutableMapOf<INDIDevice, VBox>()

    override fun start(primaryStage: Stage) {
        val root = VBox()
        val scene = Scene(root, 800.0, 600.0)

        scene.stylesheets.add(IndiClientApplication::class.java.getResource("/application.css").toExternalForm());

        root.children.add(createMainNode())

        primaryStage.scene = scene
        primaryStage.show()
    }

    private fun createMainNode(): Node {
        val hostProperty = SimpleStringProperty("192.168.0.222")
        return vbox(SPACING) {
            children += hbox(SPACING) {
                children += label("Host:")
                children += textfield(hostProperty) {
                }
                children += button("Connect") {
                    onAction = EventHandler {
                        connectIndi(hostProperty.get())
                    }
                }
            }

            children += devicesTabPane
        }
    }

    private fun connectIndi(host: String, port: Int = 7624) {
        val connection = INDIServerConnection(host, port)
        connection.connect()

        connection.addINDIServerConnectionListener(object: INDIServerConnectionListener {
            override fun newDevice(connection: INDIServerConnection, device: INDIDevice) {
                println("newDevice ${device.name}")
                addDeviceTab(device)
            }

            override fun removeDevice(connection: INDIServerConnection, device: INDIDevice) {
                println("removeDevice ${device.name}")
            }

            override fun connectionLost(connection: INDIServerConnection) {
                println("connectionLost ${connection.url}")
            }

            override fun newMessage(connection: INDIServerConnection, timestamp: Date, message: String) {
                println("$timestamp: $message")
            }
        })

        connection.askForDevices()
    }

    private fun addDeviceTab(device: INDIDevice) {
        device.addINDIDeviceListener(object : INDIDeviceListener {
            override fun newProperty(device: INDIDevice, property: INDIProperty<*>) {
                Platform.runLater {
                    mapDeviceToVbox[device]!!.children += vbox(SPACING) {
                        children += createPropertyEditor(device, property)
                    }
                }
            }

            override fun removeProperty(device: INDIDevice, property: INDIProperty<*>) {
            }

            override fun messageChanged(device: INDIDevice) {
            }
        })

        Platform.runLater {
            val devicePropertiesBox = vbox(SPACING) {
                children += label(device.name)
            }

            devicesTabPane.tabs += tab(device.name) {
                content = scrollpane {
                    content = devicePropertiesBox
                }
            }

            mapDeviceToVbox[device] = devicePropertiesBox
        }
    }

    private fun createPropertyEditor(device: INDIDevice, property: INDIProperty<*>): Node {
        return when (property) {
            is INDINumberProperty -> createNumberPropertyEditor(property)
            is INDITextProperty -> createTextPropertyEditor(property)
            is INDISwitchProperty -> createSwitchPropertyEditor(property)
            is INDILightProperty -> createLightPropertyEditor(property)
            is INDIBLOBProperty -> createBlobPropertyEditor(property)
            else -> label(property.nameStateAndValuesAsString)
        }
    }

    private fun createNumberPropertyEditor(property: INDINumberProperty): Node {
        return vbox(SPACING) {
            children += propertyNameState(property)

            for (element in property.elementsAsList) {
                val stringProperty = SimpleStringProperty(element.valueAsString)
                val editStringProperty = SimpleStringProperty(element.valueAsString)
                val editable = property.permission != Constants.PropertyPermissions.RO
                children += hbox(SPACING) {
                    children += label(element.name)
                    children += textfield(stringProperty) {
                        isDisable = true
                    }
                    if (editable) {
                        children += textfield(editStringProperty) {
                        }
                        children += button("Set") {
                            onAction = EventHandler {
                                element.setDesiredValue(editStringProperty.value)
                                property.sendChangesToDriver()
                            }
                        }
                    }
                }
                element.addINDIElementListener {
                    stringProperty.value = it.valueAsString
                }
            }
        }
    }

    private fun createTextPropertyEditor(property: INDITextProperty): Node {
        return vbox(SPACING) {
            children += propertyNameState(property)

            for (element in property.elementsAsList) {
                val stringProperty = SimpleStringProperty(element.value)
                val editStringProperty = SimpleStringProperty(element.valueAsString)
                val editable = property.permission != Constants.PropertyPermissions.RO
                children += hbox(SPACING) {
                    children += label(element.name)
                    children += textfield(stringProperty) {
                        isDisable = true
                    }
                    if (editable) {
                        children += textfield(editStringProperty) {
                        }
                        children += button("Set") {
                            onAction = EventHandler {
                                element.desiredValue = editStringProperty.value
                                property.sendChangesToDriver()
                            }
                        }
                    }
                }
                element.addINDIElementListener {
                    stringProperty.value = it.valueAsString
                }
            }
        }
    }

    private fun createSwitchPropertyEditor(property: INDISwitchProperty): Node {
        return hbox(SPACING) {
            children += propertyNameState(property)

            for (element in property.elementsAsList) {
                val booleanProperty = SimpleBooleanProperty(element.value == Constants.SwitchStatus.ON)
                children += togglebutton(element.name) {
                    selectedProperty().bindBidirectional(booleanProperty)
                    isDisable = property.permission == Constants.PropertyPermissions.RO
                }
                booleanProperty.addListener { _, _, value ->
                    element.desiredValue = if (value) Constants.SwitchStatus.ON else Constants.SwitchStatus.OFF
                    property.sendChangesToDriver()
                }
                element.addINDIElementListener {
                    booleanProperty.value = it.value == Constants.SwitchStatus.ON
                }
            }
        }
    }

    private fun createLightPropertyEditor(property: INDILightProperty): Node {
        return hbox(SPACING) {
            children += propertyNameState(property)

            for (element in property.elementsAsList) {
                children += button(element.name) {
                    styleClass += "light_" + element.value.name.lowercase()
                    isDisable = true
                }
            }
        }
    }

    private fun createBlobPropertyEditor(property: INDIBLOBProperty): Node {
        return label(property.nameStateAndValuesAsString)
    }

    private fun propertyNameState(property: INDIProperty<*>): Node {
        val stateProperty = SimpleObjectProperty(property.state)
        val node = hbox {
            children += stateLight(stateProperty) {
                fill = toColor(property.state)
            }
            children += label(property.name)
        }
        property.addINDIPropertyListener {
            stateProperty.value = it.state
        }

        return node
    }

    private fun stateLight(stateProperty: SimpleObjectProperty<Constants.PropertyStates>, initializer: Circle.() -> Unit): Circle {
        val circle = Circle(LED_RADIUS)
        circle.stroke = Color.LIGHTGRAY
        stateProperty.addListener { _, _, value ->
            circle.fill = toColor(value)
        }
        return circle.apply(initializer)
    }

    private fun toColor(propertyState: Constants.PropertyStates): Color = when(propertyState) {
        Constants.PropertyStates.IDLE -> Color.GRAY
        Constants.PropertyStates.OK -> Color.GREEN
        Constants.PropertyStates.BUSY -> Color.YELLOW
        Constants.PropertyStates.ALERT -> Color.RED
    }

    companion object {
        private const val SPACING = 4.0
        private const val LED_RADIUS = SPACING * 2

        @JvmStatic
        fun main(args: Array<String>) {
            launch(IndiClientApplication::class.java)
        }
    }
}