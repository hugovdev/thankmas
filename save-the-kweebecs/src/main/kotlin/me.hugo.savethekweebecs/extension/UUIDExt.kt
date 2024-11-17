package me.hugo.savethekweebecs.extension

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.lang.LanguageManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.koin.java.KoinJavaComponent
import java.util.*

private val languageManager: LanguageManager by KoinJavaComponent.inject(LanguageManager::class.java)
private val miniMessage: MiniMessage
    get() = SaveTheKweebecs.instance().miniMessage

public fun UUID.translate(key: String, vararg tagResolver: TagResolver): Component {
    return miniMessage.deserialize(
        languageManager.getLangString(
            key,
            this.playerData()?.locale ?: LanguageManager.DEFAULT_LANGUAGE
        ), *tagResolver
    )
}