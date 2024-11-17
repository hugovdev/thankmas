package me.hugo.savethekweebecs

import com.infernalsuite.aswm.api.SlimePlugin
import me.hugo.savethekweebecs.arena.GameManager
import me.hugo.savethekweebecs.arena.map.ArenaMap
import me.hugo.savethekweebecs.clickableitems.ItemSetManager
import me.hugo.savethekweebecs.commands.LobbyCommand
import me.hugo.savethekweebecs.commands.SaveTheKweebecsCommand
import me.hugo.savethekweebecs.dependencyinjection.SaveTheKweebecsModules
import me.hugo.savethekweebecs.lang.LanguageManager
import me.hugo.savethekweebecs.listeners.ArenaListener
import me.hugo.savethekweebecs.listeners.JoinLeaveListener
import me.hugo.savethekweebecs.music.SoundManager
import me.hugo.savethekweebecs.player.SaveTheKweebecsPlayerData
import me.hugo.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.savethekweebecs.team.TeamManager
import me.hugo.savethekweebecs.text.TextPopUpManager
import me.hugo.savethekweebecs.util.menus.MenuRegistry
import me.hugo.thankmas.ThankmasPlugin
import me.hugo.thankmas.player.PlayerDataManager
import org.bukkit.Bukkit
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.ksp.generated.module
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.BukkitCommandHandler
import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.CommandParameter
import revxrsal.commands.exception.CommandErrorException

public class SaveTheKweebecs : ThankmasPlugin(listOf("save_the_kweebecs")) {

    private val gameManager: GameManager by inject()
    private val teamManager: TeamManager by inject()

    private val languageManager: LanguageManager by inject()
    private val scoreboardManager: KweebecScoreboardManager by inject()

    private val soundManager: SoundManager by inject()
    private val textPopUp: TextPopUpManager by inject()

    private val menuRegistry: MenuRegistry by inject()
    private val itemManager: ItemSetManager by inject()

    private val joinLeaveListener: JoinLeaveListener by inject()

    public val playerManager: PlayerDataManager<SaveTheKweebecsPlayerData> = PlayerDataManager { SaveTheKweebecsPlayerData(it, this) }

    private lateinit var commandHandler: BukkitCommandHandler
    public lateinit var slimePlugin: SlimePlugin

    public companion object {
        private lateinit var main: SaveTheKweebecs

        public fun instance(): SaveTheKweebecs {
            return main
        }
    }

    override fun onEnable() {
        main = this
        logger.info("Starting Save The Kweebecs 2.0...")

        logger.info("Starting dependencies and injections!")
        startKoin {
            modules(SaveTheKweebecsModules().module)
        }

        val pluginManager = Bukkit.getPluginManager()
        slimePlugin = pluginManager.getPlugin("SlimeWorldManager") as SlimePlugin

        saveDefaultConfig()

        languageManager.setupLanguageFiles()
        scoreboardManager.initialize()
        itemManager.initialize()

        println("Loaded ${scoreboardManager.loadedTemplates.size} scoreboard templates!")

        commandHandler = BukkitCommandHandler.create(this)

        commandHandler.autoCompleter.registerSuggestion("locale") { _, _, _ -> languageManager.availableLanguages }

        commandHandler.registerValueResolver(TeamManager.Team::class.java) { context -> teamManager.teams[context.pop()] }
        commandHandler.autoCompleter.registerParameterSuggestions(TeamManager.Team::class.java,
            SuggestionProvider.of { teamManager.teams.keys })

        commandHandler.registerParameterValidator(TeamManager::class.java) { value, _: CommandParameter?, _: CommandActor? ->
            if (value == null) {
                throw CommandErrorException("This team doesn't exist!")
            }
        }

        commandHandler.registerValueResolver(ArenaMap::class.java) { context -> gameManager.maps[context.pop()] }
        commandHandler.autoCompleter.registerParameterSuggestions(ArenaMap::class.java,
            SuggestionProvider.of { gameManager.maps.keys })

        commandHandler.registerParameterValidator(ArenaMap::class.java) { value, _: CommandParameter?, _: CommandActor? ->
            if (value == null) {
                throw CommandErrorException("This map doesn't exist!")
            }
        }

        commandHandler.register(SaveTheKweebecsCommand())
        commandHandler.register(LobbyCommand())
        commandHandler.registerBrigadier()

        pluginManager.registerEvents(menuRegistry, this)
        pluginManager.registerEvents(joinLeaveListener, this)
        pluginManager.registerEvents(itemManager, this)
        pluginManager.registerEvents(ArenaListener(), this)

        soundManager.runTaskTimer(instance(), 0L, 2L)
        textPopUp.runTaskTimer(instance(), 0L, 5L)

        Bukkit.getScoreboardManager().mainScoreboard.teams.forEach { it.unregister() }
        Bukkit.getScoreboardManager().mainScoreboard.objectives.forEach { it.unregister() }

        println("Starting Game Manager... Maps: ${gameManager.maps.size}")
    }

    override fun onDisable() {
        commandHandler.unregisterAllCommands()

        Bukkit.getScoreboardManager().mainScoreboard.teams.forEach { it.unregister() }
        Bukkit.getScoreboardManager().mainScoreboard.objectives.forEach { it.unregister() }
    }


}