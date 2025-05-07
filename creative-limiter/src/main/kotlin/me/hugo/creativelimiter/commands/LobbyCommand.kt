package me.hugo.creativelimiter.commands

import me.hugo.thankmas.ThankmasPlugin
import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description

public class LobbyCommand {

    @Command("lobby", "hub", "leave", "back")
    @Description("Leave creative!")
    private fun sendToLobby(sender: Player) {
        ThankmasPlugin.instance<ThankmasPlugin<*>>().playerDataManager.getPlayerData(sender.uniqueId).saveSafely(sender) {
            sender.kick(Component.text("Sending back to hub..."))
        }
    }

}