package me.hugo.savethekweebecs.team

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.extension.*
import me.hugo.savethekweebecs.lang.LanguageManager
import me.hugo.thankmas.gui.Icon
import me.hugo.thankmas.gui.Menu
import me.hugo.thankmas.gui.paginated.PaginatedMenu
import me.hugo.thankmas.items.*
import me.hugo.thankmas.items.itemsets.ItemSetRegistry
import me.hugo.thankmas.lang.TranslatedComponent
import me.hugo.thankmas.player.translateList
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

/**
 * Registry of every team that can be
 * played as in Save The Kweebecs.
 */
@Single
public class TeamManager {

    private val main = SaveTheKweebecs.instance()
    public val teams: Map<String, Team>

    public val transformationsMenu: PaginatedMenu

    init {
        val config = main.config

        teams = config.getConfigurationSection("teams")?.getKeys(false)
            ?.associateWith {
                val configPath = "teams.$it"

                val playerVisuals = mutableListOf<TeamVisual>()
                var defaultPlayerVisual: TeamVisual? = null

                config.getConfigurationSection("$configPath.player-visuals")?.getKeys(false)?.forEach {
                    val visualPath = "$configPath.player-visuals.$it"

                    val teamVisual = TeamVisual(
                        it,
                        SkinProperty(
                            config.getString("$visualPath.skin-texture", "")!!,
                            config.getString("$visualPath.skin-signature", "")!!
                        ),
                        config.getInt("$visualPath.headCustomId")
                    )

                    playerVisuals.add(teamVisual)

                    if (config.getBoolean("$visualPath.default", false)) {
                        defaultPlayerVisual = teamVisual
                    }
                }

                Team(
                    it,
                    playerVisuals,
                    defaultPlayerVisual!!,
                    config.getString("$configPath.chat-icon", "no-icon")!!,
                    config.getString("$configPath.team-icon", "no-icon")!!,
                    config.getInt("$configPath.transformations-menu-slot", 0),
                    config.getConfigurationSection("$configPath.items")?.getKeys(false)
                        ?.associate { slot -> slot.toInt() to config.getItemStack("$configPath.items.$slot")!! }
                        ?: mapOf(),
                    config.getConfigurationSection("$configPath.shop-items")?.getKeys(false)
                        ?.map { key ->
                            TeamShopItem(
                                key,
                                config.getItemStack("$configPath.shop-items.$key.item") ?: ItemStack(Material.BEDROCK),
                                config.getInt("$configPath.shop-items.$key.cost")
                            )
                        }?.toMutableList() ?: mutableListOf()
                )
            } ?: mapOf()

        transformationsMenu = PaginatedMenu(
            "menu.teamVisuals.title",
            9 * 4,
            Menu.MenuFormat.ONE_TRIMMED,
            TranslatableItem(
                Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                flags = listOf(
                    ItemFlag.HIDE_ARMOR_TRIM,
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_ENCHANTS
                ),
                name = "menu.teamVisuals.icon.name",
                lore = "menu.teamVisuals.icon.lore"
            ),
            null,
        )

        teams.values.forEach {
            transformationsMenu.setIcon(
                it.transformationsMenuSlot, 0,
                me.hugo.thankmas.gui.Icon(
                    TranslatableItem(
                        material = Material.CARVED_PUMPKIN,
                        customModelData = it.defaultPlayerVisual.headCustomId,
                        name = "menu.teamVisuals.icon.team.${it.id}.name",
                        lore = "menu.teamVisuals.icon.team.${it.id}.lore"
                    )
                ) { player, _ ->
                    val clicker = player.clicker

                    // it.teamVisualMenu[it]?.open(clicker)
                    clicker.playSound(Sound.BLOCK_CHEST_OPEN)
                })
        }
    }

    /**
     * A team that can be played as in Save The Kweebecs and all its data:
     *
     * - id
     * - List of visuals
     * - Slot in the transformations selector.
     * - Chat and team icons
     * - Items their kit has
     * - Items they can buy.
     */
    public inner class Team(
        public val id: String,
        public val visuals: List<TeamVisual>,
        public val defaultPlayerVisual: TeamVisual,
        public val chatIcon: String,
        public val teamIcon: String,
        public val transformationsMenuSlot: Int = 0,
        public var kitItems: Map<Int, ItemStack> = mapOf(),
        public var shopItems: MutableList<TeamShopItem> = mutableListOf()
    ) : KoinComponent {

        private val itemSetManager: ItemSetRegistry by inject()

        public val visualsMenu: PaginatedMenu = PaginatedMenu(
            "menu.teamVisuals.title",
            9 * 4,
            Menu.MenuFormat.ONE_TRIMMED,
            TranslatableItem(
                material = Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                flags = listOf(
                    ItemFlag.HIDE_ARMOR_TRIM,
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_ENCHANTS
                ),
                name = "menu.teamVisuals.icon.name",
                lore = "menu.teamVisuals.icon.lore"
            ),
            transformationsMenu.pageList.firstOrNull(),
        ).also { menu ->
            visuals.forEach {
                menu.addIcon(
                    Icon(it.displayItem) { context, _ ->

                    })
            }
        }

        /**
         * Gives [player] the items in this team's kit.
         *
         * If [giveArenaItemSet] is true it will also give
         * the clickable item set "arena". Used mainly for the shop.
         */
        public fun giveItems(
            player: Player,
            clearInventory: Boolean = false,
            giveArenaItemSet: Boolean = true
        ) {
            val inventory = player.inventory

            if (clearInventory) {
                inventory.clear()
                inventory.setArmorContents(null)
            }

            kitItems.forEach { inventory.setItem(it.key, it.value) }
            if (giveArenaItemSet) itemSetManager.giveSetNullable("arena", player)
        }
    }

    /** Skin data that can be used by players and NPCs. */
    public data class SkinProperty(val value: String, val signature: String)

    /**
     * Item that can be bought in the shop for
     * certain [Team].
     */
    public data class TeamShopItem(val key: String, val item: ItemStack, val cost: Int) : TranslatedComponent {
        /**
         * Returns a clickable icon used to buy this shop item.
         */
        public fun getIcon(player: Player): Icon {
            val isAvailable = (player.playerData()?.getCoins() ?: 0) >= cost
            val translatedLore = player.translateList(
                if (isAvailable)
                    "menu.shop.icon.availableLore"
                else "menu.shop.icon.notAvailableLore",
            ) {
                Placeholder.unparsed("cost", cost.toString())
            }

            return Icon({ clicker, _ ->
                /* val playerData = clicker.playerData() ?: return@addClickAction
                 val canBuy = (clicker.playerData()?.getCoins() ?: 0) >= cost

                 if (!canBuy) {
                     clicker.sendTranslated("menu.shop.poor")
                     clicker.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)

                     return@addClickAction
                 }

                 clicker.sendTranslated(
                     "menu.shop.item_bought",
                     Placeholder.component(
                         "item",
                         Component.text(
                             PlainTextComponentSerializer.plainText()
                                 .serialize(item.itemMeta?.displayName() ?: item.displayName()), NamedTextColor.GREEN
                         )
                     ),
                     Placeholder.unparsed("amount", item.amount.toString())
                 )
                 playerData.addCoins(cost * -1, "bought_item")

                 clicker.intelligentGive(item)
                 clicker.playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP)

                 clicker.closeInventory()*/
            }) {
                ItemStack(item)
                    .putLore(item.itemMeta?.lore()?.plus(translatedLore) ?: translatedLore)
            }
        }
    }

    /**
     * Skin and custom head that can be selected
     * as a transformation for certain [Team].
     */
    public data class TeamVisual(val key: String, val skin: SkinProperty, val headCustomId: Int) : TranslatedComponent {

        public val displayItem: TranslatableItem = TranslatableItem(
            material = Material.CARVED_PUMPKIN,
            flags = listOf(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP),
            name = "global.cosmetic.head.$key.selector_name",
            lore = "menu.teamVisuals.selectLore",
        )

        /*
        ItemStack(Material.CARVED_PUMPKIN)
                .customModelData(headCustomId)
                .nameTranslatable("global.cosmetic.head.$key.selector_name", language)
                .loreTranslatable(
                    if (selected) "menu.teamVisuals.selectedLore" else
                        "menu.teamVisuals.selectLore", language, Placeholder.component(
                        "visual_name",
                        playerUuid.translate("global.cosmetic.head.$key.selector_name")
                    )
                )
                .flag(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ITEM_SPECIFICS)
                .enchantment(if (selected) Enchantment.ARROW_DAMAGE else null, 1)
         */

        /**
         * Crafts a simple head used by this TeamVisual.
         */
        public fun craftHead(teamPlayer: Player?): ItemStack {
            return ItemStack(Material.CARVED_PUMPKIN)
                .nameTranslatable("global.cosmetic.head.$key.hat_name", teamPlayer?.locale() ?: Locale.ENGLISH)
                .customModelData(headCustomId)
        }

        /**
         * Returns a clickable item for [playerUuid] used to select
         * this TeamVisual.
         */
        public fun getIcon(playerUuid: UUID, team: Team, selected: Boolean = true): Icon {
            return Icon({ _, _ -> }) {
                ItemStack(Material.BEDROCK)
            } /*.addClickAction { player, _ ->
                val playerData = player.playerData() ?: return@addClickAction

                val oldVisual = playerData.selectedTeamVisuals[team] ?: team.defaultPlayerVisual

                if (oldVisual == this) {
                    player.sendTranslated(
                        "system.teamVisuals.alreadySelected",
                        Placeholder.component(
                            "visual_name",
                            playerUuid.translate("global.cosmetic.head.$key.selector_name")
                        )
                    )

                    player.playSound(Sound.ENTITY_ENDERMAN_TELEPORT)

                    return@addClickAction
                }

                playerData.selectedTeamVisuals[team] = this

                player.sendTranslated(
                    "system.teamVisuals.equipped",
                    Placeholder.component(
                        "visual_name",
                        playerUuid.translate("global.cosmetic.head.$key.selector_name")
                    )
                )

                val visualsMenu = playerData.teamVisualMenu[team] ?: return@addClickAction

                visualsMenu.replaceFirst(
                    oldVisual.getDisplayItem(playerUuid, true),
                    oldVisual.getIcon(playerUuid, team, false)
                )
                visualsMenu.replaceFirst(getDisplayItem(playerUuid, false), getIcon(playerUuid, team, true))

                player.playSound(Sound.BLOCK_NOTE_BLOCK_HAT)
            }*/
        }
    }
}