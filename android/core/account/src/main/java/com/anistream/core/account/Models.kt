package com.anistream.core.account

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class User(val id: String, val username: String)
data class Profile(val id: String, val name: String, val avatar: String, val kids: Boolean = false)
/** [activeProfile] is null until a profile is picked. */
data class Session(val token: String, val user: User, val activeProfile: Profile?)
data class Device(val id: String, val name: String, val kind: String)

internal fun str(o: JsonObject, k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()

internal fun JsonObject.toUser() = User(str(this, "id"), str(this, "username"))
internal fun JsonObject.toProfile() = Profile(str(this, "id"), str(this, "name"), str(this, "avatar"), this["kids"]?.jsonPrimitive?.booleanOrNull ?: false)
internal fun JsonObject.userObj() = this["user"]!!.jsonObject.toUser()

/**
 * Fluent Emoji 3D avatars (Microsoft, MIT) served from jsdelivr. The server stores only [id];
 * [folder] and [file] are the exact names in the fluentui-emoji repo (each verified to return 200).
 */
data class FluentEmoji(val id: String, val folder: String, val file: String) {
    val url: String get() = FluentEmojis.url(folder, file)
}

object FluentEmojis {
    private const val BASE = "https://cdn.jsdelivr.net/gh/microsoft/fluentui-emoji@main/assets"

    fun url(folder: String, file: String) = "$BASE/${folder.replace(" ", "%20")}/3D/${file}_3d.png"

    // every folder here has file = folder lowercased with '_' for spaces (hyphens, as in "Star-struck", stay)
    private fun e(folder: String) = folder.lowercase().replace(' ', '_').let { FluentEmoji(it, folder, it) }

    val all: List<FluentEmoji> = listOf(
        "Grinning face", "Smiling face with sunglasses", "Star-struck", "Nerd face", "Partying face", "Winking face",
        "Cat face", "Dog face", "Fox", "Panda", "Koala", "Lion", "Unicorn", "Frog", "Monkey face", "Penguin", "Owl", "Rabbit face",
        "Pizza", "Hamburger", "Cupcake", "Doughnut", "Avocado", "Popcorn"
    ).map(::e)

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
    fun urlFor(avatarId: String) = byId(avatarId).url
}
