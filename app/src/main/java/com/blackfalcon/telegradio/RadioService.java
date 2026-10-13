package com.blackfalcon.telegradio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Metadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.metadata.icy.IcyInfo;

import org.json.JSONObject;

/** Plays the radio in the background, with a notification you can pause or stop. */
public class RadioService extends Service {

    public static final String ACT_PLAY = "play", ACT_TOGGLE = "toggle", ACT_STOP = "stop", ACT_SLEEP = "sleep";
    public static final int IDLE = 0, LOADING = 1, PLAYING = 2, PAUSED = 3, ERROR = 4;
    private static final String CHANNEL = "radio";
    private static final int NOTE_ID = 7;

    public interface Listener { void onState(int state, Station s, String nowPlaying); }

    public static RadioService inst;
    public static Listener listener;
    public static long sleepAtMs = 0;

    private ExoPlayer player;
    private Station cur;
    private int state = IDLE;
    private String now = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable sleepStop = new Runnable() { @Override public void run() { sleepAtMs = 0; stopAll(); } };

    public static int currentState() { return inst == null ? IDLE : inst.state; }
    public static Station currentStation() { return inst == null ? null : inst.cur; }
    public static String currentNow() { return inst == null ? "" : inst.now; }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Radio playback", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setUserAgent("Telegradio-App/1.0 (BlackFalcon)");
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int st) {
                if (st == Player.STATE_BUFFERING) set(LOADING);
                else if (st == Player.STATE_READY) set(player.getPlayWhenReady() ? PLAYING : PAUSED);
                else if (st == Player.STATE_ENDED) set(IDLE);
            }
            @Override public void onIsPlayingChanged(boolean playing) {
                if (playing) set(PLAYING);
                else if (state == PLAYING) set(PAUSED);
            }
            @Override public void onPlayerError(PlaybackException e) { now = ""; set(ERROR); }
            @Override public void onMetadata(Metadata md) {
                for (int i = 0; i < md.length(); i++) {
                    Metadata.Entry en = md.get(i);
                    if (en instanceof IcyInfo) {
                        String t = ((IcyInfo) en).title;
                        if (t != null && !t.trim().isEmpty()) { now = t.trim(); set(state); }
                    }
                }
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? null : intent.getAction();
        if (ACT_PLAY.equals(act)) {
            try {
                cur = Station.fromJson(new JSONObject(intent.getStringExtra("s")));
            } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
            now = "";
            toForeground(LOADING);
            player.setMediaItem(MediaItem.fromUri(cur.url));
            player.prepare();
            player.play();
            set(LOADING);
        } else if (ACT_TOGGLE.equals(act)) {
            toggle();
        } else if (ACT_STOP.equals(act)) {
            stopAll();
        } else if (ACT_SLEEP.equals(act)) {
            int min = intent.getIntExtra("min", 0);
            handler.removeCallbacks(sleepStop);
            if (min > 0) { sleepAtMs = System.currentTimeMillis() + min * 60000L; handler.postDelayed(sleepStop, min * 60000L); }
            else sleepAtMs = 0;
        } else if (cur == null) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    public void toggle() {
        if (cur == null) return;
        if (player.getPlayWhenReady() && state != ERROR) { player.pause(); set(PAUSED); }
        else {
            if (state == ERROR || state == IDLE) { player.setMediaItem(MediaItem.fromUri(cur.url)); player.prepare(); }
            player.play();
            set(LOADING);
        }
    }

    /** Silences the station while the dial is being turned (the notification stays). */
    public void pauseQuiet() {
        if (player != null && player.getPlayWhenReady()) { player.pause(); set(PAUSED); }
    }

    private void stopAll() {
        handler.removeCallbacks(sleepStop);
        sleepAtMs = 0;
        player.stop();
        player.clearMediaItems();
        state = IDLE;
        now = "";
        if (listener != null) listener.onState(IDLE, cur, "");
        stopForeground(true);
        stopSelf();
    }

    private void set(int st) {
        state = st;
        updateNotification();
        if (listener != null) listener.onState(state, cur, now);
    }

    // ------------------------------------------------------------ notification
    private PendingIntent action(String act, int code) {
        Intent i = new Intent(this, RadioService.class).setAction(act);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return PendingIntent.getService(this, code, i, f);
    }

    @SuppressWarnings("deprecation")
    private Notification build(int st) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent content = PendingIntent.getActivity(this, 1, open, f);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        String title = cur == null ? "Telegradio" : cur.name;
        String text = st == LOADING ? "Tuning\u2026" : st == ERROR ? "Could not play this station"
                : (!now.isEmpty() ? now : (st == PAUSED ? "Paused" : "Live radio"));
        boolean playing = st == PLAYING || st == LOADING;
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(content)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        playing ? "Pause" : "Play", action(ACT_TOGGLE, 2))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", action(ACT_STOP, 3));
        return b.build();
    }

    private void toForeground(int st) {
        Notification n = build(st);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(NOTE_ID, n);
    }

    private void updateNotification() {
        if (cur == null) return;
        try { ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTE_ID, build(state)); }
        catch (Exception ignored) { }
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(sleepStop);
        if (player != null) { player.release(); player = null; }
        inst = null;
        super.onDestroy();
    }
}
