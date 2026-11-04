package com.benchfirewatch.model

/**
 * Which thermal source the watch opens. Persisted as [id].
 * [useCamera] stays in lockstep so older call sites still mean "not the demo".
 *
 * Hardware is a USB-C thermal camera. A saved "auto" id from an older build
 * means that same path.
 */
enum class CameraChoice(val id: String, val shortLabel: String, val title: String) {
    DEMO("demo", "Demo", "Demo (Simulated)"),
    USB("usb", "USB-C", "USB-C Thermal"),
    ;

    val hardware: Boolean get() = this != DEMO

    fun displayTitle(): String = title

    fun displayShort(): String = shortLabel

    fun blurb(): String = when (this) {
        DEMO -> "Synthetic 160×120 scenes. Same detector as the camera, nothing plugged in."
        USB ->
            "USB-C thermal camera. P2 Pro, TC001, and T7 use the 256×384 frame. A camera that offers that mode is included. Thermal Master P1 and P3 use their own stream. A 512×484 frame uses the TC002C scale. UTi120M is recognized and does not invent temperatures."
    }

    companion object {
        fun fromId(id: String?): CameraChoice = when (id) {
            USB.id, "auto" -> USB
            else -> DEMO
        }

        /** Prefs written before the camera menu stored only a boolean. True meant the USB camera. */
        fun fromStored(cameraId: String?, legacyUseCamera: Boolean): CameraChoice {
            if (!cameraId.isNullOrBlank()) return fromId(cameraId)
            return if (legacyUseCamera) USB else DEMO
        }
    }
}

fun AppPrefs.cameraChoice(): CameraChoice = CameraChoice.fromId(cameraId)

fun AppPrefs.withCamera(choice: CameraChoice): AppPrefs = copy(
    cameraId = choice.id,
    useCamera = choice.hardware,
)
