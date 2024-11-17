package me.hugo.savethekweebecs.task

import me.hugo.savethekweebecs.arena.Arena
import me.hugo.savethekweebecs.arena.ArenaState
import me.hugo.savethekweebecs.arena.GameManager
import me.hugo.savethekweebecs.extension.*
import me.hugo.savethekweebecs.util.InstantFirework
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Main Save The Kweebecs game loop.
 * Takes care of every event or game countdown.
 */
class GameControllerTask : KoinComponent, BukkitRunnable() {

    private val gameManager: GameManager by inject()

    override fun run() {
        Bukkit.getOnlinePlayers().filter { it.playerData()?.currentArena == null && it.location.y <= 10 }
            .forEach { player ->
                gameManager.hubLocation?.let { player.teleport(it) }
            }

        gameManager.arenas.values.forEach { arena ->
            val arenaState = arena.arenaState
            if (arenaState == ArenaState.WAITING || arenaState == ArenaState.RESETTING) return@forEach

            arena.arenaTime--

            val time = arena.arenaTime

            if (time == 0) {
                when (arenaState) {
                    ArenaState.STARTING -> arena.start()
                    ArenaState.IN_GAME -> {
                        val event = arena.currentEvent

                        if (event != null) {
                            event.eventRun.invoke(arena)
                            arena.eventIndex++

                            arena.arenaMap.events.getOrNull(arena.eventIndex)?.second?.let { arena.arenaTime = it }
                            arena.updateBoard("next_event", "time")
                        } else arena.end(arena.arenaMap.defenderTeam)
                    }

                    ArenaState.FINISHING -> arena.reset()
                    else -> {}
                }
            } else {
                arena.updateBoard("next_event", if (arenaState == ArenaState.IN_GAME) "time" else "count")

                if (arenaState == ArenaState.FINISHING) {
                    arena.playersPerTeam[arena.winnerTeam]?.mapNotNull { it.player() }?.randomOrNull()?.let {
                        InstantFirework(
                            FireworkEffect.builder().withColor(Color.ORANGE, Color.YELLOW).trail(true)
                                .withFade(Color.RED, Color.ORANGE).build(), it.location
                        )
                    }
                }

                if (time <= 60 && (time % 10 == 0 || time <= 5)) {
                    val translationName = if (arenaState != ArenaState.IN_GAME) arenaState.name.lowercase()
                    else "event.${arena.currentEvent?.name?.lowercase()}"

                    arena.playSound(Sound.BLOCK_NOTE_BLOCK_HAT)
                    arena.announceTranslation(
                        if (time == 1) "arena.$translationName.second" else "arena.$translationName.seconds",
                        Placeholder.unparsed("count", time.toString())
                    )
                }

                respawnPlayers(arena)
            }
        }
    }

    /**
     * Goes through every dead player and updates their
     * respawning screen or respawns them if ready.
     */
    private fun respawnPlayers(arena: Arena) {
        if (arena.arenaState == ArenaState.IN_GAME) {
            arena.deadPlayers.forEach deadPlayers@{ (player, secondsLeft) ->
                val newTime = secondsLeft - 1
                arena.deadPlayers[player] = newTime

                val playerData = player.playerDataOrCreate()
                val team = playerData.currentTeam ?: return@deadPlayers

                if (newTime == 0) {
                    arena.deadPlayers.remove(player)
                    arena.arenaMap.teamSpawnPoints[team.id]?.random()?.toLocation(arena.world!!)?.let {
                        player.teleport(it)
                        player.reset(GameMode.SURVIVAL)
                        team.giveItems(player)

                        val selectedVisual =
                            playerData.selectedTeamVisuals[team] ?: team.defaultPlayerVisual
                        player.inventory.helmet = selectedVisual.craftHead(player)

                        player.showTitle(
                            "arena.respawned.title", Title.Times.times(
                                0.seconds.toJavaDuration(),
                                1.5.seconds.toJavaDuration(),
                                0.25.seconds.toJavaDuration()
                            )
                        )

                        player.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)
                    }
                } else {
                    player.showTitle(
                        "arena.respawning.title", Title.Times.times(
                            0.seconds.toJavaDuration(),
                            1.0.seconds.toJavaDuration(),
                            0.25.seconds.toJavaDuration()
                        ), Placeholder.unparsed("respawn_time", newTime.toString())
                    )

                    player.playSound(Sound.BLOCK_NOTE_BLOCK_HAT)
                }
            }
        }
    }

}