package ch.obermuhlner.indi.client

import ch.obermuhlner.kotlin.javafx.*
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.layout.VBox
import javafx.stage.Stage
import javafx.event.EventHandler
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import org.indilib.i4j.Constants
import org.indilib.i4j.client.*
import java.util.*

class IndiClientApplication : Application() {
    private var devicesTabPane = tabpane {}
    private val mapDeviceToUserInterface = mutableMapOf<INDIDevice, GridPaneContext>()

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
        val portProperty = SimpleIntegerProperty(7624)
        return vbox(SPACING) {
            children += hbox(SPACING) {
                children += label("Host:")
                children += textfield(hostProperty) {
                }
                children += textfield(portProperty) {
                }
                children += button("Connect") {
                    onAction = EventHandler {
                        connectIndi(hostProperty.get(), portProperty.get())
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
                    addPropertyEditor(device, property, mapDeviceToUserInterface[device]!!)
                }
            }

            override fun removeProperty(device: INDIDevice, property: INDIProperty<*>) {
            }

            override fun messageChanged(device: INDIDevice) {
                println(device.lastMessage)
            }
        })

        Platform.runLater {
            val deviceGridPane = gridpane {
                hgap = SPACING
                vgap = ROW_SPACING
            }

            devicesTabPane.tabs += tab(device.name) {
                content = scrollpane {
                    content = vbox(SPACING) {
                        children += deviceGridPane
                    }
                }
            }

            mapDeviceToUserInterface[device] = deviceGridPane
        }
    }

    private fun addPropertyEditor(device: INDIDevice, property: INDIProperty<*>, gridPaneContext: GridPaneContext) {
        return when (property) {
            is INDINumberProperty -> addNumberPropertyEditor(property, gridPaneContext)
            is INDITextProperty -> addTextPropertyEditor(property, gridPaneContext)
            is INDISwitchProperty -> addSwitchPropertyEditor(property, gridPaneContext)
            is INDILightProperty -> addLightPropertyEditor(property, gridPaneContext)
            is INDIBLOBProperty -> addBlobPropertyEditor(property, gridPaneContext)
            else -> addGenericPropertyEditor(property, gridPaneContext)
        }
    }

    private fun addNumberPropertyEditor(property: INDINumberProperty, gridPaneContext: GridPaneContext) {
        addPopertyNameState(property, gridPaneContext)

        for (element in property.elementsAsList) {
            val stringProperty = SimpleStringProperty(element.valueAsString)
            val editStringProperty = SimpleStringProperty(element.valueAsString)
            val editable = property.permission != Constants.PropertyPermissions.RO
            gridPaneContext.row {
                cell {
                    label("")
                }
                cell {
                    label(element.name)
                }
                cell {
                    textfield(stringProperty) {
                        isDisable = true
                    }
                }
                if (editable) {
                    cell {
                        hbox(SPACING) {
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
                }
            }
            element.addINDIElementListener {
                stringProperty.value = it.valueAsString
            }
        }
    }

    private fun addTextPropertyEditor(property: INDITextProperty, gridPaneContext: GridPaneContext) {
        addPopertyNameState(property, gridPaneContext)

        for (element in property.elementsAsList) {
            val stringProperty = SimpleStringProperty(element.valueAsString)
            val editStringProperty = SimpleStringProperty(element.valueAsString)
            val editable = property.permission != Constants.PropertyPermissions.RO
            gridPaneContext.row {
                cell {
                    label("")
                }
                cell {
                    label(element.name)
                }
                cell {
                    textfield(stringProperty) {
                        isDisable = true
                    }
                }
                if (editable) {
                    cell {
                        hbox(SPACING) {
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
                }
            }
            element.addINDIElementListener {
                stringProperty.value = it.valueAsString
            }
        }
    }

    private fun addSwitchPropertyEditor(property: INDISwitchProperty, gridPaneContext: GridPaneContext) {
        addPopertyNameState(property, gridPaneContext)

        gridPaneContext.row {
            cell {
                label("")
            }
            cell(colspan = 3) {
                hbox(SPACING) {
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
        }
    }

    private fun addLightPropertyEditor(property: INDILightProperty, gridPaneContext: GridPaneContext) {
        addPopertyNameState(property, gridPaneContext)

        gridPaneContext.row {
            cell {
                label("")
            }
            cell(colspan = 3) {
                hbox(SPACING) {
                    for (element in property.elementsAsList) {
                        children += button(element.name) {
                            styleClass += "light_" + element.value.name.lowercase()
                            isDisable = true
                        }
                    }
                }
            }
        }
    }

    private fun addBlobPropertyEditor(property: INDIBLOBProperty, gridPaneContext: GridPaneContext) {
        addPopertyNameState(property, gridPaneContext)

        gridPaneContext.row {
            cell(colspan = 3) {
                label(property.nameStateAndValuesAsString)
            }
        }
    }

    private fun addGenericPropertyEditor(property: INDIProperty<*>, gridPaneContext: GridPaneContext) {
        addPopertyNameState(property, gridPaneContext)

        gridPaneContext.row {
            cell(colspan = 3) {
                label(property.nameStateAndValuesAsString)
            }
        }
    }

    private fun addPopertyNameState(property: INDIProperty<*>, gridPaneContext: GridPaneContext) {
        val stateProperty = SimpleObjectProperty(property.state)
        gridPaneContext.row {
            cell {
                stateLight(stateProperty) {
                    fill = toColor(property.state)
                }            }
            cell(colspan = 3) {
                label(property.name)
            }
        }

        property.addINDIPropertyListener {
            stateProperty.value = it.state
        }
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
        private const val ROW_SPACING = 6.0
        private const val LED_RADIUS = SPACING * 2

        @JvmStatic
        fun main(args: Array<String>) {
            launch(IndiClientApplication::class.java)
        }
    }
}