package vn.lutrinh.app.domain.usecase

/** Device makers that get their own section in "Ghi hành trình ổn định" (docs/04 R22); everything else = [OTHER]. */
enum class DeviceMaker { XIAOMI, OPPO, SAMSUNG, VIVO, OTHER }

object BatteryGuide {
    private val XIAOMI = setOf("xiaomi", "redmi", "poco")
    private val OPPO = setOf("oppo", "realme", "oneplus")
    private val VIVO = setOf("vivo", "iqoo")

    /**
     * Maps `Build.MANUFACTURER` (and `Build.BRAND` when the manufacturer is not a known one: a Redmi
     * reports manufacturer "Xiaomi", some resellers report brand only) to a guide section.
     */
    fun makerOf(manufacturer: String?, brand: String? = null): DeviceMaker {
        for (raw in listOf(manufacturer, brand)) {
            val name = raw?.trim()?.lowercase().orEmpty()
            when {
                name in XIAOMI -> return DeviceMaker.XIAOMI
                name in OPPO -> return DeviceMaker.OPPO
                name == "samsung" -> return DeviceMaker.SAMSUNG
                name in VIVO -> return DeviceMaker.VIVO
            }
        }
        return DeviceMaker.OTHER
    }
}

/**
 * "Show once" rule for the guide that opens by itself the first time a recording starts:
 * [consume] is true exactly once; opening the guide by hand ([markSeen]) also uses it up.
 */
class ShowOnce(private val isSeen: () -> Boolean, private val setSeen: () -> Unit) {
    fun consume(): Boolean {
        if (isSeen()) return false
        setSeen()
        return true
    }

    fun markSeen() {
        if (!isSeen()) setSeen()
    }
}
