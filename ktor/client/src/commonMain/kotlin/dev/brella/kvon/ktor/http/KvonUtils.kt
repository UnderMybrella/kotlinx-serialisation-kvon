package dev.brella.kvon.ktor.http

import dev.brella.kotlinx.serialisation.kvon.decodeFromKvonString
import dev.brella.kotlinx.serialisation.kvon.encodeToKvonString
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * [HttpClient] feature that serializes/de-serializes as Kvon custom objects
 * to request and from response bodies using a [serializer].
 *
 * The default [serializer] is [GsonSerializer].
 *
 * The default [acceptContentTypes] is a list which contains [ContentType.Application.Kvon]
 *
 * Note: It will de-serialize the body response if the specified type is a public accessible class
 *       and the Content-Type is one of [acceptContentTypes] list (`application/Kvon` by default).
 *
 * @property serializer that is used to serialize and deserialize request/response bodies
 * @property acceptContentTypes that are allowed when receiving content
 */
public class KvonFeature internal constructor(
    public val json: Json,
    public val acceptContentTypes: List<ContentType> = listOf(ContentType.Application.Kvon)
) {
    internal constructor(config: Config) : this(
        config.json,
        config.acceptContentTypes
    )

    /**
     * [KvonFeature] configuration that is used during installation
     */
    public class Config {
        public var json: Json = Json.Default

        /**
         * Backing field with mutable list of content types that are handled by this feature.
         */
        private val _acceptContentTypes: MutableList<ContentType> = mutableListOf(ContentType.Application.Kvon)

        /**
         * List of content types that are handled by this feature.
         * It also affects `Accept` request header value.
         * Please note that wildcard content types are supported but no quality specification provided.
         */
        public var acceptContentTypes: List<ContentType>
            set(value) {
                require(value.isNotEmpty()) { "At least one content type should be provided to acceptContentTypes" }

                _acceptContentTypes.clear()
                _acceptContentTypes.addAll(value)
            }
            get() = _acceptContentTypes


        /**
         * Adds accepted content types. Be aware that [ContentType.Application.Kvon] accepted by default is removed from
         * the list if you use this function to provide accepted content types.
         * It also affects `Accept` request header value.
         */
        public fun accept(vararg contentTypes: ContentType) {
            _acceptContentTypes += contentTypes
        }
    }

    internal fun canHandle(contentType: ContentType): Boolean =
        acceptContentTypes.any { contentType.match(it) }

    /**
     * Companion object for feature installation
     */
    public companion object Feature : HttpClientFeature<Config, KvonFeature> {
        override val key: AttributeKey<KvonFeature> = AttributeKey("Kvon")

        override fun prepare(block: Config.() -> Unit): KvonFeature {
            val config = Config().apply(block)
            val serializer = config.json
            val allowedContentTypes = config.acceptContentTypes.toList()

            return KvonFeature(serializer, allowedContentTypes)
        }

        @OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
        override fun install(feature: KvonFeature, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Transform) { payload ->
                feature.acceptContentTypes.forEach { context.accept(it) }

                val contentType = context.contentType() ?: return@intercept
                if (!feature.canHandle(contentType)) return@intercept

                context.headers.remove(HttpHeaders.ContentType)

                val serializedContent = when (payload) {
                    Unit -> EmptyContent
                    is EmptyContent -> EmptyContent
                    else -> TextContent(feature.json.encodeToKvonString(buildSerializer(payload, feature.json.serializersModule), payload), contentType)
                }

                proceedWith(serializedContent)
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, body) ->
                if (body !is ByteReadChannel) return@intercept

                val contentType = context.response.contentType() ?: return@intercept
                if (!feature.canHandle(contentType)) return@intercept

                val text = body.readRemaining().readText()
                val deserializationStrategy = feature.json.serializersModule.getContextual(info.type)
                val mapper = deserializationStrategy ?: (info.kotlinType?.let { serializer(it) } ?: info.type.serializer())
                val parsedBody = feature.json.decodeFromKvonString(mapper, text)!!
                val response = HttpResponseContainer(info, parsedBody)
                proceedWith(response)
            }
        }
    }
}

/**
 * Install [KvonFeature].
 */
public fun HttpClientConfig<*>.Kvon(block: KvonFeature.Config.() -> Unit) {
    install(KvonFeature, block)
}

private fun buildSerializer(value: Any, module: SerializersModule): KSerializer<Any> = when (value) {
    is JsonElement -> JsonElement.serializer()
    is List<*> -> ListSerializer(value.elementSerializer(module))
    is Array<*> -> value.firstOrNull()?.let { buildSerializer(it, module) } ?: ListSerializer(String.serializer())
    is Set<*> -> SetSerializer(value.elementSerializer(module))
    is Map<*, *> -> {
        val keySerializer = value.keys.elementSerializer(module)
        val valueSerializer = value.values.elementSerializer(module)
        MapSerializer(keySerializer, valueSerializer)
    }
    else -> {
        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        module.getContextual(value::class) ?: value::class.serializer()
    }
} as KSerializer<Any>

@OptIn(ExperimentalSerializationApi::class)
private fun Collection<*>.elementSerializer(module: SerializersModule): KSerializer<*> {
    val serializers: List<KSerializer<*>> =
        filterNotNull().map { buildSerializer(it, module) }.distinctBy { it.descriptor.serialName }

    if (serializers.size > 1) {
        error(
            "Serializing collections of different element types is not yet supported. " +
            "Selected serializers: ${serializers.map { it.descriptor.serialName }}"
        )
    }

    val selected = serializers.singleOrNull() ?: String.serializer()

    if (selected.descriptor.isNullable) {
        return selected
    }

    @Suppress("UNCHECKED_CAST")
    selected as KSerializer<Any>

    if (any { it == null }) {
        return selected.nullable
    }

    return selected
}