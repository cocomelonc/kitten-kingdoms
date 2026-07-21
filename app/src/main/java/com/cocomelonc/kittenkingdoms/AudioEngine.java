/*
 * Kitten Kingdoms
 * Author: cocomelonc
 * Copyright (c) 2026 cocomelonc (Zhassulan Zhussupov)
 * SPDX-License-Identifier: MIT
 */
package com.cocomelonc.kittenkingdoms;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Gentle procedural chimes; no bundled audio, codecs, native libraries, or network access. */
final class AudioEngine {
    private static final int SAMPLE_RATE = 22_050;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "kitten-kingdoms-chime");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean closed;

    void playBuildingCompleted(int completedCount) {
        float base = 466.16f * (1f + completedCount * 0.04f);
        play(new float[]{base, base * 1.25f, base * 1.5f}, 0.30f, 0.11f);
    }

    void playPopulationGrew() {
        play(new float[]{392f, 493.88f, 587.33f}, 0.34f, 0.10f);
    }

    void playTechUnlocked() {
        play(new float[]{523.25f, 659.25f, 783.99f, 987.77f}, 0.62f, 0.14f);
    }

    private void play(float[] frequencies, float durationSeconds, float volume) {
        if (closed) {
            return;
        }
        try {
            executor.execute(() -> synthesizeAndPlay(frequencies, durationSeconds, volume));
        } catch (RejectedExecutionException ignored) {
            // The activity was closed between the state check and queueing the optional chime.
        }
    }

    private void synthesizeAndPlay(float[] frequencies, float durationSeconds, float volume) {
        int sampleCount = Math.max(256, (int) (SAMPLE_RATE * durationSeconds));
        short[] pcm = new short[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            double time = i / (double) SAMPLE_RATE;
            double progress = i / (double) sampleCount;
            double attack = Math.min(1.0, progress / 0.05);
            double release = Math.pow(Math.max(0.0, 1.0 - progress), 2.5);
            double value = 0.0;
            for (int note = 0; note < frequencies.length; note++) {
                double stagger = note * 0.052;
                if (time >= stagger) {
                    double localTime = time - stagger;
                    value += Math.sin(Math.PI * 2.0 * frequencies[note] * localTime)
                            + 0.18 * Math.sin(Math.PI * 4.0 * frequencies[note] * localTime);
                }
            }
            value /= frequencies.length * 1.18;
            pcm[i] = (short) (Short.MAX_VALUE * volume * attack * release * value);
        }

        AudioTrack track = null;
        try {
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            int written = track.write(pcm, 0, pcm.length);
            if (written != pcm.length || track.getState() != AudioTrack.STATE_INITIALIZED) {
                return;
            }
            track.play();
            Thread.sleep((long) (durationSeconds * 1000f) + 40L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException ignored) {
            // Sound is optional; unsupported or unavailable audio must never interrupt play.
        } finally {
            if (track != null) {
                try {
                    if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop();
                    }
                } catch (IllegalStateException ignored) {
                    // Some vendor audio stacks invalidate a track during an activity shutdown.
                }
                track.release();
            }
        }
    }

    void close() {
        closed = true;
        executor.shutdownNow();
    }
}
