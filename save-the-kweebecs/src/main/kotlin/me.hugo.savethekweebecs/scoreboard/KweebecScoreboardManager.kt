package me.hugo.savethekweebecs.scoreboard

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.ArenaState
import me.hugo.savethekweebecs.extension.arena
import me.hugo.savethekweebecs.extension.playerDataOrCreate
import me.hugo.savethekweebecs.player.SaveTheKweebecsPlayerData
import me.hugo.thankmas.player.translate
import me.hugo.thankmas.scoreboard.ScoreboardTemplate
import me.hugo.thankmas.scoreboard.ScoreboardTemplateManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent

/**
 * Registry of every [ScoreboardTemplate] used in the plugin.
 */
@Single
public class KweebecScoreboardManager(private val instance: SaveTheKweebecs) :
    ScoreboardTemplateManager<SaveTheKweebecsPlayerData>(instance.playerManager), KoinComponent {

    override fun loadTemplates() {
        loadTemplate("scoreboard.lines", "lobby")

        ArenaState.entries.forEach { state ->
            val friendlyName = state.name.lowercase()
            val key = "scoreboard.$friendlyName.lines"

            loadTemplate(key, friendlyName)
        }
    }

    override fun registerTags() {
        super.registerTags()

        registerTag("count") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.arenaTime ?: 0)
            }
        }

        registerTag("npcs_saved") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.remainingNPCs?.count { it.value } ?: 0)
            }
        }

        registerTag("total_npcs") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.remainingNPCs?.size ?: 0)
            }
        }

        registerTag("next_event") { player, locale ->
            Tag.selfClosingInserting {
                player.translate(
                    "arena.event.${player.arena()?.currentEvent?.name?.lowercase() ?: "unknwon"}.name",
                    locale
                )
            }
        }

        registerTag("coins") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(
                    player.playerDataOrCreate().getCoins().toString()
                )
            }
        }

        registerTag("kills") { player, _ -> Tag.selfClosingInserting { Component.text(player.playerDataOrCreate().kills) } }

        registerTag("time") { player, _ ->
            val totalSeconds = player.arena()?.arenaTime ?: 0

            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60

            Tag.selfClosingInserting { Component.text(String.format("%02d:%02d", minutes, seconds)) }
        }

        registerTag("players") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.teamPlayers()?.size ?: 0)
            }
        }

        registerTag("max_players") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.arenaMap?.maxPlayers ?: 0)
            }
        }

        registerTag("map_name") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.arenaMap?.mapName ?: "")
            }
        }

        registerTag("display_name") { player, _ ->
            Tag.selfClosingInserting {
                Component.text(player.arena()?.displayName ?: "Custom Game")
            }
        }
    }
}