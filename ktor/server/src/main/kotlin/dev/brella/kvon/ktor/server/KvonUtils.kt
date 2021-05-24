package dev.brella.kvon.ktor.server

import dev.brella.kotlinx.serialisation.kvon.Kvon
import dev.brella.kvon.ktor.http.Kvon
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.utils.io.*
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encodeToString
import kotlin.reflect.typeOf

public inline fun ApplicationCall.kvonConverter() =
    application.featureOrNull(ContentNegotiation.Feature)
        ?.registrations
        ?.firstOrNull { registration -> registration.contentType == ContentType.Application.Kvon }
        ?.converter

/**
 * Sends a [message] as a response
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> ApplicationCall.respondKvon(message: T) =
    respondText(ContentType.Application.Kvon) { Kvon.encodeToString(message) }

/**
 * Sends a [message] as a response
 */
@OptIn(ExperimentalStdlibApi::class)
@JvmName("respondWithType")
public suspend inline fun <reified T : Any> ApplicationCall.respondKvon(strategy: SerializationStrategy<T>, message: T) =
    respondText(ContentType.Application.Kvon) { Kvon.encodeToString(strategy, message) }