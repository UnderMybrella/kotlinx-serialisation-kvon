package dev.brella.kotlinx.serialisation.kvon

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonTransformingSerializer

public class KvonSerialiser<T : Any>(keySerialiser: KSerializer<T>) : JsonTransformingSerializer<T>(keySerialiser) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        element.compressElementWithKvon()

    override fun transformDeserialize(element: JsonElement): JsonElement =
        element.decompressElementWithKvon()
}

public class KvonSerialisationStrategy<T>(private val keySerialiser: SerializationStrategy<T>) : SerializationStrategy<T> {
    override val descriptor: SerialDescriptor = keySerialiser.descriptor

    override fun serialize(encoder: Encoder, value: T) {
        val output = encoder as? JsonEncoder
                     ?: throw IllegalStateException(
                         "This serializer can be used only with Json format." +
                         "Expected Encoder to be JsonEncoder, got ${this::class}"
                     )
        var element = output.json.encodeToJsonElement(keySerialiser, value)
        element = element.compressElementWithKvon()
        output.encodeJsonElement(element)
    }
}

public class KvonDeserialisationStrategy<T>(private val keyDeserialiser: DeserializationStrategy<T>) : DeserializationStrategy<T> {
    override val descriptor: SerialDescriptor = keyDeserialiser.descriptor

    override fun deserialize(decoder: Decoder): T {
        val input = decoder as? JsonDecoder
                    ?: throw IllegalStateException(
                        "This serializer can be used only with Json format." +
                        "Expected Encoder to be JsonEncoder, got ${this::class}"
                    )
        val element = input.decodeJsonElement()
        return input.json.decodeFromJsonElement(keyDeserialiser, element.decompressElementWithKvon())
    }
}