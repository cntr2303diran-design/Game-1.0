package id.diran.lisbon1944.engine.content

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** Kesalahan format konten, selalu menyebut file dan jalur JSON yang bermasalah. */
class ContentFormatException(val file: String, val path: String, message: String) :
    RuntimeException("$file: $path: $message")

/**
 * Pembaca pohon JSON dengan jejak jalur, agar pesan galat konten menunjuk tepat ke field yang salah
 * (mis. "board.json: $.tiles[12].price: wajib berupa bilangan bulat").
 */
internal class JsonNode(val file: String, val path: String, val element: JsonElement) {

    fun fail(message: String): Nothing = throw ContentFormatException(file, path, message)

    fun obj(): JsonObject = element as? JsonObject ?: fail("harus berupa objek JSON")

    fun array(): List<JsonNode> {
        val arr = element as? JsonArray ?: fail("harus berupa array JSON")
        return arr.mapIndexed { i, e -> JsonNode(file, "$path[$i]", e) }
    }

    fun has(key: String): Boolean = obj()[key]?.let { it !is JsonNull } ?: false

    fun child(key: String): JsonNode {
        val e = obj()[key] ?: JsonNode(file, "$path.$key", JsonNull).fail("field wajib tidak ada")
        return JsonNode(file, "$path.$key", e)
    }

    fun childOrNull(key: String): JsonNode? =
        obj()[key]?.takeIf { it !is JsonNull }?.let { JsonNode(file, "$path.$key", it) }

    fun asString(): String {
        val p = element as? JsonPrimitive
        if (p == null || !p.isString) fail("harus berupa string")
        return p.content
    }

    fun asInt(): Int {
        val p = element as? JsonPrimitive
        if (p == null || p.isString) fail("harus berupa bilangan bulat")
        return p.intOrNull ?: fail("harus berupa bilangan bulat, ditemukan ${p.content}")
    }

    fun asDouble(): Double {
        val p = element as? JsonPrimitive
        if (p == null || p.isString) fail("harus berupa angka")
        return p.doubleOrNull ?: fail("harus berupa angka, ditemukan ${p.content}")
    }

    fun asBoolean(): Boolean {
        val p = element as? JsonPrimitive
        if (p == null || p.isString) fail("harus berupa boolean")
        return p.booleanOrNull ?: fail("harus berupa boolean, ditemukan ${p.content}")
    }

    inline fun <reified E : Enum<E>> asEnum(): E {
        val raw = asString()
        return enumValues<E>().firstOrNull { it.name == raw }
            ?: fail("nilai '$raw' tidak dikenal; pilihan: ${enumValues<E>().joinToString { it.name }}")
    }

    fun string(key: String): String = child(key).asString()
    fun int(key: String): Int = child(key).asInt()
    fun double(key: String): Double = child(key).asDouble()
    fun boolean(key: String): Boolean = child(key).asBoolean()
    fun stringOrNull(key: String): String? = childOrNull(key)?.asString()
    fun intOrNull(key: String): Int? = childOrNull(key)?.asInt()
    fun booleanOr(key: String, default: Boolean): Boolean = childOrNull(key)?.asBoolean() ?: default
    fun intOr(key: String, default: Int): Int = childOrNull(key)?.asInt() ?: default
    inline fun <reified E : Enum<E>> enum(key: String): E = child(key).asEnum()
    fun ints(key: String): List<Int> = child(key).array().map { it.asInt() }

    /** Map dengan kunci enum, mis. buildCost { "PELABUHAN": 50, ... }. */
    inline fun <reified E : Enum<E>> enumIntMap(key: String): Map<E, Int> {
        val node = child(key)
        return node.obj().keys.associate { k ->
            val sub = node.child(k)
            val e = enumValues<E>().firstOrNull { it.name == k }
                ?: sub.fail("kunci '$k' tidak dikenal; pilihan: ${enumValues<E>().joinToString { it.name }}")
            e to sub.asInt()
        }
    }
}
