package com.blackfalcon.telegradio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    // Dynamic Theme Variables
    private SharedPreferences sp;
    private boolean isDarkMode = true;
    private int BG, CARD, EDGE, ACCENT = 0xFF29B6F6, ORANGE = 0xFFFF9800, GREEN = 0xFF2ECC71, GRAY, RED = 0xFFFF5252;
    private TextView themeToggleBtn;
    private LinearLayout rootLayout;

    private Favorites favs;
    private final StaticNoise noise = new StaticNoise();
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler handler = new Handler(Looper.getMainLooper());

    // playback state (mirrors the service)
    private Station cur;
    private int pstate = RadioService.IDLE;
    private String nowTxt = "";
    private boolean waitingForStation = false, searchNoise = false, askedNotif = false;

    // views
    private FrameLayout content;
    private final View[] pages = new View[5];
    private final TextView[] tabBtns = new TextView[5];
    private int tab = 0;
    private LinearLayout mini;
    private Logo miniLogo;
    private TextView miniName, miniSub, miniPlay;
    private FrameLayout overlay;
    private Logo nLogo;
    private TextView nName, nSub, nNow, nStatus, nPlay, nFav, nSleep;
    private EqView eq;

    // tuner
    private DialView dial;
    private Logo tLogo;
    private TextView tName, tSub, tStatus, tSource, tPlay, tFav;
    private List<Station> tunerPool = new ArrayList<>();

    // stations / search
    private EditText searchEt;
    private LinearLayout chipRow;
    private TextView sStatus;
    private ListView sList;
    private StationAdapter sAdapter;
    private final List<Station> sItems = new ArrayList<>();
    private String qName = "", qCc = "", qTag = "", qLang = "", activeLabel = "";
    private int chipIdx = 0, sOffset = 0, sGen = 0;
    private boolean sLoading = false, sEnd = false;
    private Runnable searchRun;

    // countries / genres / favourites
    private ListView cList;
    private final List<Radio.Country> countries = new ArrayList<>(), countriesShown = new ArrayList<>();
    private BaseAdapter cAdapter;
    private ListView fList;
    private StationAdapter fAdapter;
    private TextView fEmpty;

    private static final String[][] CHIPS = {
            {"🔥 Popular", "", "", ""}, {"🇵🇰 Pakistan", "PK", "", ""}, {"اردو", "", "", "urdu"},
            {"📰 News", "", "news", ""}, {"📖 Quran", "", "quran", ""}, {"🕌 Islamic", "", "islamic", ""},
            {"🎵 Pop", "", "pop", ""}, {"हिन्दी", "", "", "hindi"}, {"العربية", "", "", "arabic"},
            {"⚽ Sports", "", "sports", ""}, {"🎷 Jazz", "", "jazz", ""}};

    private static final String[][] GENRES = {
            {"📰", "News & Talk", "news"}, {"⚽", "Sports", "sports"}, {"📖", "Quran", "quran"},
            {"🕌", "Islamic", "islamic"}, {"🙏", "Religious", "religion"}, {"🎵", "Pop", "pop"},
            {"🎸", "Rock", "rock"}, {"🎸", "Classic Rock", "classic rock"}, {"🎤", "Hip-Hop & Rap", "hip hop"},
            {"🎷", "Jazz", "jazz"}, {"🎻", "Classical", "classical"}, {"🎵", "Blues", "blues"},
            {"💃", "Latin", "latin"}, {"🌵", "Country", "country"}, {"🎶", "Folk", "folk"},
            {"🤘", "Metal", "metal"}, {"🎧", "Electronic", "electronic"}, {"🏠", "House & Techno", "house"},
            {"🕺", "Dance & EDM", "dance"}, {"🌙", "Chill & Ambient", "chillout"}, {"🎤", "Reggae", "reggae"},
            {"📻", "Oldies & Retro", "oldies"}, {"📈", "Soul & Funk", "soul"}, {"📊", "Top 40 & Hits", "top 40"},
            {"🏘️", "Community & Public", "community"}, {"🎭", "Variety", "variety"}, {"👶", "Kids & Family", "kids"},
            {"😆", "Comedy", "comedy"}, {"🇰🇷", "K-Pop & Asian Pop", "kpop"}, {"🎶", "Indie & Alternative", "alternative"},
            {"🎵", "Adult Contemporary", "adult contemporary"}, {"🌍", "World Music", "world music"}, {"🎙️", "Talk", "talk"}};

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ------------------------------------------------------------ small kit
    private TextView tv(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    private GradientDrawable shape(int fill, int stroke, int rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        g.setCornerRadius(dp(rDp));
        return g;
    }

    private LinearLayout.LayoutParams lp(int w, int h, float wt, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h, wt);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT, WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private class Logo extends FrameLayout {
        final ImageView iv;
        final TextView ph;
        Logo(int sizeDp) {
            super(MainActivity.this);
            setBackground(shape(0xFF1B2A44, 0xFF2A3B5E, sizeDp / 2));
            ph = new TextView(MainActivity.this);
            ph.setText("📻");
            ph.setTextSize(sizeDp * 0.38f);
            ph.setGravity(Gravity.CENTER);
            addView(ph, new FrameLayout.LayoutParams(MATCH, MATCH));
            iv = new ImageView(MainActivity.this);
            ImageLoader.round(iv);
            addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        void set(Station s) {
            ph.setText(s == null ? "📻" : s.flag().equals("🌐") ? "📻" : s.flag());
            ImageLoader.load(iv, s == null ? "" : s.favicon);
        }
    }

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("telegradio", MODE_PRIVATE);
        isDarkMode = sp.getBoolean("isDarkMode", true);
        setupThemeColors();

        favs = new Favorites(this);
        buildUi();
        showTab(0);
        loadTunerPool("🌐 World (most popular)", "", false);
        runSearch(true, false);
        loadCountries();
        refreshFavs();
    }

    private void setupThemeColors() {
        if (isDarkMode) {
            BG = 0xFF070B14;
            CARD = 0xFF0E1626;
            EDGE = 0xFF1E2A44;
            GRAY = 0xFF8A9BB8;
        } else {
            BG = 0xFFF0F2F5;
            CARD = 0xFFFFFFFF;
            EDGE = 0xFFDDDDDD;
            GRAY = 0xFF666666;
        }
    }

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        sp.edit().putBoolean("isDarkMode", isDarkMode).apply();
        setupThemeColors();
        
        if (rootLayout != null) rootLayout.setBackgroundColor(BG);
        if (themeToggleBtn != null) themeToggleBtn.setText(isDarkMode ? "🌙" : "☀️");
        
        // Rebuild UI to apply colors properly
        content.removeAllViews();
        pages[0] = buildTuner();
        pages[1] = buildStations();
        pages[2] = buildCountries();
        pages[3] = buildGenres();
        pages[4] = buildFavorites();
        for (View p : pages) content.addView(p, new FrameLayout.LayoutParams(MATCH, MATCH));
        overlay = buildNowPlaying();
        overlay.setVisibility(View.GONE);
        content.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));
        showTab(tab);
    }

    @Override
    protected void onResume() {
        super.onResume();
        RadioService.listener = (st, s, now) -> runOnUiThread(() -> onPlayerState(st, s, now));
        cur = RadioService.currentStation() != null ? RadioService.currentStation() : cur;
        pstate = RadioService.currentState();
        nowTxt = RadioService.currentNow();
        updatePlayerUi();
        handler.post(sleepTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(sleepTick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        noise.stop();
        pool.shutdownNow();
        RadioService.listener = null;
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) { overlay.setVisibility(View.GONE); return; }
        if (tab != 0) { showTab(0); return; }
        
        new AlertDialog.Builder(this)
                .setTitle("ایپ سے باہر نکلیں")
                .setMessage("کیا آپ واقعی ایپ بند کرنا چاہتے ہیں؟")
                .setPositiveButton("جی ہاں", (dialog, which) -> {
                    super.onBackPressed();
                    finish();
                })
                .setNegativeButton("نہیں", null)
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) { super.onNewIntent(intent); }

    // ----------------------------------------------------------------- build
    private void buildUi() {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(BG);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(8));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0D2745, BG}));
        
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tv("📻 Telegradio", 22, Color.WHITE, true));
        titles.addView(tv("50,000+ radio stations from every country", 12, GRAY, false));
        
        themeToggleBtn = tv(isDarkMode ? "🌙" : "☀️", 20, Color.WHITE, false);
        themeToggleBtn.setGravity(Gravity.CENTER);
        themeToggleBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        themeToggleBtn.setOnClickListener(v -> toggleTheme());

        TextView web = tv("🌐", 22, Color.WHITE, false);
        web.setGravity(Gravity.CENTER);
        web.setPadding(dp(10), dp(8), dp(10), dp(8));
        web.setOnClickListener(v -> openUrl("https://telegradio.com/"));

        head.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(themeToggleBtn);
        head.addView(web);

        // pages
        content = new FrameLayout(this);
        pages[0] = buildTuner();
        pages[1] = buildStations();
        pages[2] = buildCountries();
        pages[3] = buildGenres();
        pages[4] = buildFavorites();
        for (View p : pages) content.addView(p, new FrameLayout.LayoutParams(MATCH, MATCH));
        overlay = buildNowPlaying();
        overlay.setVisibility(View.GONE);
        content.addView(overlay, new FrameLayout.LayoutParams(MATCH, MATCH));

        // mini player
        mini = new LinearLayout(this);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(12), dp(8), dp(8), dp(8));
        mini.setBackground(shape(0xFF10203A, ACCENT, 0));
        mini.setVisibility(View.GONE);
        miniLogo = new Logo(42);
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.setPadding(dp(10), 0, dp(8), 0);
        miniName = tv("", 15, Color.WHITE, true);
        miniName.setSingleLine(true);
        miniName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        miniSub = tv("", 12, GRAY, false);
        miniSub.setSingleLine(true);
        miniSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mt.addView(miniName);
        mt.addView(miniSub);
        miniPlay = tv("⏸", 26, ACCENT, true);
        miniPlay.setGravity(Gravity.CENTER);
        miniPlay.setPadding(dp(12), dp(2), dp(12), dp(2));
        miniPlay.setOnClickListener(v -> togglePlay());
        TextView miniStop = tv("✕", 20, GRAY, true);
        miniStop.setGravity(Gravity.CENTER);
        miniStop.setPadding(dp(10), dp(2), dp(10), dp(2));
        miniStop.setOnClickListener(v -> stopRadio());
        mini.addView(miniLogo, new LinearLayout.LayoutParams(dp(42), dp(42)));
        mini.addView(mt, new LinearLayout.LayoutParams(0, WRAP, 1f));
        mini.addView(miniPlay);
        mini.addView(miniStop);
        mini.setOnClickListener(v -> openNowPlaying());

        // bottom tabs
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(0xFF0A1322);
        nav.setPadding(dp(4), dp(6), dp(4), dp(2));
        String[] names = {"📡\nTuner", "🔍\nStations", "🌐\nCountries", "🎼\nGenres", "♥\nSaved"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            tabBtns[i] = tv(names[i], 11, GRAY, true);
            tabBtns[i].setGravity(Gravity.CENTER);
            tabBtns[i].setPadding(0, dp(5), 0, dp(5));
            tabBtns[i].setOnClickListener(v -> showTab(idx));
            nav.addView(tabBtns[i], new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        TextView credit = tv("By: Black Falcon 🦅", 11, Color.WHITE, true);
        credit.setGravity(Gravity.RIGHT);
        credit.setBackgroundColor(0xFF0A1322);
        credit.setPadding(dp(14), dp(2), dp(14), dp(5));

        rootLayout.addView(head);
        rootLayout.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        rootLayout.addView(mini);
        rootLayout.addView(nav);
        rootLayout.addView(credit);
        setContentView(rootLayout);
    }

    private void showTab(int t) {
        tab = t;
        for (int i = 0; i < 5; i++) {
            pages[i].setVisibility(i == t ? View.VISIBLE : View.GONE);
            tabBtns[i].setTextColor(i == t ? ACCENT : GRAY);
        }
        overlay.setVisibility(View.GONE);
        if (t == 4) refreshFavs();
    }

    // =================================================================== TUNER
    private View buildTuner() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(10));

        tSource = tv("", 14, ACCENT, true);
        tSource.setGravity(Gravity.CENTER);
        tSource.setPadding(dp(16), dp(9), dp(16), dp(9));
        tSource.setBackground(shape(0xFF0F1D33, ACCENT, 20));
        tSource.setOnClickListener(v -> chooseSource());
        box.addView(tSource, lp(WRAP, WRAP, 0, 0, 0, 0, 8));

        tLogo = new Logo(120);
        box.addView(tLogo, lp(dp(120), dp(120), 0, 0, 6, 0, 8));

        tName = tv("Turn the dial", 22, isDarkMode ? Color.WHITE : Color.BLACK, true);
        tName.setGravity(Gravity.CENTER);
        tName.setMaxLines(2);
        tSub = tv("", 13, GRAY, false);
        tSub.setGravity(Gravity.CENTER);
        tStatus = tv("", 13, ORANGE, true);
        tStatus.setGravity(Gravity.CENTER);
        tStatus.setPadding(0, dp(6), 0, dp(10));
        box.addView(tName, lp(MATCH, WRAP, 0, 0, 0, 0, 0));
        box.addView(tSub, lp(MATCH, WRAP, 0, 0, 2, 0, 0));
        box.addView(tStatus, lp(MATCH, WRAP, 0, 0, 0, 0, 0));

        dial = new DialView(this);
        dial.setListener(new DialView.Listener() {
            @Override public void onMove(float pos, float speed) { onDialMove(pos, speed); }
            @Override public void onSettle(int index) { onDialSettle(index); }
        });
        box.addView(dial, lp(MATCH, dp(150), 0, 0, 0, 0, 0));

        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER);
        TextView prev = tv("⏮", 28, isDarkMode ? Color.WHITE : Color.BLACK, true);
        TextView next = tv("⏭", 28, isDarkMode ? Color.WHITE : Color.BLACK, true);
        tPlay = tv("▶", 30, Color.BLACK, true);
        tFav = tv("♡", 28, ACCENT, true);
        for (TextView t : new TextView[]{prev, next, tFav}) { t.setGravity(Gravity.CENTER); t.setPadding(dp(20), dp(8), dp(20), dp(8)); }
        tPlay.setGravity(Gravity.CENTER);
        tPlay.setBackground(shape(ACCENT, ACCENT, 34));
        prev.setOnClickListener(v -> stepDial(-1));
        next.setOnClickListener(v -> stepDial(1));
        tPlay.setOnClickListener(v -> tunerPlay());
        tFav.setOnClickListener(v -> {
            int i = Math.round(dial.getPos());
            if (i >= 0 && i < tunerPool.size()) { favs.toggle(tunerPool.get(i)); updateTunerFav(); refreshFavs(); }
        });
        ctl.addView(prev);
        ctl.addView(tPlay, new LinearLayout.LayoutParams(dp(68), dp(68)));
        ctl.addView(next);
        ctl.addView(tFav);
        box.addView(ctl, lp(MATCH, WRAP, 0, 0, 10, 0, 6));

        TextView hint = tv("Drag the dial left or right. Like a real radio you hear static between the stations, and when you stop on one it tunes in. Tap the logo to open the big player.", 12, GRAY, false);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint, lp(MATCH, WRAP, 0, 6, 4, 6, 0));
        tLogo.setOnClickListener(v -> { if (cur != null) openNowPlaying(); });

        ScrollView sv =
