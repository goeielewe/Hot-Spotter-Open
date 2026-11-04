package com.benchfirewatch.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.os.Build
import androidx.core.content.ContextCompat
import com.benchfirewatch.model.PreviewImage
import com.benchfirewatch.model.ThermalFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Open-edition camera. Family A is the Realtek split frame. Family B is
 * Thermal Master P1/P3. A 512×484 YUYV frame is the TC002C path. UTi120M is
 * recognized and a raw frame is saved; its factory curve is not applied.
 */
class OpenThermalSource(
    private val context: Context,
    initialEmissivity: Float,
) : ThermalSource {
    private val _state = MutableStateFlow<SourceState>(SourceState.Idle)
    override val state: StateFlow<SourceState> = _state
    private val running = AtomicBoolean(false)
    private val ignored = HashSet<String>()
    @Volatile private var emissivity = initialEmissivity
    private var worker: Thread? = null

    override fun setEmissivity(value: Float) {
        emissivity = value
    }

    override fun start(scope: CoroutineScope, onFrame: (ThermalFrame) -> Unit, onClock: (Long) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        val thread = Thread {
            try {
                session(onFrame, onClock)
            } catch (t: Throwable) {
                _state.value = SourceState.Down(t.message ?: "USB camera error")
                TestReport.note("error ${t.javaClass.simpleName}")
            }
        }
        thread.name = "hot-spotter-open"
        worker = thread
        thread.start()
    }

    override fun stop() {
        running.set(false)
        worker?.interrupt()
        worker?.join(2_000)
        worker = null
        _state.value = SourceState.Idle
    }

    private fun session(onFrame: (ThermalFrame) -> Unit, onClock: (Long) -> Unit) {
        val usb = context.getSystemService(UsbManager::class.java)
        if (usb == null) {
            _state.value = SourceState.Down("This phone has no USB host API.")
            return
        }
        while (running.get()) {
            onClock(System.currentTimeMillis())
            if (!cameraAllowed()) {
                _state.value = SourceState.Searching(
                    "Allow the camera permission. Android asks for it before a USB camera can open.",
                )
                sleep(400)
                continue
            }
            val device = pick(usb)
            if (device == null) {
                _state.value = SourceState.Searching(
                    "Looking for a USB-C thermal camera. P2 Pro, TC001, T7, Thermal Master, or UTi120M.",
                )
                sleep(400)
                continue
            }
            if (!usb.hasPermission(device)) {
                _state.value = SourceState.Searching("Accept the USB permission prompt. Choose Always.")
                if (!UsbAccess.awaitDevice(context, device, 20_000)) {
                    if (!OpenCameras.listed(device.vendorId, device.productId)) ignored.add(key(device))
                    _state.value = SourceState.Searching("USB permission was denied. Accept the prompt and choose Always.")
                    sleep(400)
                    continue
                }
            }
            val seen = inspect(usb, device)
            if (seen.kind == OpenCameraKind.UNKNOWN) {
                ignored.add(key(device))
                TestReport.note("ignored ${hex(device.vendorId)}:${hex(device.productId)}")
                sleep(400)
                continue
            }
            when (seen.kind) {
                OpenCameraKind.UNIT_120 -> holdUniT(usb, device, seen, onClock)
                OpenCameraKind.THERMAL_MASTER_P3 -> streamMaster(usb, device, seen, 256, 192, onFrame, onClock)
                OpenCameraKind.THERMAL_MASTER_P1 -> streamMaster(usb, device, seen, 160, 120, onFrame, onClock)
                OpenCameraKind.TC002C -> streamSplit(usb, device, seen, wide = true, onFrame, onClock)
                else -> streamSplit(usb, device, seen, wide = false, onFrame, onClock)
            }
            if (running.get()) sleep(400)
        }
    }

    private fun pick(usb: UsbManager): UsbDevice? {
        val devices = usb.deviceList.values.filter { key(it) !in ignored }
        return devices.firstOrNull { OpenCameras.listed(it.vendorId, it.productId) }
            ?: devices.firstOrNull { looksLikeUvc(it) }
    }

    private fun looksLikeUvc(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) return true
        }
        return false
    }

    private fun inspect(usb: UsbManager, device: UsbDevice): Seen {
        val raw = descriptors(usb, device)
        val summary = UsbFacts.summary(device, raw)
        val wide = UvcModes.offers(raw, 512, 484)
        val tall = UvcModes.offers(raw, 256, 384)
        val kind = OpenCameras.kind(device.vendorId, device.productId, tall, wide)
        return Seen(kind, raw, summary)
    }

    private fun descriptors(usb: UsbManager, device: UsbDevice): ByteArray {
        if (!usb.hasPermission(device)) return ByteArray(0)
        val connection = usb.openDevice(device) ?: return ByteArray(0)
        return try {
            connection.rawDescriptors ?: ByteArray(0)
        } finally {
            connection.close()
        }
    }

    private fun streamSplit(
        usb: UsbManager,
        device: UsbDevice,
        seen: Seen,
        wide: Boolean,
        onFrame: (ThermalFrame) -> Unit,
        onClock: (Long) -> Unit,
    ) {
        val connection = usb.openDevice(device) ?: return
        try {
            claimVideo(connection, device)
            val width = if (wide) 512 else 256
            val height = if (wide) 484 else 384
            val target = UvcModes.target(seen.raw, width, height)
            val iso = UvcModes.largestIsoIn(seen.raw)
            val streamId = iso?.interfaceId ?: videoInterface(device)?.id ?: 1
            if (target != null) {
                UvcControl.commit(connection, streamId, target, width * height * 2)
            }
            val alt = iso?.alternate ?: 1
            setInterface(connection, streamId, alt)
            var sentEmissivity = Float.NaN
            val iface = videoInterface(device)
            val endpoint = iface?.let { bulkOrIsoIn(it) } ?: endpointFrom(device)
            if (endpoint == null) {
                _state.value = SourceState.Down("The camera has no IN endpoint.")
                return
            }
            val expect = width * height * 2
            val label = if (wide) "TC002C" else "USB-C thermal"
            _state.value = SourceState.Live(label)
            val packet = ByteArray(max(endpoint.maxPacketSize, 512).coerceAtMost(256 * 1024))
            var assembled = ByteArray(0)
            var saved = false
            while (running.get()) {
                onClock(System.currentTimeMillis())
                if (emissivity != sentEmissivity) {
                    sendEmissivity(connection)
                    sentEmissivity = emissivity
                }
                val n = read(connection, endpoint, packet)
                if (n <= 0) {
                    sleep(30)
                    continue
                }
                val payload = stripUvcHeader(packet, n)
                if (payload.isEmpty()) continue
                assembled += payload
                val done = payloadEof(packet, n) && assembled.size >= expect / 2
                if (assembled.size >= expect || done && assembled.size >= expect) {
                    val frame = assembled.copyOf(expect)
                    assembled = if (assembled.size > expect) assembled.copyOfRange(expect, assembled.size) else ByteArray(0)
                    if (!saved) {
                        TestReport.remember(device.vendorId, device.productId, if (wide) "tc002c" else "split", frame, seen.summary, seen.raw)
                        saved = true
                    }
                    val decoded = if (wide) Radiometry.decodeTc002c(frame) else Radiometry.decodeSplitYuyv(frame)
                    if (decoded != null) onFrame(toFrame(decoded, label))
                } else if (assembled.size > expect + 4096) {
                    TestReport.note("frame overrun ${assembled.size}")
                    assembled = ByteArray(0)
                }
            }
        } finally {
            connection.close()
        }
    }

    private fun streamMaster(
        usb: UsbManager,
        device: UsbDevice,
        seen: Seen,
        width: Int,
        height: Int,
        onFrame: (ThermalFrame) -> Unit,
        onClock: (Long) -> Unit,
    ) {
        val connection = usb.openDevice(device) ?: return
        try {
            val control = device.getInterface(0)
            val stream = if (device.interfaceCount > 1) device.getInterface(1) else control
            connection.claimInterface(control, true)
            if (stream !== control) connection.claimInterface(stream, true)
            val label = if (height == 192) "Thermal Master P3" else "Thermal Master P1"
            if (!startMaster(connection)) {
                TestReport.note("thermal master start command was not accepted")
            }
            val endpoint = bulkOrIsoIn(stream) ?: bulkOrIsoIn(control)
            if (endpoint == null) {
                _state.value = SourceState.Down("The camera has no IN endpoint.")
                return
            }
            val payload = 2 * (2 * height + 2) * width
            _state.value = SourceState.Live(label)
            val chunk = ByteArray(max(endpoint.maxPacketSize, 16 * 1024))
            var pending = ByteArray(0)
            var saved = false
            while (running.get()) {
                onClock(System.currentTimeMillis())
                val n = read(connection, endpoint, chunk)
                if (n <= 0) {
                    sleep(30)
                    continue
                }
                if (n > payload + MARKER + 64) {
                    TestReport.note("discarded oversized transfer $n")
                    pending = ByteArray(0)
                    continue
                }
                pending += chunk.copyOf(n)
                val start = findMasterStart(pending)
                if (start < 0) {
                    if (pending.size > MARKER) pending = pending.copyOfRange(pending.size - (MARKER - 1), pending.size)
                    continue
                }
                if (start > 0) pending = pending.copyOfRange(start, pending.size)
                if (pending.size < MARKER + payload) continue
                val body = pending.copyOfRange(MARKER, MARKER + payload)
                pending = pending.copyOfRange(MARKER + payload, pending.size)
                pending = dropEndMarker(pending)
                if (!saved) {
                    TestReport.remember(device.vendorId, device.productId, "thermal-master", body, seen.summary, seen.raw)
                    saved = true
                }
                val decoded = Radiometry.decodeThermalMaster(body, width, height) ?: continue
                onFrame(toFrame(decoded, label))
            }
        } finally {
            setInterface(connection, 1, 0)
            connection.close()
        }
    }

    private fun startMaster(connection: UsbDeviceConnection): Boolean {
        val first = vendorCommand(connection, ThermalMasterCommands.startStream)
        sleep(1_000)
        if (!running.get()) return first
        setInterface(connection, 1, 1)
        connection.controlTransfer(0x40, 0xEE, 0, 1, null, 0, 500)
        sleep(2_000)
        if (!running.get()) return first
        return vendorCommand(connection, ThermalMasterCommands.startStream)
    }

    private fun vendorCommand(connection: UsbDeviceConnection, command: ByteArray): Boolean {
        val wrote = connection.controlTransfer(0x41, 0x20, 0, 0, command, command.size, 500)
        val status = ByteArray(1)
        connection.controlTransfer(0xC1, 0x22, 0, 0, status, 1, 500)
        val response = ByteArray(1)
        connection.controlTransfer(0xC1, 0x21, 0, 0, response, 1, 500)
        connection.controlTransfer(0xC1, 0x22, 0, 0, status, 1, 500)
        return wrote >= 0
    }

    private fun holdUniT(usb: UsbManager, device: UsbDevice, seen: Seen, onClock: (Long) -> Unit) {
        val connection = usb.openDevice(device)
        var sample: ByteArray? = null
        if (connection != null) {
            try {
                for (i in 0 until device.interfaceCount) {
                    connection.claimInterface(device.getInterface(i), true)
                }
                val out = interruptOut(device)
                val inbound = (0 until device.interfaceCount).firstNotNullOfOrNull { bulkOrIsoIn(device.getInterface(it)) }
                if (out != null) {
                    val ask = byteArrayOf(0x81.toByte())
                    connection.bulkTransfer(out, ask, ask.size, 400)
                }
                if (inbound != null) {
                    val buf = ByteArray(UNIT_FRAME)
                    var filled = 0
                    var tries = 0
                    while (running.get() && filled < UNIT_FRAME && tries < 12) {
                        val chunk = ByteArray(min(4096, UNIT_FRAME - filled))
                        val n = read(connection, inbound, chunk)
                        if (n > 0) {
                            chunk.copyInto(buf, filled, 0, n)
                            filled += n
                        } else {
                            tries++
                            sleep(30)
                        }
                    }
                    if (filled > 0) sample = buf.copyOf(filled)
                }
            } finally {
                connection.close()
            }
        }
        TestReport.remember(device.vendorId, device.productId, "uti120", sample, seen.summary, seen.raw)
        _state.value = SourceState.Searching(
            "UNI-T UTi120M is connected. This build saves a raw frame in the test report and does not invent temperatures from it.",
        )
        while (running.get()) {
            onClock(System.currentTimeMillis())
            sleep(400)
        }
    }

    private fun sendEmissivity(connection: UsbDeviceConnection) {
        val ready = ByteArray(1)
        connection.controlTransfer(
            P2ProPackets.REQ_IN,
            P2ProPackets.REQUEST_READ,
            P2ProPackets.W_VALUE,
            0x200,
            ready,
            1,
            200,
        )
        val steps = P2ProPackets.emissivitySteps(emissivity)
        val (a, b) = P2ProPackets.longWrite(
            P2ProPackets.CMD_PROP_TPD or P2ProPackets.DIR_SET,
            P2ProPackets.PROP_EMISSIVITY,
            steps,
        )
        connection.controlTransfer(
            P2ProPackets.REQ_OUT,
            P2ProPackets.REQUEST_WRITE,
            P2ProPackets.W_VALUE,
            P2ProPackets.INDEX_LONG_A,
            a,
            a.size,
            200,
        )
        connection.controlTransfer(
            P2ProPackets.REQ_OUT,
            P2ProPackets.REQUEST_WRITE,
            P2ProPackets.W_VALUE,
            P2ProPackets.INDEX_LONG_B,
            b,
            b.size,
            200,
        )
    }

    private fun toFrame(decoded: Radiometry.CelsiusFrame, label: String): ThermalFrame {
        val argb = IntArray(decoded.celsius.size)
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        for (v in decoded.celsius) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        val span = max(hi - lo, 1f)
        for (i in decoded.celsius.indices) {
            val g = ((decoded.celsius[i] - lo) / span * 255f).toInt().coerceIn(0, 255)
            argb[i] = (0xFF shl 24) or (g shl 16) or ((g / 3) shl 8)
        }
        return ThermalFrame(
            timestampMs = System.currentTimeMillis(),
            width = decoded.width,
            height = decoded.height,
            celsius = decoded.celsius,
            label = label,
            preview = PreviewImage(decoded.width, decoded.height, argb),
        )
    }

    private fun claimVideo(connection: UsbDeviceConnection, device: UsbDevice) {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                connection.claimInterface(iface, true)
            }
        }
    }

    private fun videoInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO &&
                iface.interfaceSubclass == 2
            ) {
                return iface
            }
        }
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO) return iface
        }
        return if (device.interfaceCount > 0) device.getInterface(0) else null
    }

    private fun endpointFrom(device: UsbDevice): UsbEndpoint? {
        for (i in 0 until device.interfaceCount) {
            bulkOrIsoIn(device.getInterface(i))?.let { return it }
        }
        return null
    }

    private fun bulkOrIsoIn(iface: UsbInterface): UsbEndpoint? {
        var best: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.direction != UsbConstants.USB_DIR_IN) continue
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) return ep
            if (best == null || ep.maxPacketSize > best.maxPacketSize) best = ep
        }
        return best
    }

    private fun interruptOut(device: UsbDevice): UsbEndpoint? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                if (ep.direction == UsbConstants.USB_DIR_OUT &&
                    ep.type == UsbConstants.USB_ENDPOINT_XFER_INT
                ) {
                    return ep
                }
            }
        }
        return null
    }

    private fun setInterface(connection: UsbDeviceConnection, id: Int, alt: Int) {
        connection.controlTransfer(0x01, 0x0B, alt, id, null, 0, 300)
    }

    private fun read(connection: UsbDeviceConnection, endpoint: UsbEndpoint, buf: ByteArray): Int {
        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
            endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT
        ) {
            return connection.bulkTransfer(endpoint, buf, buf.size, 800)
        }
        val request = UsbRequest()
        if (!request.initialize(connection, endpoint)) return -1
        val buffer = ByteBuffer.allocateDirect(buf.size)
        try {
            if (!request.queue(buffer, buf.size)) return -1
            val done = connection.requestWait(800)
            if (done == null) return -1
            val n = buffer.position().coerceIn(0, buf.size)
            if (n == 0) return 0
            buffer.flip()
            buffer.get(buf, 0, n)
            return n
        } finally {
            request.close()
        }
    }

    private fun stripUvcHeader(packet: ByteArray, n: Int): ByteArray {
        if (n < 2) return ByteArray(0)
        val header = packet[0].toInt() and 0xFF
        if (header in 2..12 && header < n) return packet.copyOfRange(header, n)
        return packet.copyOf(n)
    }

    private fun payloadEof(packet: ByteArray, n: Int): Boolean {
        if (n < 2) return false
        val header = packet[0].toInt() and 0xFF
        if (header !in 2..12) return false
        return packet[1].toInt() and 0x02 != 0
    }

    private fun findMasterStart(buf: ByteArray): Int {
        val limit = buf.size - 1
        var i = 0
        while (i < limit) {
            if (buf[i] == MARKER.toByte()) {
                val sync = buf[i + 1].toInt() and 0xFF
                if (sync == 0x8C || sync == 0x8D) return i
            }
            i++
        }
        return -1
    }

    private fun dropEndMarker(buf: ByteArray): ByteArray {
        if (buf.size >= MARKER && buf[0] == MARKER.toByte()) {
            val sync = buf[1].toInt() and 0xFF
            if (sync == 0x8E || sync == 0x8F) {
                return if (buf.size == MARKER) ByteArray(0) else buf.copyOfRange(MARKER, buf.size)
            }
        }
        return buf
    }

    private fun cameraAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 28) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun key(device: UsbDevice): String = "${device.vendorId}:${device.productId}:${device.deviceId}"

    private fun hex(value: Int): String = "%04x".format(value)

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            running.set(false)
        }
    }

    private data class Seen(val kind: OpenCameraKind, val raw: ByteArray, val summary: String)

    companion object {
        private const val MARKER = 12
        private const val UNIT_FRAME = 25600
    }
}

/** Public USB Video Class probe and commit. No vendor library. */
object UvcControl {
    fun commit(connection: UsbDeviceConnection, interfaceId: Int, target: UvcModes.StreamTarget, frameBytes: Int) {
        val probe = ByteArray(26)
        probe[2] = target.formatIndex.toByte()
        probe[3] = target.frameIndex.toByte()
        writeLe32(probe, 4, target.interval)
        writeLe32(probe, 18, frameBytes)
        connection.controlTransfer(0x21, 0x01, 0x0100, interfaceId, probe, probe.size, 500)
        val got = ByteArray(34)
        val n = connection.controlTransfer(0xA1, 0x81, 0x0100, interfaceId, got, got.size, 500)
        val commit = if (n >= 26) got.copyOf(n) else probe
        connection.controlTransfer(0x21, 0x01, 0x0200, interfaceId, commit, commit.size, 500)
    }

    private fun writeLe32(dest: ByteArray, at: Int, value: Int) {
        dest[at] = (value and 0xFF).toByte()
        dest[at + 1] = ((value shr 8) and 0xFF).toByte()
        dest[at + 2] = ((value shr 16) and 0xFF).toByte()
        dest[at + 3] = ((value shr 24) and 0xFF).toByte()
    }
}

/** Interface and endpoint facts for a test report. String descriptors are left out. */
object UsbFacts {
    fun summary(device: UsbDevice, raw: ByteArray): String = buildString {
        append("interfaces=${device.interfaceCount}")
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            append(" if=${iface.id} alt=${iface.alternateSetting} class=${iface.interfaceClass} sub=${iface.interfaceSubclass} eps=${iface.endpointCount}")
            for (e in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(e)
                append(" ep=${ep.address} type=${ep.type} max=${ep.maxPacketSize}")
            }
        }
        append(" raw_len=${raw.size}")
    }
}
