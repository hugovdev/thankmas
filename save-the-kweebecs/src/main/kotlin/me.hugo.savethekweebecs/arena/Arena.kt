package me.hugo.savethekweebecs.arena

import com.infernalsuite.aswm.api.SlimePlugin
import com.infernalsuite.aswm.api.world.SlimeWorld
import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.events.ArenaEvent
import me.hugo.savethekweebecs.arena.map.ArenaMap
import me.hugo.savethekweebecs.arena.map.MapLocation
import me.hugo.savethekweebecs.clickableitems.ItemSetManager
import me.hugo.savethekweebecs.extension.*
import me.hugo.savethekweebecs.lang.LanguageManager
import me.hugo.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.savethekweebecs.team.TeamManager
import me.hugo.thankmas.location.MapPoint
import net.citizensnpcs.api.CitizensAPI
import net.citizensnpcs.api.npc.NPC
import net.citizensnpcs.api.trait.trait.Equipment
import net.citizensnpcs.trait.CurrentLocation
import net.citizensnpcs.trait.HologramTrait
import net.citizensnpcs.trait.LookClose
import net.citizensnpcs.trait.SkinTrait
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * A representation of a playable game of Save The
 * Kweebecs in [arenaMap] with [displayName] as a name.
 */
public class Arena(val arenaMap: ArenaMap, val displayName: String) : KoinComponent {

    private val main = SaveTheKweebecs.instance()
    private val slimePlugin: SlimePlugin = main.slimePlugin

    private val gameManager: GameManager by inject()
    private val languageManager: LanguageManager by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()
    private val itemManager: ItemSetManager by inject()

    val arenaUUID: UUID = UUID.randomUUID()

    private val slimeWorld: SlimeWorld? = arenaMap.slimeWorld!!.clone(arenaUUID.toString())

    var world: World? = null

    var arenaState: ArenaState = ArenaState.RESETTING
        set(state) {
            field = state
            arenaPlayers().mapNotNull { it.player() }.forEach { setCurrentBoard(it) }

            gameManager.refreshArenaIcon(this)
        }

    var winnerTeam: TeamManager.Team? = null

    var arenaTime: Int = arenaMap.defaultCountdown
    var eventIndex: Int = 0
        set(value) {
            field = value
            currentEvent = arenaMap.events.getOrNull(eventIndex)?.first
        }

    var currentEvent: ArenaEvent? = arenaMap.events[eventIndex].first

    val playersPerTeam: MutableMap<TeamManager.Team, MutableList<UUID>> = mutableMapOf(
        Pair(arenaMap.defenderTeam, mutableListOf()),
        Pair(arenaMap.attackerTeam, mutableListOf())
    )
    private val spectators: MutableList<UUID> = mutableListOf()
    val deadPlayers: ConcurrentMap<Player, Int> = ConcurrentHashMap()

    val remainingNPCs: MutableMap<NPC, Boolean> = mutableMapOf()

    var lastIcon: MutableMap<String, ItemStack> = mutableMapOf()

    init {
        main.logger.info("Creating game with map ${arenaMap.mapName} with display name $displayName...")
        createWorld(true)
        main.logger.info("$displayName is now available!")
    }

    /**
     * Attempts to add [player] to this game.
     *
     * It will fail if:
     * - The game has already started.
     * - The game is full.
     * - The player is already in a game.
     */
    fun joinArena(player: Player) {
        if (hasStarted()) {
            player.sendTranslated("arena.join.started", Placeholder.unparsed("arena_name", displayName))
            return
        }

        if (teamPlayers().size >= arenaMap.maxPlayers) {
            player.sendTranslated("arena.join.full", Placeholder.unparsed("arena_name", displayName))
            return
        }

        val playerData = player.playerData() ?: return

        if (playerData.currentArena != null) {
            player.sendTranslated("arena.join.alreadyInArena")
            return
        }

        val lobbyLocation = arenaMap.getLocation(MapLocation.LOBBY, world) ?: return

        player.reset(GameMode.ADVENTURE)
        player.teleport(lobbyLocation)

        playerData.currentArena = this

        val team = playersPerTeam.keys.minBy { playersPerTeam[it]?.size ?: 0 }
        addPlayerTo(player, team)

        announceTranslation(
            "arena.join.global",
            Placeholder.unparsed("player_name", player.name),
            Placeholder.unparsed("current_players", arenaPlayers().size.toString()),
            Placeholder.unparsed("max_players", arenaMap.maxPlayers.toString()),
        )

        itemManager.getSet(arenaState.itemSetKey)?.forEach { it.give(player) }

        if (teamPlayers().size >= arenaMap.minPlayers && arenaState == ArenaState.WAITING)
            arenaState = ArenaState.STARTING
        else updateBoard("players", "max_players")

        setCurrentBoard(player)
        gameManager.refreshArenaIcon(this)
    }

    /**
     * Removes [player] from the current game.
     *
     * If [disconnect] is false it will reset their
     * skin if needed, and it will send them to hub.
     */
    fun leave(player: Player, disconnect: Boolean = false) {
        val playerData = player.playerData() ?: return

        if (!hasStarted()) {
            playerData.currentTeam?.let { removePlayerFrom(player, it) }

            announceTranslation(
                "arena.leave.global",
                Placeholder.unparsed("player_name", player.name),
                Placeholder.unparsed("current_players", arenaPlayers().size.toString()),
                Placeholder.unparsed("max_players", arenaMap.maxPlayers.toString()),
            )
        } else {
            player.damage(player.health)
            deadPlayers.remove(player)

            if (!disconnect) playerData.resetSkin()

            teamPlayers().filter { it != player.uniqueId }.forEach {
                it.player()?.scoreboard?.getTeam(
                    if (it.playerData()?.currentTeam == playerData.currentTeam) "own" else "enemy"
                )?.removeEntry(player.name)
            }

            playerData.currentTeam?.let { removePlayerFrom(player, it) }

            val teamsWithPlayers = playersPerTeam.filter { it.value.isNotEmpty() }.map { it.key }

            if (teamsWithPlayers.size == 1) this.end(teamsWithPlayers.first())
        }

        if (!disconnect) {
            playerData.currentArena = null
            gameManager.sendToHub(player)
        }

        gameManager.refreshArenaIcon(this)
    }

    /**
     * Empties the player pools.
     * Unloads previous game worlds.
     *
     * Creates a new world with the proper gamerule setup
     * and spawns/restores NPCs.
     *
     * Also resets [arenaState], [arenaTime], [winnerTeam]
     * and [eventIndex].
     */
    public fun createWorld(firstTime: Boolean = true) {
        playersPerTeam.values.forEach { it.clear() }
        spectators.clear()
        deadPlayers.clear()

        if (!arenaMap.isValid) {
            main.logger.info("Map ${arenaMap.mapName} is not valid!")
            return
        }

        if (!firstTime) Bukkit.unloadWorld(world!!, false)

        slimePlugin.loadWorld(slimeWorld)

        val newWorld = Bukkit.getWorld(arenaUUID.toString()) ?: return

        newWorld.setGameRule(GameRule.SPECTATORS_GENERATE_CHUNKS, false)
        newWorld.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
        newWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        newWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
        newWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false)
        newWorld.setGameRule(GameRule.DO_FIRE_TICK, false)

        world = newWorld

        if (firstTime) arenaMap.kidnapedPoints?.forEach { remainingNPCs[createKweebecNPC(it)] = false }
        else remainingNPCs.keys.forEach {
            remainingNPCs[it] = false
            it.spawn(it.storedLocation.toLocation(world!!))
        }

        arenaState = ArenaState.WAITING
        arenaTime = arenaMap.defaultCountdown
        winnerTeam = null
        eventIndex = 0
    }

    /**
     * Creates an NPC in [mapPoint] for this arena.
     */
    private fun createKweebecNPC(mapPoint: MapPoint): NPC {
        val attackerTeam = arenaMap.attackerTeam
        val npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "")

        npc.data().setPersistent(NPC.Metadata.SHOULD_SAVE, false)
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false)
        npc.data().setPersistent(NPC.Metadata.ALWAYS_USE_NAME_HOLOGRAM, true)

        npc.data().setPersistent("arena", arenaUUID.toString())

        val visualToUse = attackerTeam.visuals.random()

        npc.getOrAddTrait(SkinTrait::class.java)?.apply {
            setSkinPersistent(
                attackerTeam.id,
                visualToUse.skin.signature,
                visualToUse.skin.value
            )
        }

        npc.getOrAddTrait(Equipment::class.java).set(Equipment.EquipmentSlot.HELMET, visualToUse.craftHead(null))
        npc.getOrAddTrait(CurrentLocation::class.java)

        npc.getOrAddTrait(LookClose::class.java).apply { lookClose(true) }

        npc.getOrAddTrait(HologramTrait::class.java).apply {
            lineHeight = -0.28

            addLine(
                LegacyComponentSerializer.legacySection().serialize(
                    SaveTheKweebecs.instance().translations.translations.miniMessage.deserialize(languageManager.getLangString("arena.npc.name.${attackerTeam.id}"))
                )
            )

            addLine(
                LegacyComponentSerializer.legacySection().serialize(
                    SaveTheKweebecs.instance().translations.translations.miniMessage.deserialize(languageManager.getLangString("arena.npc.action.${attackerTeam.id}"))
                )
            )

            setMargin(0, "bottom", 0.65)
        }

        npc.spawn(mapPoint.toLocation(world!!))

        return npc
    }

    /**
     * Updates [player]'s scoreboard to the one that
     * is being used in the current [arenaState].
     */
    private fun setCurrentBoard(player: Player) {
        scoreboardManager
        scoreboardManager.loadedTemplates[arenaState.name.lowercase()]?.printBoard(player)
    }

    /**
     * Updates the tags [tags] in every player's board.
     */
    fun updateBoard(vararg tags: String) {
        arenaPlayers().mapNotNull { it.player() }.forEach { it.updateBoardTags(*tags) }
    }

    /**
     * Returns a list of the participants in this arena.
     */
    fun teamPlayers(): List<UUID> {
        return playersPerTeam.values.flatten()
    }

    /**
     * Returns a list of every player in this arena, playing
     * or not playing.
     */
    fun arenaPlayers(): List<UUID> {
        return teamPlayers().plus(spectators)
    }

    /**
     * Adds [player] to [team].
     */
    private fun addPlayerTo(player: Player, team: TeamManager.Team) {
        addPlayerTo(player.uniqueId, team)
    }

    /**
     * Adds [uuid] to [team].
     */
    private fun addPlayerTo(uuid: UUID, team: TeamManager.Team) {
        playersPerTeam.computeIfAbsent(team) { mutableListOf() }.add(uuid)
        uuid.playerData()?.currentTeam = team
    }

    /**
     * Removes [player] from [team].
     */
    private fun removePlayerFrom(player: Player, team: TeamManager.Team) {
        removePlayerFrom(player.uniqueId, team)
    }

    /**
     * Removes [uuid] from [team].
     */
    private fun removePlayerFrom(uuid: UUID, team: TeamManager.Team) {
        playersPerTeam[team]?.remove(uuid)
        uuid.playerData()?.currentTeam = null
    }

    /**
     * Creates an ItemStack that represents
     * the arena in [locale] language.
     */
    fun getCurrentIcon(locale: String): ItemStack {
        val resolvers = arrayOf(
            Placeholder.unparsed("display_name", displayName),
            Placeholder.component("arena_state", arenaState.getFriendlyName(locale)),
            Placeholder.unparsed("map_name", arenaMap.mapName),
            Placeholder.unparsed("team_size", (arenaMap.maxPlayers / 2).toString()),
            Placeholder.unparsed("current_players", teamPlayers().size.toString()),
            Placeholder.unparsed("max_players", arenaMap.maxPlayers.toString()),
            Placeholder.unparsed("arena_short_uuid", arenaUUID.toString().split("-").first())
        )

        val item = ItemStack(arenaState.material)
            .name("menu.arenas.arenaIcon.name", locale, *resolvers)
            .putLore("menu.arenas.arenaIcon.lore", locale, *resolvers)

        lastIcon[locale] = item

        return item
    }

}