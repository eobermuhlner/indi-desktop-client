package ch.obermuhlner.indi.client

import ch.obermuhlner.kotlin.javafx.*
import ch.obermuhlner.math.clamp
import ch.obermuhlner.math.wrap
import ch.obermuhlner.starmap.data.StarData
import ch.obermuhlner.starmap.javafx.StarCanvas
import ch.obermuhlner.starmap.projection.EquatorialStereographicProjection
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.scene.web.WebView
import javafx.stage.Stage
import nom.tam.fits.BasicHDU
import nom.tam.fits.Fits
import nom.tam.fits.ImageHDU
import org.indilib.i4j.Constants
import org.indilib.i4j.client.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.*


class IndiClientApplication : Application() {
    private var connection: INDIServerConnection? = null
    private var devicesTabPane = tabpane {}
    private val mapDeviceToUserInterface = mutableMapOf<INDIDevice, GridPaneContext>()
    private val deviceManagerWebview = WebView()


    private val hipparcosStars = StarData.readStars(File("hip.jbin"))
    private val constellations = StarData.readConstellations(hipparcosStars, File("constellations_western.txt"))
    private val starNames = StarData.readStarNames(hipparcosStars, File("star_names_western.txt"))

    //private val starMapCanvas = ResizableCanvas()
    private val starMapCanvas = StarCanvas(hipparcosStars, constellations, starNames, EquatorialStereographicProjection())

    private val centerRaProperty = SimpleDoubleProperty(0.0)
    private val centerDeProperty = SimpleDoubleProperty(0.0)
    private val projectionRadiusProperty = SimpleDoubleProperty(200.0)

    override fun start(primaryStage: Stage) {
        val root = VBox()
        val scene = Scene(root, 800.0, 600.0)

        scene.stylesheets.add(IndiClientApplication::class.java.getResource("/application.css").toExternalForm());

        root.children.add(createMainNode())

        primaryStage.scene = scene
        primaryStage.show()
    }

    override fun stop() {
        connection?.let {
            it.disconnect()
        }
        super.stop()
    }

    private fun createMainNode(): Node {
        val hostProperty = SimpleStringProperty("192.168.0.223")
        val portProperty = SimpleIntegerProperty(7624)
        val devicePortProperty = SimpleIntegerProperty(8624)
        return vbox(SPACING) {
            children += hbox(SPACING) {
                children += label("Host:")
                children += textfield(hostProperty) {
                }
                children += textfield(portProperty) {
                }
                children += textfield(devicePortProperty) {
                }
                children += button("Connect") {
                    onAction = EventHandler {
                        deviceManagerWebview.engine.load("http://${hostProperty.value}:${devicePortProperty.value}/")
                        connectIndi(hostProperty.get(), portProperty.get())
                    }
                }
            }

            devicesTabPane.tabs += tab("Map") {
                content = starMapCanvas
                var startRa: Double? = null
                var startDe: Double? = null
                starMapCanvas.onMousePressed = EventHandler { event: MouseEvent ->
                    val x = event.x - starMapCanvas.width / 2
                    val y = (starMapCanvas.height - event.y) - starMapCanvas.height / 2
                    val (ra2, de2) = toRaDe(x, y)
                    val raLast = centerRaProperty.value
                    val deLast = centerDeProperty.value
                    val ra = raLast + ra2
                    val de = deLast + de2
                    startRa = ra
                    startDe = de
                }
                starMapCanvas.onMouseDragged = EventHandler { event: MouseEvent ->
                    val x = event.x - starMapCanvas.width / 2
                    val y = (starMapCanvas.height - event.y) - starMapCanvas.height / 2
                    val (ra2, de2) = toRaDe(x, y)
                    val raLast = centerRaProperty.value
                    val deLast = centerDeProperty.value
                    val ra = raLast + ra2
                    val de = deLast + de2
                    val localStartRa = startRa
                    val localStartDe = startDe
                    if (localStartRa != null && localStartDe != null) {
                        val deltaRa = localStartRa - ra
                        val deltaDe = localStartDe - de
                        centerRaProperty.value = wrap(raLast + deltaRa, 0.0, 2 * Math.PI)
                        centerDeProperty.value = clamp(deLast + deltaDe, -Math.PI, Math.PI)
                    } else {
                        startRa = ra
                        startDe = de
                    }
                }
                starMapCanvas.onMouseReleased = EventHandler { event: MouseEvent ->
                    startRa = null
                    startDe = null
                }

                starMapCanvas.onScroll = EventHandler { event: ScrollEvent ->
                    val factor = if (event.deltaY > 0) 1.1 else 0.9
                    projectionRadiusProperty.value = min(20000.0, max(200.0, projectionRadiusProperty.value * factor))
                }

                starMapCanvas.widthProperty().addListener { _, _, _ -> drawStars() }
                starMapCanvas.heightProperty().addListener { _, _, _ -> drawStars() }

                centerRaProperty.addListener { _, _, _ -> drawStars() }
                centerDeProperty.addListener { _, _, _ -> drawStars() }
                projectionRadiusProperty.addListener { _, _, _ -> drawStars() }
                drawStars()
            }

            devicesTabPane.tabs += tab("Device Manager") {
                content = deviceManagerWebview
            }

            children += devicesTabPane
        }
    }

    private fun toRaDe(x: Double, y: Double): Pair<Double, Double> {
        return starMapCanvas.projection.toRaDe(x, y, centerRaProperty.value, centerDeProperty.value, projectionRadiusProperty.value)
    }

    private fun toXY(ra: Double, de: Double): Pair<Double, Double> {
        return starMapCanvas.projection.toXY(ra, de, centerRaProperty.value, centerDeProperty.value, projectionRadiusProperty.value)
    }

    private fun drawStars() {
        starMapCanvas.ra0 = centerRaProperty.value
        starMapCanvas.de0 = centerDeProperty.value
        starMapCanvas.zoomFactor = projectionRadiusProperty.value
        starMapCanvas.draw()
    }

    private fun connectIndi(host: String, port: Int = 7624) {
        val serverConnection = INDIServerConnection(host, port)
        serverConnection.connect()

        serverConnection.addINDIServerConnectionListener(object: INDIServerConnectionListener {
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

        Thread().run {
            try {
                serverConnection.askForDevices()
                connection = serverConnection
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
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
                val gridPaneContext = mapDeviceToUserInterface[device]!!
                Platform.runLater {
                    gridPaneContext.children
                        .filter { it.userData == property }
                        .forEach {
                            gridPaneContext.children.remove(it)
                        }
                }
            }

            override fun messageChanged(device: INDIDevice) {
                lastMessageProperty.value += device.lastMessage + "\n"
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
                    bottom = textarea(lastMessageProperty) {
                        minHeight = 150.0
                        isEditable = false
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

        val elementToEditStringProperties = mutableMapOf<INDINumberElement, StringProperty>()

        fun updateEditedElements() {
            for (entry in elementToEditStringProperties) {
                entry.key.setDesiredValue(entry.value.value)
            }
            property.sendChangesToDriver()
        }

        for (element in property.elementsAsList) {
            val stringProperty = SimpleStringProperty(element.valueAsString)
            val editStringProperty = SimpleStringProperty(element.valueAsString)
            elementToEditStringProperties[element] = editStringProperty
            val editable = property.permission != Constants.PropertyPermissions.RO
            gridPaneContext.row {
                cell {
                    label("") {
                        userData = property
                    }
                }
                cell {
                    label(element.label) {
                        userData = property
                    }
                }
                cell {
                    tooltipWrapper(
                        textfield(stringProperty) {
                            isDisable = true
                        },
                        tooltip("""
                            ${element.label}
                            ${element.min} - ${element.max}
                            """.trimIndent())
                    ) {
                        userData = property
                    }
                }
                if (editable) {
                    cell {
                        textfield(editStringProperty) {
                            userData = property
                        }
                    }
                    if (element == property.elementsAsList[0]) {
                        cell(rowspan = property.elementsAsList.size) {
                            button("Set") {
                                userData = property
                                setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
                                onAction = EventHandler {
                                    updateEditedElements()                            }
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

        val elementToEditStringProperties = mutableMapOf<INDITextElement, StringProperty>()

        fun updateEditedElements() {
            for (entry in elementToEditStringProperties) {
                entry.key.setDesiredValue(entry.value.value)
            }
            property.sendChangesToDriver()
        }

        for (element in property.elementsAsList) {
            val stringProperty = SimpleStringProperty(element.valueAsString)
            val editStringProperty = SimpleStringProperty(element.valueAsString)
            val editable = property.permission != Constants.PropertyPermissions.RO
            elementToEditStringProperties[element] = editStringProperty
            gridPaneContext.row {
                cell {
                    label("") {
                        userData = property
                    }
                }
                cell {
                    label(element.label) {
                        userData = property
                    }
                }
                cell {
                    tooltipWrapper(
                        textfield(stringProperty) {
                            isDisable = true
                        },
                        tooltip(stringProperty)
                    ) {
                        userData = property
                    }
                }
                if (editable) {
                    cell {
                        textfield(editStringProperty) {
                            userData = property
                        }
                    }
                    if (element == property.elementsAsList[0]) {
                        cell(rowspan = property.elementsAsList.size) {
                            button("Set") {
                                userData = property
                                setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
                                onAction = EventHandler {
                                    updateEditedElements()                            }
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
                label("") {
                    userData = property
                }
            }
            cell(colspan = 3) {
                flowpane(SPACING) {
                    userData = property
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
                    label("") {
                        userData = property
                    }
                }
                cell {
                    label(element.label) {
                        userData = property
                    }
                }
                cell(colspan = 2) {
                    vbox {
                        userData = property
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
                    Thread().run {
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
                        val fxImage = if (format == ".fits") {
                            fitsToFXImage(it.value.blobData)
                        } else {
                            toFXImage(ImageIO.read(file))
                        }
                        Platform.runLater {
                            imageProperty.value = fxImage
                        }
                    }
                }
            }
        }
    }

    private fun fitsToFXImage(
        data: ByteArray?,
        valueFunc: (Double) -> Double = { v -> v.pow(1.0 / 5.0) }): Image? {
        val fits = Fits(ByteArrayInputStream(data))
        val hdu = fits.getHDU(0)
        if (hdu is ImageHDU) {
            when (hdu.axes.size) {
                2 -> {
                    val height = hdu.axes[0]
                    val width = hdu.axes[1]

                    val wr = WritableImage(width, height)
                    val pw = wr.pixelWriter
                    var minValue = Double.MAX_VALUE
                    var maxValue = 0.0

                    when (hdu.bitPix) {
                        BasicHDU.BITPIX_SHORT -> {
                            val kernel = hdu.kernel as Array<ShortArray>
                            for (y in 0 until height) {
                                for (x in 0 until width) {
                                    val value = scaleFitsValue(kernel[y][x].toDouble(), hdu)
                                    minValue = min(value, minValue)
                                    maxValue = max(value, maxValue)
                                    val stretchedValue = valueFunc(value)
                                    val argb = toARGB(stretchedValue, stretchedValue, stretchedValue)
                                    pw.setArgb(x, y, argb)
                                }
                            }
                        }
                    }
                    println("IMAGE min=$minValue max=$maxValue")
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

    private fun toARGB(r: Double, g: Double, b: Double): Int {
        val aByte = 0xff
        val rByte = (r * 0xff).toInt()
        val gByte = (g * 0xff).toInt()
        val bByte = (b * 0xff).toInt()
        return  (aByte shl 24) or (rByte shl 16) or (gByte shl 8) or bByte
    }

    private fun scaleFitsValue(value: Double, hdu: BasicHDU<*>): Double {
        return if (hdu.minimumValue != hdu.maximumValue) {
            hdu.bZero + (value - hdu.minimumValue) / (hdu.maximumValue - hdu.minimumValue) * hdu.bScale
        } else {
            val scaledValue = hdu.bZero + value * hdu.bScale
            when (hdu.bitPix) {
                BasicHDU.BITPIX_BYTE -> scaledValue / (256.0 - 1)
                BasicHDU.BITPIX_SHORT -> scaledValue / (65536.0 - 1)
                BasicHDU.BITPIX_INT -> scaledValue / (4294967296.0 - 1)
                BasicHDU.BITPIX_LONG -> scaledValue / (18446744073709551616.0 - 1)
                BasicHDU.BITPIX_FLOAT -> scaledValue
                else -> throw RuntimeException("Unknown bitpix: " + hdu.bitPix)
            }
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
                    userData = property
                    fill = toColor(property.state)
                }            }
            cell(colspan = 3) {
                label(property.label) {
                    userData = property
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