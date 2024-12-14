package me.hugo.creativelimiter

import me.hugo.creativelimiter.commands.LobbyCommand
import me.hugo.creativelimiter.dependencyinjection.CreativeModules
import me.hugo.creativelimiter.listener.CreativeLimiting
import me.hugo.creativelimiter.player.CreativePlayer
import me.hugo.creativelimiter.scoreboard.CreativeScoreboardManager
import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.listener.PlayerDataLoader
import me.hugo.thankmas.listener.PlayerLocaleDetector
import me.hugo.thankmas.listener.PlayerSpawnpointOnJoin
import me.hugo.thankmas.listener.RankedPlayerChat
import me.hugo.thankmas.player.PlayerDataManager
import me.hugo.thankmas.player.rank.PlayerGroupChange
import me.hugo.thankmas.world.registry.AnvilWorldRegistry
import org.bukkit.Bukkit
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.parameter.parametersOf
import org.koin.ksp.generated.module
import revxrsal.commands.bukkit.BukkitCommandHandler

public class CreativeLimiter : ThankmasPlugin<CreativePlayer>(listOf("creative")) {

    override val playerDataManager: PlayerDataManager<CreativePlayer> = PlayerDataManager { CreativePlayer(it, this) }
    override val scoreboardTemplateManager: CreativeScoreboardManager by inject { parametersOf(this) }

    private val anvilWorldRegistry: AnvilWorldRegistry by inject()

    private val spawnpointOnJoin: PlayerSpawnpointOnJoin by inject { parametersOf(creativeWorldName, "spawnpoint") }
    private var creativeWorldName: String = "world"

    private lateinit var commandHandler: BukkitCommandHandler


    override fun onLoad() {
        super.onLoad()

        loadKoinModules(CreativeModules().module)

        anvilWorldRegistry.loadMarkers(this.creativeWorldName)
    }

    override fun onEnable() {
        super.onEnable()

        scoreboardTemplateManager.initialize()

        Bukkit.getPluginManager().also {
            val creativeLimiting: CreativeLimiting by inject { parametersOf(this) }

            it.registerEvents(creativeLimiting, this)
            it.registerEvents(RankedPlayerChat(playerDataManager), this)
            it.registerEvents(PlayerDataLoader(this, playerDataManager), this)
            it.registerEvents(spawnpointOnJoin, this)
            it.registerEvents(PlayerLocaleDetector(this.playerDataManager), this)
        }

        // Register luck perms events!
        PlayerGroupChange(this.playerDataManager)

        commandHandler = BukkitCommandHandler.create(this)
        commandHandler.register(LobbyCommand())
    }

    override fun onDisable() {
        super.onDisable()
        commandHandler.unregisterAllCommands()
    }

}