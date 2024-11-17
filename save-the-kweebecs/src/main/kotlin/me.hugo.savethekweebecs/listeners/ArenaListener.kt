package me.hugo.savethekweebecs.listeners

import com.destroystokyo.paper.MaterialSetTag
import com.destroystokyo.paper.MaterialTags
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent
import io.papermc.paper.event.player.AsyncChatEvent
import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.arena.Arena
import me.hugo.savethekweebecs.arena.GameManager
import me.hugo.savethekweebecs.extension.*
import me.hugo.savethekweebecs.music.SoundManager
import me.hugo.savethekweebecs.player.SaveTheKweebecsPlayerData
import net.citizensnpcs.api.event.NPCRightClickEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


public class ArenaListener : KoinComponent, Listener {

    private val gameManager: GameManager by inject()
    private val soundManager: SoundManager by inject()

    private companion object {
        private val BREAKABLE_ATTACKER_BLOCKS = MaterialSetTag(NamespacedKey("stk", "attacker_breakable"))
            .add(MaterialTags.FENCES).add(Material.BAMBOO_PLANKS, Material.IRON_BARS).lock()
    }

    @EventHandler
    public fun onPhysicalInteraction(event: PlayerInteractEvent) {
        if (event.action != Action.PHYSICAL) return

        if (event.player.playerData()?.currentArena == null) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler
    public fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as Player
        val playerData = player.playerDataOrCreate()

        if (playerData.currentArena?.isInGame() == true) return

        event.isCancelled = true
    }

    @EventHandler
    public fun onDamage(event: EntityDamageEvent) {
        val player = event.entity
        if (player !is Player) return

        val playerData = player.playerDataOrCreate()
        val arena: Arena? = player.arena()

        val isDying = player.health - event.finalDamage <= 0

        if (arena?.isInGame() == true && player.gameMode != GameMode.SPECTATOR) {
            if (event is EntityDamageByEntityEvent) {
                val attacker = event.damager

                val playerSource: Player? = if (attacker is Player) attacker
                else if (attacker is Projectile) {
                    if (isDying && attacker is Arrow) attacker.remove()

                    val shooter = attacker.shooter
                    if (shooter is Player) shooter
                    else null
                } else null

                if (playerSource?.playerData()?.currentTeam == playerData.currentTeam) {
                    event.isCancelled = true
                    return
                }

                playerSource?.let { playerData.lastAttack = SaveTheKweebecsPlayerData.PlayerAttack(it.uniqueId) }
            }

            if (isDying) {
                event.isCancelled = true

                val deathLocation = player.location

                playerData.deaths++
                player.gameMode = GameMode.SPECTATOR
                arena.deadPlayers[player] = 8

                deathLocation.world.playEffect(deathLocation, Effect.STEP_SOUND, Material.REDSTONE_BLOCK)
                deathLocation.world.playEffect(
                    deathLocation.clone().add(0.0, 1.0, 0.0),
                    Effect.STEP_SOUND,
                    Material.REDSTONE_BLOCK
                )

                val lastAttack = playerData.lastAttack
                val attacker = lastAttack?.attacker?.player()

                // If there is no last attack, it was too long ago or the attacker has disconnected, player died
                // by themselves!
                if (lastAttack == null || lastAttack.time < System.currentTimeMillis() - (10000) || attacker == null) {
                    arena.announceTranslation(
                        "arena.death.self",
                        Placeholder.unparsed("player_team_icon", playerData.currentTeam?.chatIcon ?: ""),
                        Placeholder.unparsed("player", player.name)
                    )
                } else {
                    val attackerData = attacker.playerDataOrCreate()

                    attackerData.kills++

                    soundManager.playSoundEffect("save_the_kweebecs.kill", attacker)

                    player.world.playSound(player.location, Sound.ENTITY_GENERIC_HURT, 1.0f, 1.0f)
                    player.world.playSound(player.location, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f)

                    attacker.showTitle(
                        "arena.kill.title",
                        Title.Times.times(
                            0.5.seconds.toJavaDuration(),
                            1.5.seconds.toJavaDuration(),
                            0.5.seconds.toJavaDuration()
                        )
                    )

                    arena.announceTranslation(
                        "arena.death.player",
                        Placeholder.unparsed("player", player.name),
                        Placeholder.unparsed("player_team_icon", playerData.currentTeam?.chatIcon ?: ""),
                        Placeholder.unparsed("killer_team_icon", attackerData.currentTeam?.chatIcon ?: ""),
                        Placeholder.unparsed("killer", attacker.name)
                    )

                    attackerData.addCoins(10, "kill")
                }
            }

            return
        }

        event.isCancelled = true
    }

    @EventHandler
    public fun onNPCClick(event: NPCRightClickEvent) {
        val npc = event.npc

        if (event.clicker.gameMode == GameMode.SPECTATOR) return

        if (npc.data().has("arena")) {
            val arena = gameManager.arenas[UUID.fromString(npc.data().get("arena"))] ?: return

            if (!arena.isInGame()) return

            val player = event.clicker
            val attackerTeam = arena.arenaMap.attackerTeam
            if (player.playerDataOrCreate().currentTeam != attackerTeam) return

            npc.despawn()
            arena.remainingNPCs[npc] = true

            val location = npc.storedLocation

            if (arena.remainingNPCs.any { !it.value }) soundManager.playSoundEffect(
                "save_the_kweebecs.kweebec_saved",
                player
            )

            if (location.block.type == Material.FIRE) location.block.type = Material.AIR

            arena.announceTranslation(
                "arena.${attackerTeam.id}.saved",
                Placeholder.unparsed("player", player.name),
                Placeholder.unparsed("player_team_icon", attackerTeam.chatIcon),
                Placeholder.unparsed("npcs_saved", arena.remainingNPCs.count { it.value }.toString()),
                Placeholder.unparsed("total_npcs", arena.remainingNPCs.size.toString())
            )

            player.playerData()?.addCoins(15, "saved_${attackerTeam.id}")

            location.world.spawnParticle(Particle.CLOUD, location.clone().add(0.0, 0.55, 0.0), 5, 0.05, 0.1, 0.05, 0.02)

            if (arena.remainingNPCs.all { it.value }) arena.end(attackerTeam)
            else arena.updateBoard("npcs_saved", "total_npcs")
        }
    }

    @EventHandler
    public fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player

        if (player.gameMode == GameMode.CREATIVE) return

        val playerData = player.playerDataOrCreate()

        val currentArena = playerData.currentArena

        if (currentArena?.isInGame() == true) {
            if (playerData.currentTeam == currentArena.arenaMap.defenderTeam && event.block.type == Material.BAMBOO_PLANKS) return
        }

        event.isCancelled = true
    }

    @EventHandler
    public fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player

        if (player.gameMode == GameMode.CREATIVE) return

        val playerData = player.playerDataOrCreate()

        val currentArena = playerData.currentArena

        if (currentArena?.isInGame() == true) {
            if (playerData.currentTeam == currentArena.arenaMap.attackerTeam &&
                BREAKABLE_ATTACKER_BLOCKS.isTagged(event.block)
            ) {
                event.isDropItems = false
                return
            }
        }

        event.isCancelled = true
    }

    @EventHandler
    public fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as Player
        val arena = player.arena()

        if (player.gameMode == GameMode.CREATIVE) return

        if (arena == null || !arena.isInGame()) {
            event.isCancelled = true
        }
    }

    @EventHandler
    public fun onPlayerChat(event: AsyncChatEvent) {
        val player = event.player
        val arena = player.arena()

        event.viewers().clear()
        event.viewers().add(Bukkit.getConsoleSender())

        if (arena == null || !arena.hasStarted()) {
            val isAdmin = player.hasPermission("stk.admin")

            event.viewers().addAll(arena?.arenaPlayers()?.mapNotNull { it.player() } ?: Bukkit.getOnlinePlayers()
                .filter { it.arena() == null })

            event.renderer { source, _, message, viewer ->
                if (viewer is Player) {
                    viewer.translate(
                        "global.chat.lobby", Placeholder.component(
                            "player_name", Component.text(
                                if (isAdmin) "[Admin] ${source.name}" else source.name,
                                if (isAdmin) NamedTextColor.RED else NamedTextColor.GRAY
                            )
                        ),
                        Placeholder.component(
                            "message",
                            event.message()
                                .color(if (isAdmin) NamedTextColor.WHITE else NamedTextColor.GRAY)
                        )
                    )
                } else Component.text((if (arena == null) "[LOBBY]" else "[${arena.displayName}]") + " ${source.name} -> ")
                    .append(message)
            }

            return
        }

        val team = player.playerData()?.currentTeam

        if (team == null) {
            player.sendTranslated("global.chat.cant_speak")
            event.isCancelled = true

            return
        }

        event.viewers().addAll(arena.arenaPlayers().mapNotNull { it.player() })

        event.renderer { source, _, message, viewer ->
            if (viewer is Player) {
                viewer.translate(
                    "global.chat.in_game",
                    Placeholder.unparsed("team_icon", team.chatIcon),
                    Placeholder.component("player_name", Component.text(player.name, NamedTextColor.GRAY)),
                    Placeholder.component("message", event.message().color(NamedTextColor.WHITE))
                )
            } else Component.text("[${arena.displayName}] ${source.name} -> ").append(message)
        }
    }

    @EventHandler
    public fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val world = player.world
        val worldFrom = event.from

        val main = SaveTheKweebecs.instance()

        world.players.forEach {
            it.showPlayer(main, player)
            player.showPlayer(main, it)
        }

        worldFrom.players.forEach {
            it.hidePlayer(main, player)
            player.hidePlayer(main, it)
        }
    }

    @EventHandler
    public fun onItemDrop(event: PlayerDropItemEvent) {
        if (event.player.gameMode == GameMode.CREATIVE) return

        event.isCancelled = true
    }

    @EventHandler
    public fun onItemPickup(event: EntityPickupItemEvent) {
        event.isCancelled = true
    }

    @EventHandler
    public fun onExpPickup(event: PlayerPickupExperienceEvent) {
        event.isCancelled = true
    }

    @EventHandler
    public fun onWeatherChange(event: WeatherChangeEvent) {
        event.isCancelled = true
    }
}