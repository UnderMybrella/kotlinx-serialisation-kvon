package dev.brella.kotlinx.serialisation.kvon

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

public object Kvon {
    public val KVON_MISSING: String = ">\u0018<"

    public val NULL_JSON: JsonObject? = null
}

/**
 * Compress this element with [Kvon]
 *
 * This method checks the type of the element, then applies a deduplication strategy to it appropriately
 * - if this element is an array, each element is recursively compressed, using the previous element as a base. [baseElement] is assumed to be a starting element, if provided
 * - if this element is an object, each value is compressed against [baseElement], if provided.
 * - Otherwise, nothing happens
 */
public fun JsonElement.compressElementWithKvon(baseElement: JsonElement? = null): JsonElement {
    return when (this) {
        is JsonArray -> {
            buildJsonArray {
                fold(baseElement) { previous, element ->
                    add(element.compressElementWithKvon(previous))
                    element
                }
            }
        }

        is JsonObject -> {
            val previous = baseElement as? JsonObject ?: return this

            buildJsonObject {
                val changedKeys = filter { (key, value) -> previous[key] != value }
                val missingKeys = previous.keys.filterNot(::containsKey)

                changedKeys.forEach { (key, value) -> put(key, value.compressElementWithKvon(previous[key])) }
                missingKeys.forEach { key -> put(key, Kvon.KVON_MISSING) }
            }
        }

        else -> this
    }
}

/**
 * Decompresses this element with [Kvon]
 *
 * This method checks the type of the element, then applies a duplication strategy to it appropriately.
 *
 * - if this element is an array, each element is recursively decompressed, using the previous element as a base. [baseElement] is assumed to be a starting element, if provided
 * - if this element is an object, each value is decompressed against [baseElement], if provided
 * - Otherwise, nothing happens
 */
public fun JsonElement.decompressElementWithKvon(baseElement: JsonElement? = null): JsonElement =
    when (this) {
        is JsonArray -> buildJsonArray {
            fold(baseElement) { previous, element ->
                element.decompressElementWithKvon(previous).also(this::add)
            }

/*            fold(previous) { previous, element ->
                when (element) {
                    is JsonObject -> {
                        if (previous == null) {
                            add(element)
                            return@fold element
                        } else {
                            val reinterlaced = buildJsonObject {
//                                val withoutRemoved = element.filterValues { value -> value !is JsonPrimitive || !value.isString || value.content != KVON_MISSING }
//                                val changedKeys = withoutRemoved.filter { (key, value) -> previous[key] != value }
//                                val missingKeys = previous.keys.filterNot(element::containsKey)
                                val newKeys = element.filterKeys { it !in previous }

                                previous.forEach { (key, value) ->
                                    val newValue = element[key]

                                    if (newValue !is JsonPrimitive || newValue.contentOrNull != Kvon.KVON_MISSING)
                                        put(key, (newValue ?: value).compressElementWithKvon())
                                }

                                newKeys.forEach { (key, value) -> put(key, value.compressElementWithKvon()) }
                            }

                            add(reinterlaced)

                            return@fold reinterlaced
                        }
                    }

                    else -> add(element)
                }

                previous
            }*/
        }
        is JsonObject -> {
            val previous = baseElement as? JsonObject

            buildJsonObject {
                val newKeys = if (previous == null) this@decompressElementWithKvon else filterKeys { it !in previous }

                previous?.forEach { (key, value) ->
                    val newValue = this@decompressElementWithKvon[key]

                    if (newValue !is JsonPrimitive || newValue.contentOrNull != Kvon.KVON_MISSING)
                        put(key, (newValue ?: value).decompressElementWithKvon(value))
                }

                newKeys.forEach { (key, value) -> put(key, value.decompressElementWithKvon()) }
            }

//            JsonObject(mapValues { (_, value) -> value.decompressElementWithKvon() })
        }
        else -> this
    }

public inline fun <T : Any> Json.encodeToKvonString(serialiser: SerializationStrategy<T>, value: T): String =
    encodeToString(KvonSerialisationStrategy(serialiser), value)

public inline fun <reified T : Any> Json.encodeToKvonElement(serialiser: SerializationStrategy<T>, value: T): JsonElement =
    encodeToJsonElement(KvonSerialisationStrategy(serialiser), value)

public inline fun <reified T : Any> Json.encodeToKvonString(value: T): String =
    encodeToString(KvonSerialiser(serializersModule.serializer()), value)

public inline fun <reified T : Any> Json.encodeToKvonElement(value: T): JsonElement =
    encodeToJsonElement(KvonSerialiser(serializersModule.serializer()), value)

/** Decode */
public inline fun <T : Any> Json.decodeFromKvonString(deserialiser: DeserializationStrategy<T>, string: String): T =
    decodeFromString(KvonDeserialisationStrategy(deserialiser), string)

public inline fun <T : Any> Json.decodeFromKvonElement(deserialiser: DeserializationStrategy<T>, element: JsonElement): T =
    decodeFromJsonElement(KvonDeserialisationStrategy(deserialiser), element)

public inline fun <reified T : Any> Json.decodeFromKvonString(string: String): T =
    decodeFromString(KvonSerialiser(serializersModule.serializer()), string)

public inline fun <reified T : Any> Json.decodeFromKvonElement(element: JsonElement): T =
    decodeFromJsonElement(KvonSerialiser(serializersModule.serializer()), element)