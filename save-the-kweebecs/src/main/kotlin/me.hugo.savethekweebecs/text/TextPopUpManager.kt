package me.hugo.savethekweebecs.text

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.annotation.Single
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.seconds

@Single
class TextPopUpManager : BukkitRunnable() {

    private val popUps: ConcurrentMap<TextPopUp, Long> = ConcurrentHashMap()

    override fun run() {
        popUps.forEach { (popup, spawnTime) ->
            if (!popup.location.isWorldLoaded || System.currentTimeMillis() > spawnTime + popup.millisecondDuration) {
                removePopUp(popup)
            }
        }
    }

    fun createPopUp(
        viewers: List<Player>,
        textKey: String,
        location: Location,
        times: PopupTimes = PopupTimes(1.5.seconds, 0.5.seconds),
        transformations: PopupTransformation = PopupTransformation()
    ) {
        popUps[TextPopUp(viewers, textKey, location, times, transformations)] = System.currentTimeMillis()
    }

    fun createPopUp(
        viewer: Player,
        textKey: String,
        location: Location,
        times: PopupTimes = PopupTimes(1.5.seconds, 0.5.seconds),
        transformations: PopupTransformation = PopupTransformation()
    ) {
        popUps[TextPopUp(listOf(viewer), textKey, location, times, transformations)] = System.currentTimeMillis()
    }

    private fun removePopUp(popup: TextPopUp) {
        popUps.remove(popup)
        popup.entities.values.forEach { it.remove() }
    }

}