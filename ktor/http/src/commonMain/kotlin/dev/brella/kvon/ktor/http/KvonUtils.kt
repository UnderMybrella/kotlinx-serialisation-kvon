package dev.brella.kvon.ktor.http

import io.ktor.http.*

private val APPLICATION_KVON = ContentType.parse("application/kvon")

public val ContentType.Application.Kvon: ContentType get() = APPLICATION_KVON