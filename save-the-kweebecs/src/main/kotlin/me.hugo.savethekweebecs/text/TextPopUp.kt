package me.hugo.savethekweebecs.text

import me.hugo.savethekweebecs.SaveTheKweebecs
import me.hugo.savethekweebecs.extension.translate
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class PopupTimes(val popupDuration: Duration, val popupTime: Duration)
public data class PopupTransformation(val startingScale: Float = 0.0f, val scale: Float = 1.0f)

public class TextPopUp(
    viewers: List<Player>,
    textKey: String,
    public var location: Location,
    times: PopupTimes = PopupTimes(1.5.seconds, 0.5.seconds),
    transformations: PopupTransformation = PopupTransformation()
) {

    private val popupMilliseconds = times.popupTime.inWholeMilliseconds
    public val millisecondDuration: Long = times.popupDuration.inWholeMilliseconds

    public val entities: Map<UUID, TextDisplay>

    init {
        location = location.clone().add(0.0, transformations.scale.toDouble() / 2, 0.0)

        entities = viewers.associate { player ->
            val textDisplay =
                location.world.spawnEntity(
                    location,
                    EntityType.TEXT_DISPLAY,
                    CreatureSpawnEvent.SpawnReason.CUSTOM
                )
                { entity ->
                    entity as TextDisplay

                    entity.isVisibleByDefault = false

                    entity.isDefaultBackground = false
                    entity.backgroundColor = Color.fromARGB(0, 255, 255, 255)

                    entity.billboard = Display.Billboard.CENTER
                    entity.text(player.translate(textKey))
                    entity.brightness = Display.Brightness(15, 15)

                    entity.transformation = Transformation(
                        Vector3f(),
                        AxisAngle4f(),
                        Vector3f(transformations.startingScale),
                        AxisAngle4f()
                    )
                    entity.interpolationDuration = (popupMilliseconds * 0.02).toInt()
                    entity.teleportDuration = (popupMilliseconds * 0.02).toInt()
                    entity.interpolationDelay = -1

                    player.showEntity(SaveTheKweebecs.instance(), entity)
                } as TextDisplay

            player.uniqueId to textDisplay
        }

        // Wait 2 ticks to ensure the entity tracker has registered this entity and transformations
        // will happen in the
        object : BukkitRunnable() {
            override fun run() {
                entities.values.forEach {
                    it.transformation =
                        Transformation(
                            Vector3f(0.0f, (transformations.scale / 2) * -1, 0.0f),
                            AxisAngle4f(),
                            Vector3f(transformations.scale),
                            AxisAngle4f()
                        )
                }
            }
        }.runTaskLater(SaveTheKweebecs.instance(), 2L)
    }

}