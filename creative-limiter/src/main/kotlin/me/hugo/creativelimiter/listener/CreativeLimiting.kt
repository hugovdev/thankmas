package me.hugo.creativelimiter.listener

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import me.hugo.creativelimiter.CreativeLimiter
import me.hugo.thankmas.player.updateBoardTags
import org.bukkit.Bukkit
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntitySpawnEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.koin.core.annotation.Single

@Single
public class CreativeLimiting(private val main: CreativeLimiter) : Listener {

    public companion object {
        public const val ENTITY_LIMIT: Int = 400
    }

    public var currentEntities: Int = 0
        private set

    private val allowedEntityBypass: List<EntityType> = listOf(EntityType.PLAYER)

    @EventHandler
    public fun onEntitySpawn(event: EntitySpawnEvent) {
        val entity = event.entity

        if (entity is Player) return

        if (entity is Wither || entity is EnderDragon || entity is Warden) {
            event.isCancelled = true
            return
        }

        val entityList =
            event.location.world.entities.filter { currentEntity -> !allowedEntityBypass.contains(currentEntity.type) }

        if (entityList.size >= ENTITY_LIMIT) {
            Bukkit.getLogger()
                .warning("Entity spawn limit has been reached. Aborted spawn of entity " + entity.type.name + ".")
            event.isCancelled = true

            return
        }

        if (allowedEntityBypass.contains(entity.type)) return

        currentEntities = entityList.size + 1
        updateBoardTags("entities")
    }

    @EventHandler
    public fun onEntitySpawn(event: EntityRemoveFromWorldEvent) {
        val entity = event.entity

        if (allowedEntityBypass.contains(entity.type)) return

        val entityList = entity.world.entities.filter { currentEntity -> !allowedEntityBypass.contains(currentEntity.type) }

        currentEntities = entityList.size
        updateBoardTags("entities")
    }

    @EventHandler
    public fun onBlockSpread(e: BlockSpreadEvent) {
        e.isCancelled = true
    }

    @EventHandler
    public fun onBlockSpread(e: BlockBurnEvent) {
        e.isCancelled = true
    }

    @EventHandler
    public fun onBlockSpread(e: BlockIgniteEvent) {
        e.isCancelled = true
    }

    @EventHandler
    public fun onWeatherChange(event: WeatherChangeEvent) {
        if (event.toWeatherState()) event.isCancelled = true
    }

    @EventHandler
    public fun onEntityExplosion(event: EntityExplodeEvent) {
        event.isCancelled = true
    }

    @EventHandler
    public fun onBlockExplode(event: BlockExplodeEvent) {
        event.isCancelled = true
    }
}