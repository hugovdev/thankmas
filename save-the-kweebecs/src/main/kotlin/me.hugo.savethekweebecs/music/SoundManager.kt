package me.hugo.savethekweebecs.music

import me.hugo.savethekweebecs.extension.player
import me.hugo.savethekweebecs.extension.translate
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.koin.core.annotation.Single
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Takes care of the loopable sounds and music.
 */
@Single
public class SoundManager : BukkitRunnable() {

    public companion object {
        /** The default track that plays when in a game of Save The Kweebecs. */
        public val IN_GAME_MUSIC: MusicTrack = MusicTrack("music.save_the_kweebecs", 51.seconds)
    }

    /** The amount of loop iterations since the playing notification was last sent. */
    private var timeSinceNotification: Int = 0

    /** List of the players with music tracks their current [PlaybackData]. */
    private val musicPlayers: ConcurrentMap<UUID, PlaybackData> = ConcurrentHashMap()

    /**
     * Takes care of replaying looped music and displaying
     * the notification every 15 iterations.
     */
    override fun run() {
        val notify = timeSinceNotification >= 15

        musicPlayers.forEach { (uuid, playbackData) ->
            if (playbackData.showTrackStatus && notify) uuid.player()?.let { sendPlayingNotification(it) }

            if (playbackData.startTime + playbackData.track.duration.inWholeMilliseconds <= System.currentTimeMillis()) {
                musicPlayers[uuid] =
                    PlaybackData(playbackData.track, System.currentTimeMillis(), playbackData.showTrackStatus)
                val player = uuid.player()

                if (player != null && player.isOnline) {
                    player.playSound(playbackData.track.sound)
                } else {
                    musicPlayers.remove(uuid)
                }
            }
        }

        if (notify) timeSinceNotification = 0
        else timeSinceNotification++
    }

    /**
     * Plays [track] to [player].
     *
     * If [showTrackStatus] is true the player will see
     * a "Now Playing" notification in their actionbar.
     */
    public fun playTrack(track: MusicTrack, player: Player, showTrackStatus: Boolean = true) {
        // Stop the current track before playing a new one!
        stopTrack(player)

        player.playSound(track.sound)
        musicPlayers[player.uniqueId] = PlaybackData(track, System.currentTimeMillis(), showTrackStatus)

        sendPlayingNotification(player)
    }

    /** Plays the sound effect with id [name] to [player]. */
    public fun playSoundEffect(name: String, player: Player) {
        player.playSound(Sound.sound(Key.key(name), Sound.Source.AMBIENT, 1.0f, 1.0f))
    }

    /** Sends a "Now Playing" notification to [player] with the current track name and author. */
    private fun sendPlayingNotification(player: Player) {
        val track = musicPlayers[player.uniqueId]?.track ?: return

        player.sendActionBar(
            player.translate(
                "global.sound.music.now_playing",
                Placeholder.component("track_name", player.translate("global.sound.${track.trackId}.name")),
                Placeholder.component("track_author", player.translate("global.sound.${track.trackId}.author")),
            )
        )
    }

    /**
     * Stops the current music playing for [player]. (If any)
     */
    public fun stopTrack(player: Player) {
        musicPlayers.remove(player.uniqueId)?.track?.sound?.let { player.stopSound(it) }
    }

    /**
     * Contains when a player started playing [track]
     * and if they should see the playing notification.
     */
    public data class PlaybackData(val track: MusicTrack, val startTime: Long, val showTrackStatus: Boolean = true)

    /**
     * Contains the minecraft sound id for this track
     * and its duration.
     */
    public data class MusicTrack(val trackId: String, val duration: Duration) {
        public val sound: Sound = Sound.sound(Key.key(trackId), Sound.Source.RECORD, 1.0f, 1.0f)
    }
}