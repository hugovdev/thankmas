package me.hugo.creativelimiter.scoreboard

import me.hugo.creativelimiter.CreativeLimiter
import me.hugo.creativelimiter.listener.CreativeLimiting
import me.hugo.creativelimiter.player.CreativePlayer
import me.hugo.thankmas.scoreboard.ScoreboardTemplateManager
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.tag.Tag
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Single
public class CreativeScoreboardManager(private val instance: CreativeLimiter) :
    ScoreboardTemplateManager<CreativePlayer>(instance.playerDataManager), KoinComponent {

    public val creativeLimiting: CreativeLimiting by inject()

    override fun registerTags() {
        super.registerTags()

        registerTag("all_players") { _, _ ->
            Tag.selfClosingInserting {
                Component.text(instance.playerDataManager.getAllPlayerData().size)
            }
        }

        registerTag("entities") { _, _ ->
            Tag.selfClosingInserting {
                Component.text(creativeLimiting.currentEntities)
            }
        }
    }

    override fun loadTemplates() {
        loadTemplate("scoreboard.lines", "creative")
    }
}