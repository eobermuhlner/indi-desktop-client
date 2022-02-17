package ch.obermuhlner.indi.client

import ch.obermuhlner.kotlin.javafx.*
import ch.obermuhlner.starmap.StarData
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.*
import javafx.event.EventHandler
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
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
    private val starMapCanvas = ResizableCanvas()
    private val hipparcosStars = StarData.readStars(File("hip.jbin"))
    private val constellations = StarData.readConstellations(hipparcosStars, File("constellations_western.txt"))
    private val starNames = StarData.readStarNames(hipparcosStars, File("star_names_western.txt"))

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

            devicesTabPane.tabs += tab("Map") {
                content = starMapCanvas
                var startRa: Double? = null
                var startDe: Double? = null
                starMapCanvas.onMousePressed = EventHandler { event: MouseEvent ->
                    val x = event.x - starMapCanvas.width / 2
                    val y = (starMapCanvas.height - event.y) - starMapCanvas.height / 2
                    val (ra2, de2) = stereographicProjectionToRaDe(x, y, projectionRadiusProperty.value)
                    val ra = centerRaProperty.value + ra2
                    val de = centerDeProperty.value + de2
                    startRa = ra
                    startDe = de
                }
                starMapCanvas.onMouseDragged = EventHandler { event: MouseEvent ->
                    val x = event.x - starMapCanvas.width / 2
                    val y = (starMapCanvas.height - event.y) - starMapCanvas.height / 2
                    val (ra2, de2) = stereographicProjectionToRaDe(x, y, projectionRadiusProperty.value)
                    val ra = centerRaProperty.value + ra2
                    val de = centerDeProperty.value + de2
                    val localStartRa = startRa
                    val localStartDe = startDe
                    if (localStartRa != null && localStartDe != null) {
                        val deltaRa = localStartRa - ra
                        val deltaDe = localStartDe - de
                        centerRaProperty.value = wrap(centerRaProperty.value + deltaRa, 0.0, 2 * Math.PI)
                        centerDeProperty.value = clamp(centerDeProperty.value + deltaDe, -Math.PI, Math.PI)
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

                starMapCanvas.widthProperty().addListener { _, _, _ -> drawStars(starMapCanvas) }
                starMapCanvas.heightProperty().addListener { _, _, _ -> drawStars(starMapCanvas) }

                centerRaProperty.addListener { _, _, _ -> drawStars(starMapCanvas) }
                centerDeProperty.addListener { _, _, _ -> drawStars(starMapCanvas) }
                projectionRadiusProperty.addListener { _, _, _ -> drawStars(starMapCanvas) }
                drawStars(starMapCanvas)
            }

            children += devicesTabPane
        }
    }

    private fun wrap(value: Double, min: Double, max: Double): Double {
        return if (value > max) {
            value - max
        } else if (value < min) {
            value + min
        } else {
            value
        }
    }

    private fun clamp(value: Double, min: Double, max: Double): Double {
        return if (value < min) {
            min
        } else if (value > max) {
            max
        } else {
            value
        }
    }

    private fun drawStars(canvas: Canvas) {
        drawStars(canvas, centerRaProperty.value, centerDeProperty.value, projectionRadiusProperty.value)
    }

    private fun drawStars(canvas: Canvas, centerRa: Double, centerDe: Double, projectionRadius: Double, limitMagnitude: Double = Math.log10(projectionRadius)+3.0, labelDarkestMagnitude: Double = limitMagnitude+2.0, labelMinProjectionRadius: Double = 1000.0) {
        val gc = canvas.graphicsContext2D
        val width = canvas.width
        val height = canvas.height

        fun toX(x: Double) = x + width/2
        fun toY(y: Double) = height - (y + height/2)

        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, width, height)

        gc.stroke = Color.BLUE
        for (constellation in constellations) {
            for (starPair in constellation.value) {
                val (x1, y1) = stereographicProjectionToXY(
                    starPair.first.ra-centerRa,
                    starPair.first.de-centerDe,
                    centerRa,
                    centerDe,
                    projectionRadius)
                val (x2, y2) = stereographicProjectionToXY(
                    starPair.second.ra-centerRa,
                    starPair.second.de-centerDe,
                    projectionRadius)
                gc.strokeLine(toX(x1), toY(y1), toX(x2), toY(y2))
            }
        }

        gc.fill = Color.WHITE
        for (star in hipparcosStars) {
            if (star != null) {
                val (xx, yy) = stereographicProjectionToXY(
                    star.ra-centerRa,
                    star.de-centerDe,
                    projectionRadius
                )

                val x = toX(xx)
                val y = toY(yy)

                if (x > 0 && x < width && y > 0 && y < height) {
                    val radius = -star.magnitude + limitMagnitude
                    if (radius > 0) {
                        gc.fillOval(x - radius / 2, y - radius / 2, radius, radius)
                        if (projectionRadius > labelMinProjectionRadius /*&& star.magnitude < labelDarkestMagnitude*/) {
                            //gc.strokeText(star.magnitude.toString(), x, y)
                            val name = starNames[star]
                            if (name != null) {
                                gc.strokeText(name, x, y)
                            }
                        }
                    }
                }
            }
        }
    }

    // https://mathworld.wolfram.com/StereographicProjection.html
    private fun stereographicProjectionToXY(
        ra: Double,
        de: Double,
        radius: Double,
    ): Pair<Double, Double> {
        val sinDe = sin(de)
        val cosDe = cos(de)
        val k = 2 * radius / (1 + cosDe*cos(ra))
        val x = k * cosDe * sin(ra)
        val y = k * (sinDe)
        return Pair(x, y)
    }

    private fun stereographicProjectionToXY(
        ra: Double,
        de: Double,
        projectionCenterRa: Double,
        projectionCenterDe: Double,
        radius: Double,
        cosProjectionCenterDe: Double = cos(projectionCenterDe),
        sinProjectionCenterDe: Double = sin(projectionCenterDe)
    ): Pair<Double, Double> {
        val sinDe = sin(de)
        val cosDe = cos(de)
        val k = 2 * radius / (1 + sinProjectionCenterDe *sinDe + cosProjectionCenterDe *cosDe*cos(ra - projectionCenterRa))
        val x = k * cosDe * sin(ra - projectionCenterRa)
        val y = k * (cosProjectionCenterDe * sinDe + sinProjectionCenterDe * cosDe * cos(ra - projectionCenterRa))
        return Pair(x, y)
    }

    private fun stereographicProjectionToRaDe(
        x: Double,
        y: Double,
        radius: Double,
    ): Pair<Double, Double> {
        val r = sqrt(x*x + y*y)
        var c = 2 * atan2(r, 2*radius)
        val de = asin((y * sin(c)) / r )
        val ra = atan2(x * sin(c), r * cos(c))
        return Pair(ra, de)
    }

    private fun stereographicProjectionToRaDe(
        x: Double,
        y: Double,
        centerRa: Double,
        centerDe: Double,
        radius: Double,
        sinCenterDe: Double = sin(centerDe),
        cosCenterDe: Double = cos(centerDe)
    ): Pair<Double, Double> {
        val r = sqrt(x*x + y*y)
        var c = 2 * atan2(r, 2*radius)
        val de = asin(cos(c) * sinCenterDe + (y * sin(c) * cosCenterDe) / r )
        val ra = centerRa + atan2(x * sin(c), r * cosCenterDe * cos(c) - y * sinCenterDe * sin(c))
        return Pair(ra, de)
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
                // TODO remove rows
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
                        textfield(editStringProperty) {
                        }
                    }
                    if (element == property.elementsAsList[0]) {
                        cell(rowspan = property.elementsAsList.size) {
                            button("Set") {
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
                        textfield(editStringProperty) {
                        }
                    }
                    if (element == property.elementsAsList[0]) {
                        cell(rowspan = property.elementsAsList.size) {
                            button("Set") {
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
                            val kernel = hdu.kernel as Array<ShortArray>
                            for (y in 0 until height) {
                                for (x in 0 until width) {
                                    val value = scaleFitsValue(kernel[y][x].toDouble(), hdu)
                                    val argb = toARGB(value, value, value)
                                    pw.setArgb(x, y, argb)
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