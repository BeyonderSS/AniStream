package com.anistream.core.parser

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArrayOrNull(): JsonArray? = this as? JsonArray

internal fun JsonObject?.fieldAsArrayOrNull(key: String): JsonArray? {
    val value = this?.get(key) ?: return null
    return value as? JsonArray
}

internal fun JsonObject?.fieldAsObjectOrNull(key: String): JsonObject? {
    val value = this?.get(key) ?: return null
    return value as? JsonObject
}

internal fun JsonObject?.fieldStringOrNull(key: String): String? {
    val value = this?.get(key) ?: return null
    return (value as? JsonPrimitive)?.contentOrNull
}

internal fun JsonObject?.fieldIntOrNull(key: String): Int? {
    val value = this?.get(key) ?: return null
    return (value as? JsonPrimitive)?.intOrNull
}

internal fun JsonObject?.fieldLongOrNull(key: String): Long? {
    val value = this?.get(key) ?: return null
    return (value as? JsonPrimitive)?.longOrNull
}

internal fun JsonObject?.fieldDoubleOrNull(key: String): Double? {
    val value = this?.get(key) ?: return null
    return (value as? JsonPrimitive)?.doubleOrNull
}

internal fun JsonObject?.fieldBooleanOrNull(key: String): Boolean? {
    val value = this?.get(key) ?: return null
    return (value as? JsonPrimitive)?.booleanOrNull
}
