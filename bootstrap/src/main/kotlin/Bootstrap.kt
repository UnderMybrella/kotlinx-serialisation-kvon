import com.fasterxml.jackson.databind.ObjectMapper
import dev.brella.kotlinx.serialisation.kvon.Kvon
import dev.brella.kotlinx.serialisation.kvon.compressElementWithKvon
import dev.brella.kotlinx.serialisation.kvon.decodeFromKvonString
import dev.brella.kotlinx.serialisation.kvon.decompressElementWithKvon
import dev.brella.kotlinx.serialisation.kvon.encodeToKvonElement
import dev.brella.kotlinx.serialisation.kvon.encodeToKvonString
import dev.brella.kvon.ktor.http.Kvon
import dev.brella.kvon.ktor.http.KvonFeature
import dev.brella.kvon.ktor.server.respondKvon
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.msgpack.jackson.dataformat.MessagePackFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimedValue
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@Serializable
public data class VeryImportantData(
    val identity: String? = null,
    val isTest: Boolean? = null,
    val question: String? = null
)

@Serializable
public data class CityMetric(val metricName: String, val metricValue: Double, val recorderComment: String? = null)

@OptIn(ExperimentalStdlibApi::class, kotlin.time.ExperimentalTime::class)
public suspend fun main() {
    val json = Json {
//        prettyPrint = true
    }

    val kvonBaseData = json.decodeGameUpdatesFromChronicler(File("bac30156-624e-4171-8f60-d660855b3fd5.json").readText())

    embeddedServer(Netty, port = 8000) {
        install(ContentNegotiation) {
            json(json)
            serialization(ContentType.Application.Kvon, Kvon(json))
        }

        routing {
            get("/json") {
                call.respondText(json.encodeToString(kvonBaseData), ContentType.Application.Json)
            }

            get("/kvon") {
                call.respondKvon(kvonBaseData)
            }

            get("/kvon_integrated") {
                call.respond(kvonBaseData)
            }
        }
    }.start(false)

    val client = HttpClient {
        install(KvonFeature) {
            this.json = json
        }
    }

    val retrieved = client.get<List<JsonObject>>("http://localhost:8000/kvon")
    println(retrieved)
    println(retrieved == kvonBaseData)

    println("Waiting...")
    readLine()

    val obj = listOf(
        VeryImportantData(identity = "test", isTest = true),
        VeryImportantData(identity = "test", isTest = false),
        VeryImportantData(identity = "test", isTest = false),
        VeryImportantData(identity = "test", isTest = null, question = "or is it?")
    )

    val possibleMetrics = listOf(
        "Water Level" to { Random.nextDouble(0.0, 100.0) },
        "Population Count" to { Random.nextInt(1920, 4096).toDouble() },
        "Did I Eat" to { if (Random.nextBoolean() && Random.nextBoolean()) 1.0 else 0.0 }
    )

    var index = possibleMetrics.indices.random()
    val cityMetrics = (0 until 100).map { i ->
        if (Random.nextBoolean()) index = possibleMetrics.indices.random()
        val (name, func) = possibleMetrics[index]

        CityMetric(name, func())
    }

    json.demonstrateKvonBrilliance("Simple Object", obj)
    json.demonstrateKvonBrilliance("City Metrics", cityMetrics)
    json.demonstrateKvonBrilliance("Miami Dale vs. Ohio Worms", json.decodeGameUpdatesFromChronicler(File("bac30156-624e-4171-8f60-d660855b3fd5.json").readText()), 100)
    json.demonstrateKvonBrilliance("json-iterator / test-data / large-file.json", json.decodeFromString<JsonArray>(File("large-file.json").readText()), 100)
    json.demonstrateKvonBrilliance("Sim Data", json.decodeGameUpdatesFromChroniclerV2(File("sim.json").readText()), 100)

    println("Well, that seems to be everything !")
    println("...")
    println("...")
    println("...wait")
    println("...oh no")
    println("...run !!")
    val totalLines = 3048396
    var i = 0

    val dir = File("feed")
    dir.mkdirs()

    var globalPrev: JsonElement? = null
    val prevByType: MutableMap<Int, JsonElement> = HashMap()

    val globalOut = PrintStream(File(dir, "global.kvon"))
    val outByType: MutableMap<Int, Pair<PrintStream, PrintStream>> = HashMap()

    println(
        "Took ${
            measureTime {
                try {
                    File("feed.json").forEachLine { line ->
                        print("\r${(i++ * 100.0 / totalLines.toDouble()).roundOut()}%")
                        val element = json.parseToJsonElement(line)

                        run {
                            val type = ((element as? JsonObject)?.get("type") as? JsonPrimitive)?.intOrNull ?: -1
                            val out = outByType.computeIfAbsent(type) { PrintStream(File(dir, "feed_$it.json")) to PrintStream(File(dir, "feed_$it.kvon")) }
                            val prev = prevByType[type]

                            out.first.println(json.encodeToString(element))
                            out.first.flush()

                            val compressed = element.compressElementWithKvon(prev)

                            if (compressed.decompressElementWithKvon(prev) != element) {
                                throw IllegalStateException("Uho!!")
                            }

                            out.second.println(json.encodeToString(compressed))
                            out.second.flush()

                            prevByType[type] = element
                        }

                        run {
                            val compressed = element.compressElementWithKvon(globalPrev)

                            if (compressed.decompressElementWithKvon(globalPrev) != element) {
                                throw IllegalStateException("Uho!!!")
                            }

                            globalOut.println(json.encodeToString(compressed))
                            globalOut.flush()

                            globalPrev = element
                        }
                    }

                    println()
                } finally {
                    globalOut.close()
                    outByType.values.forEach { it.first.close(); it.second.close() }
                }
            }
        }"
    )
}

public inline fun Json.decodeGameUpdatesFromChronicler(string: String): List<JsonObject> =
    decodeFromString<JsonObject>(string)
        .getValue("data")
        .jsonArray
        .mapNotNull { element -> (element as? JsonObject)?.get("data") as? JsonObject }

public inline fun Json.decodeGameUpdatesFromChroniclerV2(string: String): List<JsonObject> =
    decodeFromString<JsonObject>(string)
        .getValue("items")
        .jsonArray
        .mapNotNull { element -> (element as? JsonObject)?.get("data") as? JsonObject }

public inline fun String.compressGz(): ByteArray = this.encodeToByteArray().compressGz()
public fun ByteArray.compressGz(): ByteArray {
    val baos = ByteArrayOutputStream()
    GZIPOutputStream(baos).use { it.write(this) }
    return baos.toByteArray()
}

public inline fun String.hash(algorithm: String = "SHA-256"): String = encodeToByteArray().hash(algorithm)
public fun ByteArray.hash(algorithm: String = "SHA-256"): String {
    val md = MessageDigest.getInstance(algorithm)
    val hashBytes = md.digest(this)
    return String.format("%0${hashBytes.size shl 1}x", BigInteger(1, hashBytes))
}

@OptIn(ExperimentalTime::class)
public inline fun <reified T : Any> Json.demonstrateKvonBrilliance(header: String?, obj: T, outputLimit: Int = 1_000) {
    val (normal, kvon, normalMsgPack, kvonMsgPack) = encodeTwiceWithMsgPack(obj)
    val kvonDecoded = encodeToString(decodeFromKvonString<T>(kvon.value))

    header?.let { println("=====[ $it ]=====") }

    val normalBytes = normal.value.encodeToByteArray()
    val kvonBytes = kvon.value.encodeToByteArray()
    val kvonDecodedBytes = kvonDecoded.encodeToByteArray()

//    File("$header.json").writeBytes(normalBytes)
//    File("$header.kvon").writeBytes(kvonBytes)

    val normalGz = measureTimedValue { normalBytes.compressGz() }
    val kvonGz = measureTimedValue { kvonBytes.compressGz() }
    val kvonDecodedGz = measureTimedValue { kvonDecodedBytes.compressGz() }
    val normalMsgPackGz = measureTimedValue { normalMsgPack.value.compressGz() }
    val kvonMsgPackGz = measureTimedValue { kvonMsgPack.value.compressGz() }

    val hashAlgorithm = "MD5"

    val normalHash = normalBytes.hash(hashAlgorithm)
    val kvonHash = kvonBytes.hash(hashAlgorithm)
    val kvonDecodedHash = kvonDecodedBytes.hash(hashAlgorithm)

    val normalMsgPackHash = normalMsgPack.value.hash(hashAlgorithm)
    val kvonMsgPackHash = kvonMsgPack.value.hash(hashAlgorithm)

    if (normalHash != kvonDecodedHash) {
        println("... running check ...")
        if (parseToJsonElement(normal.value) != parseToJsonElement(kvonDecoded)) {
            println("==== WARNING ====")
            println(normalHash)
            println("/////////////////")
            println(kvonDecodedHash)

            File("$normalHash.json").writeBytes(normalBytes)
            File("$kvonDecodedHash.kvon").writeBytes(kvonDecodedBytes)

            throw IllegalStateException("Izzy you fucked up!")
        } else {
            println("Nothing to worry about!")
        }
    }

    val format: (name: String, taken: Duration, hash: String, raw: ByteArray, gz: TimedValue<ByteArray>) -> Unit = { name, taken, hash, raw, gz ->
        println("$name ($hash)")
        println("\tEncoding took $taken")
        println("\tEncoding w/ gz took ${gz.duration} (${taken + gz.duration} total)")
        println("\t${raw.size.byteLength(9)} raw [${(raw.size * 100.0 / normalBytes.size).roundOut().toString().padStart(5, ' ')}%]")
        println("\t${gz.value.size.byteLength(9)} gz  [${(gz.value.size * 100.0 / normalBytes.size).roundOut().toString().padStart(5, ' ')}%]")

    }

    format("Normal JSON", normal.duration, normalHash, normalBytes, normalGz)
    format("KVON", kvon.duration, kvonHash, kvonBytes, kvonGz)
    format("Normal JSON w/ MsgPack", normalMsgPack.duration, normalMsgPackHash, normalMsgPack.value, normalMsgPackGz)
    format("KVON w/ MsgPack", kvonMsgPack.duration, kvonMsgPackHash, kvonMsgPack.value, kvonMsgPackGz)

    println()
    println()
    println()
}

public const val KILOBYTE: Double = 1000.0
public const val MEGABYTE: Double = KILOBYTE * 1000.0
public const val GIGABYTE: Double = MEGABYTE * 1000.0

public inline fun Double.roundOut(): Double = (this * 100).roundToInt() / 100.0

public fun Int.byteLength(padLength: Int = 0): String {
    val bytes = this@byteLength.toLong()

    val gigabytes = (bytes / GIGABYTE)
    if (gigabytes > 0.9999) return "${gigabytes.roundOut()} GB".padStart(padLength, ' ')

    val megabytes = bytes / MEGABYTE
    if (megabytes > 0.9999) return "${megabytes.roundOut()} MB".padStart(padLength, ' ')

    val kilobytes = bytes / KILOBYTE
    if (kilobytes > 0.9999) return "${kilobytes.roundOut()} kB".padStart(padLength, ' ')

    return "$bytes B".padStart(padLength, ' ')
}

public inline fun <reified T : Any> Json.encodeTwice(obj: T): Pair<String, String> =
    Pair(
        encodeToString(obj),
        encodeToKvonString(obj)
    )

@OptIn(ExperimentalTime::class)
public inline fun <reified T : Any> Json.encodeTwiceWithMsgPack(obj: T): MsgPackEncodeResults =
    MsgPackEncodeResults(
        measureTimedValue { encodeToString(obj) },
        measureTimedValue { encodeToKvonString(obj) },
        measureTimedValue { encodeToJsonElement(obj).pack() },
        measureTimedValue { encodeToKvonElement(obj).pack() }
    )

public val jacksonMsgPacker: ObjectMapper = ObjectMapper(MessagePackFactory())

public fun JsonElement.shake(): Any? =
    when (this) {
        is JsonObject -> mapValues { (_, element) -> element.shake() }
        is JsonArray -> map { it.shake() }
        is JsonPrimitive -> booleanOrNull ?: floatOrNull ?: doubleOrNull ?: longOrNull ?: contentOrNull
    }

public inline fun JsonElement.pack(): ByteArray =
    jacksonMsgPacker.writeValueAsBytes(this.shake())

/*public fun JsonElement.pack(): ByteArray = MessagePack.newDefaultBufferPacker().packJsonElement(this).toByteArray()
public fun <T : MessagePacker> T.packJsonElement(element: JsonElement): T {
    when (element) {
        is JsonObject -> {
            packMapHeader(element.size)
            element.forEach { (k, v) ->
                packString(k)
                packJsonElement(v)
            }
        }
        is JsonArray -> {
            packArrayHeader(element.size)
            element.forEach(::packJsonElement)
        }
        is JsonNull -> packNil()
        is JsonPrimitive ->
            element.booleanOrNull?.let(::packBoolean)
            ?: element.intOrNull?.let(::packInt)
            ?: element.floatOrNull?.let(::packFloat)
            ?: element.doubleOrNull?.let(::packDouble)
            ?: packString(element.content)
    }

    return this
}*/

@ExperimentalTime
public data class MsgPackEncodeResults(val normal: TimedValue<String>, val kvon: TimedValue<String>, val normalMsg: TimedValue<ByteArray>, val kvonMsg: TimedValue<ByteArray>)