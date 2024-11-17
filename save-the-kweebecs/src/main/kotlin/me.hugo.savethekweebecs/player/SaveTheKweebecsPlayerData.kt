package me.hugo.savethekweebecs.player

import com.destroystokyo.paper.profile.ProfileProperty
import dev.kezz.miniphrase.audience.sendTranslated
import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.Arena
import me.hugo.savethekweebecs.extension.*
import me.hugo.savethekweebecs.scoreboard.KweebecScoreboardManager
import me.hugo.savethekweebecs.team.TeamManager
import me.hugo.thankmas.items.*
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.rank.RankedPlayerData
import me.hugo.thankmas.player.reset
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.koin.core.component.inject
import java.util.*

/**
 * A class containing all the current stats, private
 * menus and data for [uuid].
 */
public class SaveTheKweebecsPlayerData(playerUUID: UUID, instance: SaveTheKweebecs) :
    RankedPlayerData<SaveTheKweebecsPlayerData>(playerUUID, instance.playerManager), TranslatedComponent {

    private val scoreboardManager: KweebecScoreboardManager by inject()
    private val itemManager: ItemSetRegistry by inject()
    private val teamManager: TeamManager by inject()

    public var currentArena: Arena? = null
    public var currentTeam: TeamManager.Team? = null
    public var lastAttack: PlayerAttack? = null

    private var playerSkin: TeamManager.SkinProperty? = null

    public val selectedTeamVisuals: MutableMap<TeamManager.Team, TeamManager.TeamVisual> =
        teamManager.teams.values.associateWith { it.defaultPlayerVisual }.toMutableMap()

    public var kills: Int = 0
        set(value) {
            field = value
            playerUUID.player()?.updateBoardTags("kills")
        }

    public var deaths: Int = 0
        set(value) {
            field = value
            playerUUID.player()?.updateBoardTags("deaths")
        }

    private var coins: Int = 0

    public fun getCoins(): Int {
        return coins
    }

    public fun resetCoins() {
        coins = 0
    }

    public fun addCoins(amount: Int, reason: String) {
        coins += amount

        val isNegative = amount < 0
        val displayedAmount = if (isNegative) amount * -1 else amount

        playerUUID.player()?.let {
            it.updateBoardTags("coins")
            it.sendTranslated(
                if (isNegative) "arena.gold.minus" else "arena.gold.plus",
                Placeholder.unparsed("amount", displayedAmount.toString()),
                Placeholder.component("reason", playerUUID.translate("arena.gold.reason.$reason"))
            )
        }
    }

    override fun setLocale(newLocale: Locale) {
        super.setLocale(newLocale)

        val arena = currentArena

        val currentBoard = lastBoardId ?: "lobby"
        scoreboardManager.getTemplate(currentBoard).printBoard(player = onlinePlayer, locale = newLocale)

        val itemSetManager: ItemSetRegistry by inject()
        itemSetManager.giveSetNullable(
            if (arena != null) arena.arenaState.itemSetKey else "lobby",
            onlinePlayer,
            newLocale
        )
    }

    override fun onPrepared(player: Player) {
        super.onPrepared(player)

        player.isPersistent = false
        player.reset(GameMode.ADVENTURE)

        val scoreboardManager: KweebecScoreboardManager by inject()
        scoreboardManager.getTemplate("lobby").printBoard(player)

        player.sendTranslated("welcome")

        // Give lobby item-set!
        val itemSetManager: ItemSetRegistry by inject()
        itemSetManager.giveSet("lobby", player)

        val textures = player.playerProfile.properties.firstOrNull { it.name == "textures" }

        if (textures == null) {
            println("Could not find textures for player ${player.name}!")
            return
        }

        playerSkin = TeamManager.SkinProperty(textures.value, textures.signature ?: "")
    }

    public fun resetSkin() {
        val skin = playerSkin ?: return
        setSkin(skin)
    }

    public fun setSkin(skin: TeamManager.SkinProperty) {
        val player = onlinePlayerOrNull ?: return

        val profile = player.playerProfile
        profile.setProperty(ProfileProperty("textures", skin.value, skin.signature))

        player.playerProfile = profile
    }

    public fun swapToLobbyBoard() {
        scoreboardManager.getTemplate("lobby").printBoard(onlinePlayer)
    }

    public data class PlayerAttack(val attacker: UUID, val time: Long = System.currentTimeMillis())
}