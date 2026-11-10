package vn.lutrinh.app.catalog

import org.json.JSONArray
import org.json.JSONObject

/** The shared spec copied from lutrinh-server (app/src/test/resources/spec, SOURCE.md names the commit). */
object Spec {
    fun bytes(path: String): ByteArray =
        requireNotNull(Spec::class.java.getResourceAsStream("/spec/$path")) { "missing spec/$path" }.use { it.readBytes() }

    fun text(path: String): String = bytes(path).toString(Charsets.UTF_8)

    fun json(path: String): JSONObject = JSONObject(text(path))

    const val SAMPLE = "samples/ha-giang-4n/route.json"
    const val SAMPLE_EXPECTED = "samples/ha-giang-4n/expected.json"

    /** Applies RFC 6902 `add` / `remove` / `replace` ops (as package-vectors.json uses them) to a copy of [doc]. */
    fun patch(doc: JSONObject, ops: JSONArray): JSONObject {
        val out = JSONObject(doc.toString())
        for (i in 0 until ops.length()) {
            val op = ops.getJSONObject(i)
            val parts = op.getString("path").split("/").drop(1).map { it.replace("~1", "/").replace("~0", "~") }
            var parent: Any = out
            parts.dropLast(1).forEach { k -> parent = if (parent is JSONArray) (parent as JSONArray).get(k.toInt()) else (parent as JSONObject).get(k) }
            val key = parts.last()
            val value = op.opt("value")
            when (val p = parent) {
                is JSONObject -> when (op.getString("op")) {
                    "remove" -> p.remove(key)
                    else -> p.put(key, value)
                }
                is JSONArray -> when (op.getString("op")) {
                    "remove" -> p.remove(key.toInt())
                    "replace" -> p.put(key.toInt(), value)
                    "add" -> if (key == "-") p.put(value) else insert(p, key.toInt(), value)
                }
            }
        }
        return out
    }

    private fun insert(a: JSONArray, at: Int, value: Any?) {
        val tail = (at until a.length()).map { a.get(it) }
        while (a.length() > at) a.remove(a.length() - 1)
        a.put(value)
        tail.forEach { a.put(it) }
    }
}
