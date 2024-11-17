package me.hugo.savethekweebecs.arena.map

import com.infernalsuite.aswm.api.SlimePlugin
import com.infernalsuite.aswm.api.exceptions.SlimeException
import com.infernalsuite.aswm.api.world.SlimeWorld
import com.infernalsuite.aswm.api.world.properties.SlimeProperties
import com.infernalsuite.aswm.api.world.properties.SlimePropertyMap
import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.Arena
import me.hugo.savethekweebecs.arena.GameManager
import me.hugo.savethekweebecs.arena.events.ArenaEvent
import me.hugo.savethekweebecs.team.TeamManager
import me.hugo.thankmas.location.MapPoint
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Represents a Save The Kweebecs map.
 * All the data related to a map is saved here:
 * - Locations
 * - Attacker and defender teams.
 * - Minimum and Maximum players.
 * - Event timings and default countdowns.
 *
 * If [load] is true the map data will be fetched
 * from the config file.
 */
public class ArenaMap(public val configName: String, load: Boolean = true) : KoinComponent {

    private val gameManager: GameManager by inject()
    private val teamManager: TeamManager by inject()

    public companion object {
        /** Default Save The Kweebec map properties. */
        public val DEFAULT_PROPERTIES: SlimePropertyMap = SlimePropertyMap().apply {
            setValue(SlimeProperties.DIFFICULTY, "normal")
            setValue(SlimeProperties.ALLOW_ANIMALS, false)
            setValue(SlimeProperties.ALLOW_MONSTERS, false)
        }
    }

    /** Stays false if the map failed to load. */
    public var isValid: Boolean = false

    /** SlimeWorld to be cloned by every Arena. */
    public lateinit var slimeWorld: SlimeWorld

    /** Name used to identify the map internally. */
    public var mapName: String = configName.lowercase()

    /** Static map locations like Lobbies, Spectators, etc. */
    public val mapLocations: MutableMap<MapLocation, MapPoint> = mutableMapOf()

    /** List of Spawn-points per team. */
    public val teamSpawnPoints: MutableMap<String, MutableList<MapPoint>> = mutableMapOf()

    /** Team that defends NPCs. */
    public var defenderTeam: TeamManager.Team = teamManager.teams["trork"]!!

    /** Team that has to save every NPC. */
    public var attackerTeam: TeamManager.Team = teamManager.teams["kweebec"]!!

    /** List of events and the time before they occur. */
    public var events: MutableList<Pair<ArenaEvent, Int>> = mutableListOf()

    /** Spawn-points for every NPC to save. */
    public var kidnapedPoints: MutableList<MapPoint>? = null

    /** The minimum players required by this map to play. */
    public var minPlayers: Int = 6

    /** The maximum amount of players this map can hold. */
    public var maxPlayers: Int = 12

    /** Where the countdown starts at when the game is starting. */
    public var defaultCountdown: Int = 60

    init {
        if (load) {
            val main = SaveTheKweebecs.instance()

            main.logger.info("Loading map $configName...")

            loadMap(main) {
                main.logger.info("Slime map was loaded successfully!")
                main.logger.info("Fetching the rest of the game data...")

                val config = main.config
                val configPath = "maps.$configName"

                minPlayers = config.getInt("$configPath.minPlayers", 6)
                maxPlayers = config.getInt("$configPath.maxPlayers", 12)

                teamManager.teams[config.getString("$configPath.defenderTeam")]?.let { defenderTeam = it }
                teamManager.teams[config.getString("$configPath.attackerTeam")]?.let { attackerTeam = it }

                defaultCountdown = config.getInt("$configPath.defaultCountdown", 60)

                events = config.getStringList("$configPath.events").mapNotNull { ArenaEvent.deserialize(it) }
                    .toMutableList()

                config.getString("$configPath.mapName")?.let { mapName = it }

                kidnapedPoints = config.getStringList("$configPath.kidnapedPoints")
                    .mapNotNull { MapPoint.deserialize(it) }.toMutableList()

                // Read every location from the map and save it. (Waiting lobby, spectators, etc.)
                MapLocation.entries.forEach { location ->
                    MapPoint.deserialize("$configPath.${location.name.lowercase()}")?.let {
                        mapLocations[location] = it
                    }
                }

                // Read the spawn points for each team in the config file!
                teamManager.teams.values.forEach { team ->
                    teamSpawnPoints[team.id] = config.getStringList("$configPath.${team.id.lowercase()}")
                        .mapNotNull { MapPoint.deserialize(it) }.toMutableList()
                }

                main.logger.info("Map $configName has been loaded correctly and is now valid!")
                isValid = true

                val arena = Arena(this, mapName)
                gameManager.registerArena(arena)
            }
        }
    }

    /** Gets the bukkit location for [mapLocation] in [world]. */
    public fun getLocation(mapLocation: MapLocation, world: World?): Location? {
        if (world == null) return null
        return mapLocations[mapLocation]?.toLocation(world)
    }

    /**
     * Uses the SlimePlugin API to load the slime world that will
     * be used by every Arena running this map. (Asynchronously)
     *
     * Runs [onSuccessful] if the map was loaded correctly.
     */
    private fun loadMap(main: JavaPlugin, onSuccessful: () -> Unit) {
        val slimePlugin: SlimePlugin = Bukkit.getPluginManager().getPlugin("SlimeWorldManager") as SlimePlugin
        val slimeWorldName = SaveTheKweebecs.instance().config.getString("maps.$configName.slimeWorld")

        object : BukkitRunnable() {
            override fun run() {
                try {
                    slimeWorld = slimePlugin.loadWorld(
                        slimePlugin.getLoader("file"),
                        slimeWorldName, true, DEFAULT_PROPERTIES
                    )



                    object : BukkitRunnable() {
                        override fun run() {
                            onSuccessful()
                        }
                    }.runTask(main)
                } catch (e: SlimeException) {
                    main.logger.info("There was a problem trying to load the world: $slimeWorldName")
                    e.printStackTrace()
                }
            }
        }.runTaskAsynchronously(main)
    }
}