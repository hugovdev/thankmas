package me.hugo.savethekweebecs.arena

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.lang.LanguageManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The different states an arena can be in.
 */
public enum class ArenaState(public val color: TextColor, public val material: Material, public val itemSetKey: String? = null) : KoinComponent {

    /** Arena is waiting for players to join the lobby. */
    WAITING(NamedTextColor.GREEN, Material.LIME_CONCRETE, "arena-lobby"),

    /** Enough players have joined the lobby and the game is starting soon. */
    STARTING(NamedTextColor.GOLD, Material.YELLOW_CONCRETE, "arena-lobby"),

    /** Game in the arena has started. */
    IN_GAME(NamedTextColor.RED, Material.ORANGE_CONCRETE),

    /** Game has finished and the victory is being celebrated. */
    FINISHING(NamedTextColor.RED, Material.RED_CONCRETE, "arena-lobby"),

    /** Game has finished and the map is being reset. */
    RESETTING(NamedTextColor.AQUA, Material.BLACK_CONCRETE);

    private val languageManager: LanguageManager by inject()
    private val miniMessage: MiniMessage
        get() = SaveTheKweebecs.instance().translations.translations.miniMessage

    /** Returns a friendly name for this arena state fetched from the language file. */
    public fun getFriendlyName(locale: String): Component {
        return miniMessage.deserialize(languageManager.getLangString("arena.state.${this.name.lowercase()}", locale))
    }

}