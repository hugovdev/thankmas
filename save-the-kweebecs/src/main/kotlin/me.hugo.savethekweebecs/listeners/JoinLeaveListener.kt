package me.hugo.savethekweebecs.listeners

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.GameManager
import me.hugo.savethekweebecs.extension.arena
import me.hugo.savethekweebecs.extension.playerData
import me.hugo.savethekweebecs.extension.updateBoardTags
import me.hugo.savethekweebecs.music.SoundManager
import me.hugo.savethekweebecs.player.PlayerManager
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerLocaleChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


@Single
public class JoinLeaveListener : KoinComponent, Listener {

    private val playerManager: PlayerManager by inject()
    private val gameManager: GameManager by inject()
    private val soundManager: SoundManager by inject()

    public var onlinePlayers: Int = Bukkit.getOnlinePlayers().size

    @EventHandler
    public fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        // Create player data and save their skin!
        playerManager.getOrCreatePlayerData(event.uniqueId)
    }

    @EventHandler
    public fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(null)
        val player = event.player

        // Don't save any player data on worlds, etc.
        player.isPersistent = false

        player.playerData()?.initialize()
        gameManager.sendToHub(player)
        onlinePlayers++

        val instance = SaveTheKweebecs.instance()

        Bukkit.getServer().onlinePlayers.forEach {
            if (it.arena() == null) it.updateBoardTags("all_players")

            if (it.world === player.world) {
                it.showPlayer(instance, player)
                player.showPlayer(instance, it)
                return@forEach
            }

            it.hidePlayer(instance, player)
            player.hidePlayer(instance, it)
        }
    }

    @EventHandler
    public fun onLocaleChange(event: PlayerLocaleChangeEvent) {
        event.player.playerData()?.locale = event.locale().language
    }

    @EventHandler
    public fun onPlayerQuit(event: PlayerQuitEvent) {
        event.quitMessage(null)
        val player = event.player

        player.playerData()?.currentArena?.leave(player, true)
        playerManager.removePlayerData(player.uniqueId)
        onlinePlayers--

        Bukkit.getOnlinePlayers().filter { it != player }.filter { it.arena() == null }
            .forEach { it.updateBoardTags("all_players") }

        soundManager.stopTrack(player)
    }
}