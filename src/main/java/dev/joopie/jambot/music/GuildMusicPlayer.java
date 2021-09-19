package dev.joopie.jambot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.joopie.jambot.exceptions.JambotMusicPlayerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.*;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@RequiredArgsConstructor
public class GuildMusicPlayer {
    public static final int VOLUME_MAX = 50;

    private final Guild guild;
    private final AudioPlayer audioPlayer;
    private final BlockingQueue<AudioTrack> audioTrackQueue = new LinkedBlockingQueue<>();

    public void joinVoiceChannelOfUser(final User user) {
        if (guild.getAudioManager().isConnected()) {
            log.warn("Already connected to a voice channel in guild `%s`.".formatted(guild.getName()));
            throw new JambotMusicPlayerException("I'm already connected!");
        }

        Member member = guild.getMemberById(user.getIdLong());
        if (Objects.isNull(member)) {
            log.warn("User `%s` is not a member of guild `%s`.".formatted(user.getName(), guild.getName()));
            throw new JambotMusicPlayerException("It appears I can't find you on the server...");
        }

        GuildVoiceState voiceState = member.getVoiceState();

        if (Objects.isNull(voiceState) || !voiceState.inVoiceChannel()) {
            log.warn("User `%s` is not in voice channel.".formatted(user.getName()));
            throw new JambotMusicPlayerException("Are you sure you're in a voice channel, yes?");
        }

        join(voiceState.getChannel());
    }

    public void join(final VoiceChannel voiceChannel) {
        if (isConnectedToVoiceChannel()) {
            throw new JambotMusicPlayerException("I'm already connected!");
        }

        guild.getAudioManager().openAudioConnection(voiceChannel);

        if (!(guild.getAudioManager().getSendingHandler() instanceof AudioPlayerAudioSendHandler)) {
            guild.getAudioManager().setSendingHandler(new AudioPlayerAudioSendHandler(audioPlayer));
        }
    }

    public void leave() {
        if (isConnectedToVoiceChannel()) {
            guild.getAudioManager().closeAudioConnection();
            audioPlayer.checkCleanup(0);
            clear();
        } else {
            throw new JambotMusicPlayerException();
        }
    }

    public void play(final AudioTrack audioTrack) {
        if (!audioPlayer.startTrack(audioTrack, true)) {
            audioTrackQueue.offer(audioTrack);
        }
    }

    public void pause() {
        if (audioPlayer.getPlayingTrack() != null) {
            audioPlayer.setPaused(!audioPlayer.isPaused());
        }
    }

    public void stop() {
        audioPlayer.stopTrack();
    }

    public void next() {
        audioPlayer.startTrack(audioTrackQueue.poll(), false);
    }

    public void clear() {
        audioTrackQueue.clear();
    }

    public void volume(int volume) {
        volume = Math.min(Math.max(volume, 0), 200);
        volume = (int) Math.floor(VOLUME_MAX / 100.0 * volume);
        audioPlayer.setVolume(volume);
    }

    public boolean isConnectedToVoiceChannel() {
        return guild.getAudioManager().isConnected();
    }

    public boolean isSameVoiceChannelAsUser(final User user) {
        return isConnectedToVoiceChannel() &&
                guild.getAudioManager().getConnectedChannel().getMembers().stream()
                        .anyMatch(x -> Objects.equals(x.getUser(), user));
    }
}