package me.hugo.savethekweebecs.arena

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.map.ArenaMap
import me.hugo.savethekweebecs.extension.*
import me.hugo.savethekweebecs.lang.LanguageManager
import me.hugo.savethekweebecs.music.SoundManager
import me.hugo.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.savethekweebecs.task.GameControllerTask
import me.hugo.thankmas.gui.paginated.PaginatedMenu
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.location.MapPoint
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scoreboard.DisplaySlot
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

@Single
public class GameManager : KoinComponent {

    private val main: SaveTheKweebecs = SaveTheKweebecs.instance()

    private val itemManager: ItemSetRegistry by inject()
    private val soundManager: SoundManager by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()

    /** The main hub location. */
    public val hubLocation: Location? = Bukkit.getWorld("world")
        ?.let { MapPoint.deserialize("hubLocation")?.toLocation(it) }

    /** Every available map related to their internal name. */
    public val maps: Map<String, ArenaMap>

    /** List of all arenas related to their UUID. */
    public val arenas: ConcurrentMap<UUID, Arena> = ConcurrentHashMap()

    /** Arena menus for each available language. */
    private val arenaMenus: MutableMap<String, PaginatedMenu> = mutableMapOf()

    init {
        val config = main.config

        val mapKeys = config.getConfigurationSection("maps")?.getKeys(false)
        maps = mapKeys?.associateWith { ArenaMap(it) } ?: mapOf()

        languageManager.availableLanguages.forEach {
            arenaMenus[it] = PaginatedMenu(
                9 * 4, "menu.arenas.title", PaginatedMenu.PageFormat.TWO_ROWS_TRIMMED.format,
                ItemStack(Material.ENDER_EYE)
                    .name("menu.arenas.icon.name", it)
                    .putLore("menu.arenas.icon.lore", it), null, it
            )
        }

        GameControllerTask().runTaskTimer(main, 0L, 20L)
    }

    /**
     * Opens the arena menu to [player] using their
     * current language or the default if unavailable.
     */
    public fun openArenasMenu(player: Player) {
        (arenaMenus[player.playerDataOrCreate().locale] ?: arenaMenus[LanguageManager.DEFAULT_LANGUAGE]!!).open(player)
    }

    /** Refreshes the icon for [arena] in every arena menu. */
    public fun refreshArenaIcon(arena: Arena) {
        arenaMenus.forEach {
            arena.lastIcon[it.key]?.let { lastIcon ->
                it.value.replaceFirst(lastIcon, Icon(arena.getCurrentIcon(it.key)).addClickAction { player, _ ->
                    arena.joinArena(player)
                })
            }
        }
    }

    /** Registers [arena] in every arena menu. */
    public fun registerArena(arena: Arena) {
        arenas[arena.arenaUUID] = arena

        arenaMenus.forEach {
            it.value.addItem(Icon(arena.getCurrentIcon(it.key)).addClickAction { player, _ ->
                arena.joinArena(player)
            })
        }
    }

    /**
     * Sends [player] to hub removing their scoreboard entries,
     * teleporting them, resetting their inventory, stats and
     * sounds and giving them the configurable "lobby" ItemSet.
     */
    public fun sendToHub(player: Player) {
        if (!player.isOnline) return

        removeScoreboardEntries(player)

        hubLocation?.let { player.teleport(it) }
        player.reset(GameMode.ADVENTURE)

        val playerData = player.playerData() ?: return

        playerData.kills = 0
        playerData.deaths = 0
        playerData.resetCoins()

        soundManager.stopTrack(player)

        scoreboardManager.getTemplate("lobby").printBoard(player)
        itemManager.giveSet("lobby", player)
    }

    /**
     * Removes the scoreboard teams used when playing
     * a game of Save The Kweebecs.
     */
    private fun removeScoreboardEntries(player: Player) {
        val scoreboard = player.scoreboard

        scoreboard.getTeam("own")?.let { team ->
            team.removeEntries(team.entries)
            team.unregister()
        }

        scoreboard.getTeam("enemy")?.let { team ->
            team.removeEntries(team.entries)
            team.unregister()
        }

        scoreboard.clearSlot(DisplaySlot.BELOW_NAME)
    }
}