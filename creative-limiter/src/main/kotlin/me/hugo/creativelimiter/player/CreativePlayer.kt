package me.hugo.creativelimiter.player

import me.hugo.creativelimiter.CreativeLimiter
import me.hugo.creativelimiter.scoreboard.CreativeScoreboardManager
import me.hugo.thankmas.config.ConfigurationProvider
import me.hugo.thankmas.database.PlayerData
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.player.cosmetics.CosmeticsPlayerData
import me.hugo.thankmas.player.reset
import me.hugo.thankmas.player.updateBoardTags
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import java.util.*

public class CreativePlayer(playerUUID: UUID, instance: CreativeLimiter) :
    CosmeticsPlayerData<CreativePlayer>(playerUUID, instance) {

    private val configProvider: ConfigurationProvider by inject()

    init {
        transaction {
            val playerData = PlayerData.selectAll().where { PlayerData.uuid eq playerUUID.toString() }.singleOrNull()

            loadCurrency(playerData)
            loadCosmetics(playerData)
        }
    }

    override fun onPrepared(player: Player) {
        super.onPrepared(player)

        player.isPersistent = false

        player.reset(GameMode.CREATIVE)

        val scoreboardManager: CreativeScoreboardManager by inject()
        scoreboardManager.getTemplate("creative").printBoard(player)

        // Give lobby item-set!
        val itemSetManager: ItemSetRegistry by inject { parametersOf(configProvider.getOrLoad("creative/config.yml")) }
        itemSetManager.giveSet("creative", player)

        updateBoardTags("all_players")
        giveCosmetic()
    }

    override fun onQuit(player: Player) {
        super.onQuit(player)
        updateBoardTags("all_players")
    }

}