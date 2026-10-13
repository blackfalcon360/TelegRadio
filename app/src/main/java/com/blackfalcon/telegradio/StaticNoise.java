package com.blackfalcon.telegradio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/** The hiss you hear between radio stations: generated on the phone, no file needed. */
public final class StaticNoise {

    private static final int RATE = 16000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private volatile float vol = 0f;
    private Thread thread;
    private Runnable fade;

    public boolean isRunning() { return running; }

    public void setVolume(float v) { vol = Math.max(0f, Math.min(1f, v)); }

    public synchronized void start(float v) {
        cancelFade();
        vol = Math.max(0f, Math.min(1f, v));
        if (running) return;
        running = true;
        thread = new Thread(() -> {
            AudioTrack track = null;
            try {
                int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(Math.max(min, 4096) * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build();
                track.play();
                Random rnd = new Random();
                short[] buf = new short[800];
                float lp = 0f, hum = 0f;
                while (running) {
                    float amp = vol * 14000f;
                    for (int i = 0; i < buf.length; i++) {
                        float n = rnd.nextFloat() * 2f - 1f;
                        lp = lp * 0.45f + n * 0.55f;                       // slightly soft hiss
                        float crackle = rnd.nextInt(3500) == 0 ? (rnd.nextFloat() * 2f - 1f) * 2.2f : 0f;
                        hum += 0.0125f;                                      // faint whistle that drifts, like a dial
                        float whistle = (float) Math.sin(hum * (1.5f + 0.5f * (float) Math.sin(hum * 0.003f))) * 0.07f;
                        float s = (lp + crackle + whistle) * amp;
                        buf[i] = (short) Math.max(-32000, Math.min(32000, s));
                    }
                    track.write(buf, 0, buf.length);
                }
            } catch (Exception ignored) {
            } finally {
                try { if (track != null) { track.stop(); track.release(); } } catch (Exception ignored) { }
                running = false;
            }
        }, "static-noise");
        thread.start();
    }

    public synchronized void stop() {
        cancelFade();
        running = false;
    }

    private void cancelFade() {
        if (fade != null) { main.removeCallbacks(fade); fade = null; }
    }

    /** Fades the hiss out over `ms` milliseconds, then stops it. */
    public void fadeOut(final long ms) {
        if (!running) return;
        cancelFade();
        final int steps = 8;
        final float start = vol;
        final long[] i = {0};
        fade = new Runnable() {
            @Override public void run() {
                i[0]++;
                if (i[0] >= steps) { stop(); return; }
                vol = start * (1f - i[0] / (float) steps);
                main.postDelayed(this, ms / steps);
            }
        };
        main.postDelayed(fade, ms / steps);
    }
}
