package me.hugo.savethekweebecs.util

import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.entity.Firework

/**
 * Firework that instantly spawns.
 */
class InstantFirework(effect: FireworkEffect, location: Location) {
    init {
        val f = location.getWorld().spawn(location, Firework::class.java)
        val fm = f.fireworkMeta
        fm.addEffect(effect)
        f.fireworkMeta = fm
    }
}