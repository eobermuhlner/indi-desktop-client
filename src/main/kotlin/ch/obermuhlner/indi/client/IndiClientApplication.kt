package ch.obermuhlner.indi.client

import ch.obermuhlner.kotlin.javafx.*
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.stage.Stage
import nom.tam.fits.BasicHDU
import nom.tam.fits.Fits
import nom.tam.fits.ImageHDU
import org.indilib.i4j.Constants
import org.indilib.i4j.client.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO


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
                device.blobsEnable(Constants.BLOBEnables.ALSO)
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
        val lastMessageProperty = SimpleStringProperty("")
        device.addINDIDeviceListener(object : INDIDeviceListener {
            override fun newProperty(device: INDIDevice, property: INDIProperty<*>) {
                Platform.runLater {
                    addPropertyEditor(device, property, mapDeviceToUserInterface[device]!!)
                }
            }

            override fun removeProperty(device: INDIDevice, property: INDIProperty<*>) {
            }

            override fun messageChanged(device: INDIDevice) {
                lastMessageProperty.value = device.lastMessage
            }
        })

        Platform.runLater {
            val deviceGridPane = gridpane {
                hgap = SPACING
                vgap = ROW_SPACING
            }

            devicesTabPane.tabs += tab(device.name) {
                content = borderpane {
                    center = scrollpane {
                        content = vbox(SPACING) {
                            children += deviceGridPane
                        }
                    }
                    bottom = textfield(lastMessageProperty) {
                        isDisable = true
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
                    label(element.label)
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
                            children += label("${element.min} - ${element.max}")
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
                    label(element.label)
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
                flowpane(SPACING) {
                    for (element in property.elementsAsList) {
                        val booleanProperty = SimpleBooleanProperty(element.value == Constants.SwitchStatus.ON)
                        val infoProperty = SimpleStringProperty(element.nameAndValueAsString)
                        children += togglebutton(element.label) {
                            selectedProperty().bindBidirectional(booleanProperty)
                            isDisable = property.permission == Constants.PropertyPermissions.RO
                            tooltip = tooltip(infoProperty)
                        }
                        booleanProperty.addListener { _, _, value ->
                            element.desiredValue = if (value) Constants.SwitchStatus.ON else Constants.SwitchStatus.OFF
                            property.sendChangesToDriver()
                        }
                        element.addINDIElementListener {
                            booleanProperty.value = it.value == Constants.SwitchStatus.ON
                            infoProperty.value = it.nameAndValueAsString
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
                flowpane(SPACING) {
                    for (element in property.elementsAsList) {
                        children += button(element.label) {
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

        for (element in property.elementsAsList) {
            val blobProperty = SimpleStringProperty(element.valueAsString)
            val imageProperty = SimpleObjectProperty<Image>()
            gridPaneContext.row {
                cell {
                    label("")
                }
                cell {
                    label(element.label)
                }
                cell(colspan = 2) {
                    vbox {
                        children += textfield(blobProperty) {
                            isDisable = true
                        }
                        children += imageview {
                            this.imageProperty().bindBidirectional(imageProperty)
                            fitWidth = 300.0
                            fitHeight = 300.0
                            isPreserveRatio = true
                        }
                    }
                }
            }
            element.addINDIElementListener {
                println("BLOB ELEMENT " + it.nameAndValueAsString)
                blobProperty.value = it.valueAsString
                if (it is INDIBLOBElement) {
                    val format = if (it.value.format.startsWith(".")) {
                        it.value.format
                    } else {
                        "." + it.value.format
                    }
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_kkmmss"))
                    val filename = "${property.device.name}_${property.name}_${element.name}_$timestamp$format"
                    val file = File(filename)
                    println("BLOB FILE $file")
                    it.value.saveBLOBData(file)
                    if (format == ".fits") {
                        imageProperty.value = fitsToFXImage(it.value.blobData)
                    } else {
                        imageProperty.value = toFXImage(ImageIO.read(file))
                    }
                }
            }
        }
    }

    private fun fitsToFXImage(data: ByteArray?): Image? {
        val fits = Fits(ByteArrayInputStream(data))
        val hdu = fits.getHDU(0)
        if (hdu is ImageHDU) {
            when (hdu.axes.size) {
                2 -> {
                    val height = hdu.axes[0]
                    val width = hdu.axes[1]

                    val wr = WritableImage(width, height)
                    val pw = wr.pixelWriter

                    when (hdu.bitPix) {
                        BasicHDU.BITPIX_SHORT -> {
                            val data = hdu.kernel as Array<ShortArray>
                            for (y in 0 until height) {
                                for (x in 0 until width) {
                                    val value = scaleFitsValue(data[y][x].toDouble(), hdu)
                                    val rgb = toGrayRGB(value)
                                    pw.setArgb(x, y, rgb)
                                }
                            }
                        }
                    }
                    return ImageView(wr).image
                }
                3 -> {
                    val channels = hdu.axes[0]
                    val height = hdu.axes[1]
                    val width = hdu.axes[2]

                }
            }
        }
        return null
    }

    private fun toGrayRGB(value: Double): Int {
        val v = (value * 256).toInt()
        val rgb = v shl 24 + v shl 16 + v
        return rgb
    }

    private fun scaleFitsValue(value: Double, hdu: BasicHDU<*>): Double {
        return if (hdu.minimumValue != hdu.maximumValue) {
            hdu.bZero + (value - hdu.minimumValue) / (hdu.maximumValue - hdu.minimumValue) * hdu.bScale
        } else {
            hdu.bZero + value * hdu.bScale
        }
    }

    private fun toFXImage(image: BufferedImage?): Image? {
        var wr: WritableImage? = null
        if (image != null) {
            wr = WritableImage(image.width, image.height)
            val pw = wr.pixelWriter
            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    pw.setArgb(x, y, image.getRGB(x, y))
                }
            }
        }
        return ImageView(wr).image
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
        val infoProperty = SimpleStringProperty(property.nameStateAndValuesAsString)
        gridPaneContext.row {
            cell {
                stateLight(stateProperty) {
                    fill = toColor(property.state)
                }            }
            cell(colspan = 3) {
                label(property.label) {
                    tooltip = tooltip(infoProperty)
                }
            }
        }

        property.addINDIPropertyListener {
            stateProperty.value = it.state
            infoProperty.value = it.nameStateAndValuesAsString
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