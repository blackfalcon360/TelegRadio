package com.blackfalcon.telegradio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

    private static final int BG = 0xFF070B14, CARD = 0xFF0E1626, EDGE = 0xFF1E2A44, ACCENT = 0xFF29B6F6, ORANGE = 0xFFFF9800,
            GREEN = 0xFF2ECC71, GRAY = 0xFF8A9BB8, RED = 0xFFFF5252;

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
            {"\uD83D\uDD25 Popular", "", "", ""}, {"\uD83C\uDDF5\uD83C\uDDF0 Pakistan", "PK", "", ""}, {"اردو", "", "", "urdu"},
            {"\uD83D\uDCF0 News", "", "news", ""}, {"\uD83D\uDCD6 Quran", "", "quran", ""}, {"\uD83D\uDD4C Islamic", "", "islamic", ""},
            {"\uD83C\uDFB5 Pop", "", "pop", ""}, {"हिन्दी", "", "", "hindi"}, {"العربية", "", "", "arabic"},
            {"\u26BD Sports", "", "sports", ""}, {"\uD83C\uDFB7 Jazz", "", "jazz", ""}};

    private static final String[][] GENRES = {
            {"\uD83D\uDCF0", "News & Talk", "news"}, {"\u26BD", "Sports", "sports"}, {"\uD83D\uDCD6", "Quran", "quran"},
            {"\uD83D\uDD4C", "Islamic", "islamic"}, {"\uD83D\uDE4F", "Religious", "religion"}, {"\uD83C\uDFB5", "Pop", "pop"},
            {"\uD83C\uDFB8", "Rock", "rock"}, {"\uD83C\uDFB8", "Classic Rock", "classic rock"}, {"\uD83C\uDFA4", "Hip-Hop & Rap", "hip hop"},
            {"\uD83C\uDFB7", "Jazz", "jazz"}, {"\uD83C\uDFBB", "Classical", "classical"}, {"\uD83C\uDFB5", "Blues", "blues"},
            {"\uD83D\uDC83", "Latin", "latin"}, {"\uD83C\uDF35", "Country", "country"}, {"\uD83C\uDFB6", "Folk", "folk"},
            {"\uD83E\uDD18", "Metal", "metal"}, {"\uD83C\uDFA7", "Electronic", "electronic"}, {"\uD83C\uDFE0", "House & Techno", "house"},
            {"\uD83D\uDD7A", "Dance & EDM", "dance"}, {"\uD83C\uDF19", "Chill & Ambient", "chillout"}, {"\uD83C\uDFA4", "Reggae", "reggae"},
            {"\uD83D\uDCFB", "Oldies & Retro", "oldies"}, {"\uD83C\uDFBC", "Soul & Funk", "soul"}, {"\uD83D\uDCC8", "Top 40 & Hits", "top 40"},
            {"\uD83C\uDFD8", "Community & Public", "community"}, {"\uD83C\uDFAD", "Variety", "variety"}, {"\uD83D\uDC76", "Kids & Family", "kids"},
            {"\uD83D\uDE02", "Comedy", "comedy"}, {"\uD83C\uDDF0\uD83C\uDDF7", "K-Pop & Asian Pop", "kpop"}, {"\uD83C\uDFB6", "Indie & Alternative", "alternative"},
            {"\uD83C\uDFB5", "Adult Contemporary", "adult contemporary"}, {"\uD83C\uDF0D", "World Music", "world music"}, {"\uD83C\uDF99", "Talk", "talk"}};

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

    /** A round station logo with a radio emoji behind it. */
    private class Logo extends FrameLayout {
        final ImageView iv;
        final TextView ph;
        Logo(int sizeDp) {
            super(MainActivity.this);
            setBackground(shape(0xFF1B2A44, 0xFF2A3B5E, sizeDp / 2));
            ph = new TextView(MainActivity.this);
            ph.setText("\uD83D\uDCFB");
            ph.setTextSize(sizeDp * 0.38f);
            ph.setGravity(Gravity.CENTER);
            addView(ph, new FrameLayout.LayoutParams(MATCH, MATCH));
            iv = new ImageView(MainActivity.this);
            ImageLoader.round(iv);
            addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        void set(Station s) {
            ph.setText(s == null ? "\uD83D\uDCFB" : s.flag().equals("\uD83C\uDF10") ? "\uD83D\uDCFB" : s.flag());
            ImageLoader.load(iv, s == null ? "" : s.favicon);
        }
    }

    // ------------------------------------------------------------- lifecycle
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        favs = new Favorites(this);
        buildUi();
        showTab(0);
        loadTunerPool("\uD83C\uDF0D World (most popular)", "", false);
        runSearch(true, false);
        loadCountries();
        refreshFavs();
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
        moveTaskToBack(true); // keep the radio playing
    }

    @Override
    protected void onNewIntent(Intent intent) { super.onNewIntent(intent); }

    // ----------------------------------------------------------------- build
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(16), dp(10), dp(8), dp(8));
        head.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0D2745, BG}));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(tv("\uD83D\uDCFB Telegradio", 22, Color.WHITE, true));
        titles.addView(tv("50,000+ radio stations from every country", 12, GRAY, false));
        TextView web = tv("\uD83C\uDF10", 22, Color.WHITE, false);
        web.setGravity(Gravity.CENTER);
        web.setPadding(dp(12), dp(8), dp(12), dp(8));
        web.setOnClickListener(v -> openUrl("https://telegradio.com/"));
        head.addView(titles, new LinearLayout.LayoutParams(0, WRAP, 1f));
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
        miniPlay = tv("\u23F8", 26, ACCENT, true);
        miniPlay.setGravity(Gravity.CENTER);
        miniPlay.setPadding(dp(12), dp(2), dp(12), dp(2));
        miniPlay.setOnClickListener(v -> togglePlay());
        TextView miniStop = tv("\u2715", 20, GRAY, true);
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
        String[] names = {"\uD83D\uDCE1\nTuner", "\uD83D\uDD0E\nStations", "\uD83C\uDF0D\nCountries", "\uD83C\uDFBC\nGenres", "\u2665\nSaved"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            tabBtns[i] = tv(names[i], 11, GRAY, true);
            tabBtns[i].setGravity(Gravity.CENTER);
            tabBtns[i].setPadding(0, dp(5), 0, dp(5));
            tabBtns[i].setOnClickListener(v -> showTab(idx));
            nav.addView(tabBtns[i], new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 11, Color.WHITE, true);
        credit.setGravity(Gravity.RIGHT);
        credit.setBackgroundColor(0xFF0A1322);
        credit.setPadding(dp(14), dp(2), dp(14), dp(5));

        root.addView(head);
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        root.addView(mini);
        root.addView(nav);
        root.addView(credit);
        setContentView(root);
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

        tName = tv("Turn the dial", 22, Color.WHITE, true);
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
        TextView prev = tv("\u23EE", 28, Color.WHITE, true);
        TextView next = tv("\u23ED", 28, Color.WHITE, true);
        tPlay = tv("\u25B6", 30, Color.BLACK, true);
        tFav = tv("\u2661", 28, ACCENT, true);
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

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        return sv;
    }

    private void loadTunerPool(final String label, final String cc, final boolean favOnly) {
        tSource.setText(label + "  \u25BE");
        tStatus.setText("Loading stations\u2026");
        if (favOnly) { setTunerPool(new ArrayList<>(favs.all())); return; }
        pool.execute(() -> {
            List<Station> r = null;
            try { r = Radio.search("", cc, null, null, 0, 300); } catch (Exception ignored) { }
            final List<Station> res = r;
            runOnUiThread(() -> {
                if (res == null || res.isEmpty()) { tStatus.setText("No connection \u2014 tap the source button to try again"); return; }
                setTunerPool(res);
            });
        });
    }

    private void setTunerPool(List<Station> l) {
        tunerPool = l;
        int start = 0;
        if (cur != null) for (int i = 0; i < l.size(); i++) if (l.get(i).uuid.equals(cur.uuid)) start = i;
        dial.setPool(l, start);
        if (l.isEmpty()) { tName.setText("No stations here yet"); tSub.setText(""); tStatus.setText("Heart a station to see it here"); return; }
        previewTuner(start);
        tStatus.setText("Drag the dial to tune");
    }

    private void previewTuner(int i) {
        if (i < 0 || i >= tunerPool.size()) return;
        Station s = tunerPool.get(i);
        tName.setText(s.name);
        tSub.setText(s.subtitle());
        tLogo.set(s);
        updateTunerFav();
    }

    private void updateTunerFav() {
        int i = Math.round(dial.getPos());
        boolean on = i >= 0 && i < tunerPool.size() && favs.has(tunerPool.get(i).uuid);
        tFav.setText(on ? "\u2665" : "\u2661");
        tFav.setTextColor(on ? RED : ACCENT);
    }

    private int lastPreview = -1;

    private void onDialMove(float pos, float speed) {
        int near = Math.round(pos);
        if (near != lastPreview) { lastPreview = near; previewTuner(near); }
        // like a real radio: static while the dial is between stations
        float frac = Math.abs(pos - near);
        float v = Math.min(0.9f, 0.10f + frac * 1.6f + Math.min(0.45f, speed * 0.15f));
        if (RadioService.inst != null) RadioService.inst.pauseQuiet();
        waitingForStation = false;
        if (!noise.isRunning()) noise.start(v); else noise.setVolume(v);
        tStatus.setText("Tuning\u2026");
    }

    private void onDialSettle(int index) {
        if (index < 0 || index >= tunerPool.size()) { noise.fadeOut(200); return; }
        playStation(tunerPool.get(index));
    }

    private void stepDial(int d) {
        if (tunerPool.isEmpty()) return;
        dial.settleTo(Math.max(0, Math.min(tunerPool.size() - 1, Math.round(dial.getPos()) + d)));
    }

    private void tunerPlay() {
        if (cur != null && Math.round(dial.getPos()) < tunerPool.size() && tunerPool.get(Math.round(dial.getPos())).uuid.equals(cur.uuid)
                && pstate != RadioService.IDLE && pstate != RadioService.ERROR) { togglePlay(); return; }
        int i = Math.round(dial.getPos());
        if (i >= 0 && i < tunerPool.size()) playStation(tunerPool.get(i));
    }

    private void chooseSource() {
        final String[] names = {"\uD83C\uDF0D World (most popular)", "\u2665 My saved stations", "\uD83C\uDDF5\uD83C\uDDF0 Pakistan", "\uD83C\uDDEE\uD83C\uDDF3 India",
                "\uD83C\uDDF8\uD83C\uDDE6 Saudi Arabia", "\uD83C\uDDE6\uD83C\uDDEA United Arab Emirates", "\uD83C\uDDEC\uD83C\uDDE7 United Kingdom",
                "\uD83C\uDDFA\uD83C\uDDF8 United States", "\uD83C\uDDF9\uD83C\uDDF7 Turkey", "\uD83C\uDDE6\uD83C\uDDEB Afghanistan",
                "\uD83C\uDDE7\uD83C\uDDE9 Bangladesh", "\uD83C\uDF10 Another country\u2026"};
        final String[] cc = {"", "", "PK", "IN", "SA", "AE", "GB", "US", "TR", "AF", "BD", ""};
        new AlertDialog.Builder(this).setTitle("Tune through\u2026").setItems(names, (d, w) -> {
            if (w == 1) loadTunerPool(names[w], "", true);
            else if (w == 11) showTab(2);
            else loadTunerPool(names[w], cc[w], false);
        }).show();
    }

    // ================================================================ STATIONS
    private View buildStations() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        searchEt = new EditText(this);
        searchEt.setHint("\uD83D\uDD0D  Search stations by name\u2026");
        searchEt.setHintTextColor(0xFF5A6B88);
        searchEt.setTextColor(Color.WHITE);
        searchEt.setTextSize(16);
        searchEt.setSingleLine(true);
        searchEt.setPadding(dp(18), dp(11), dp(18), dp(11));
        searchEt.setBackground(shape(CARD, EDGE, 26));
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (searchRun != null) handler.removeCallbacks(searchRun);
                final String text = s.toString().trim();
                searchRun = () -> { qName = text; runSearch(true, true); };
                handler.postDelayed(searchRun, 550);
            }
        });
        box.addView(searchEt, lp(MATCH, WRAP, 0, 12, 8, 12, 4));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setPadding(dp(8), dp(2), dp(8), dp(2));
        hs.addView(chipRow);
        box.addView(hs);
        rebuildChips();

        sStatus = tv("", 12, GRAY, false);
        sStatus.setPadding(dp(18), dp(4), dp(18), dp(2));
        box.addView(sStatus);

        sAdapter = new StationAdapter(sItems);
        sList = new ListView(this);
        sList.setDivider(null);
        sList.setPadding(dp(10), 0, dp(10), dp(6));
        sList.setClipToPadding(false);
        sList.setAdapter(sAdapter);
        sList.setOnItemClickListener((p, v, pos, id) -> playStation(sItems.get(pos)));
        sList.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) { }
            @Override public void onScroll(AbsListView v, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 6) runSearch(false, false);
            }
        });
        box.addView(sList, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return box;
    }

    private void rebuildChips() {
        chipRow.removeAllViews();
        if (!activeLabel.isEmpty()) {
            TextView b = tv(activeLabel + "  \u2715", 13, Color.BLACK, true);
            b.setPadding(dp(14), dp(8), dp(14), dp(8));
            b.setBackground(shape(ORANGE, ORANGE, 18));
            b.setOnClickListener(v -> { activeLabel = ""; qCc = ""; qTag = ""; qLang = ""; chipIdx = 0; rebuildChips(); runSearch(true, true); });
            chipRow.addView(b, lp(WRAP, WRAP, 0, 3, 0, 3, 0));
        }
        for (int i = 0; i < CHIPS.length; i++) {
            final int idx = i;
            boolean on = activeLabel.isEmpty() && chipIdx == i;
            TextView b = tv(CHIPS[i][0], 13, on ? Color.BLACK : ACCENT, true);
            b.setPadding(dp(14), dp(8), dp(14), dp(8));
            b.setBackground(shape(on ? ACCENT : 0xFF0F1D33, ACCENT, 18));
            b.setOnClickListener(v -> {
                chipIdx = idx; activeLabel = "";
                qCc = CHIPS[idx][1]; qTag = CHIPS[idx][2]; qLang = CHIPS[idx][3];
                rebuildChips();
                runSearch(true, true);
            });
            chipRow.addView(b, lp(WRAP, WRAP, 0, 3, 0, 3, 0));
        }
    }

    /** Searches the stations. withStatic = play the radio hiss while we look, like tuning between stations. */
    private void runSearch(final boolean fresh, final boolean withStatic) {
        if (fresh) {
            sOffset = 0; sEnd = false;
            sItems.clear();
            sAdapter.notifyDataSetChanged();
        } else if (sLoading || sEnd) {
            return;
        }
        sLoading = true;
        final int gen = ++sGen;
        final long t0 = System.currentTimeMillis();
        final boolean useNoise = withStatic && fresh && !noise.isRunning() && pstate != RadioService.PLAYING && pstate != RadioService.LOADING;
        if (useNoise) { noise.start(0.5f); searchNoise = true; }
        sStatus.setText(fresh ? "Searching\u2026" : "Loading more\u2026");
        final String name = qName, cc = qCc, tag = qTag, lang = qLang;
        final int offset = sOffset;
        pool.execute(() -> {
            List<Station> r = null;
            try { r = Radio.search(name, cc, tag, lang, offset, 40); } catch (Exception ignored) { }
            final List<Station> res = r;
            runOnUiThread(() -> {
                if (gen != sGen) return;
                sLoading = false;
                if (res == null) {
                    sStatus.setText("No connection \u2014 check the internet and search again");
                } else {
                    sItems.addAll(res);
                    sOffset += res.size();
                    if (res.size() < 40) sEnd = true;
                    sAdapter.notifyDataSetChanged();
                    sStatus.setText(sItems.isEmpty() ? "No stations found" : sItems.size() + (sEnd ? "" : "+") + " stations \u2022 tap one to listen");
                }
                if (useNoise) {
                    long wait = Math.max(0, 700 - (System.currentTimeMillis() - t0));
                    handler.postDelayed(() -> { if (searchNoise && !waitingForStation) noise.fadeOut(350); searchNoise = false; }, wait);
                }
            });
        });
    }

    // ================================================================ COUNTRIES
    private View buildCountries() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText f = new EditText(this);
        f.setHint("\uD83D\uDD0D  Find a country\u2026");
        f.setHintTextColor(0xFF5A6B88);
        f.setTextColor(Color.WHITE);
        f.setSingleLine(true);
        f.setTextSize(16);
        f.setPadding(dp(18), dp(11), dp(18), dp(11));
        f.setBackground(shape(CARD, EDGE, 26));
        f.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { filterCountries(s.toString().trim()); }
        });
        box.addView(f, lp(MATCH, WRAP, 0, 12, 8, 12, 4));
        cList = new ListView(this);
        cList.setDivider(null);
        cList.setPadding(dp(10), 0, dp(10), dp(6));
        cList.setClipToPadding(false);
        cAdapter = new BaseAdapter() {
            @Override public int getCount() { return countriesShown.size(); }
            @Override public Object getItem(int i) { return countriesShown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int pos, View convert, ViewGroup parent) {
                LinearLayout row;
                if (convert == null) {
                    LinearLayout wrap = new LinearLayout(MainActivity.this);
                    wrap.setPadding(0, dp(3), 0, dp(3));
                    row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(14), dp(12), dp(14), dp(12));
                    row.setBackground(shape(CARD, EDGE, 14));
                    TextView flag = tv("", 26, Color.WHITE, false);
                    TextView name = tv("", 16, Color.WHITE, true);
                    name.setPadding(dp(12), 0, dp(8), 0);
                    TextView count = tv("", 13, ACCENT, true);
                    row.addView(flag);
                    row.addView(name, new LinearLayout.LayoutParams(0, WRAP, 1f));
                    row.addView(count);
                    wrap.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
                    wrap.setTag(new TextView[]{flag, name, count});
                    convert = wrap;
                }
                TextView[] t = (TextView[]) convert.getTag();
                Radio.Country c = countriesShown.get(pos);
                t[0].setText(Station.flagOf(c.cc));
                t[1].setText(c.name);
                t[2].setText(String.format(Locale.US, "%,d", c.count));
                return convert;
            }
        };
        cList.setAdapter(cAdapter);
        cList.setOnItemClickListener((p, v, pos, id) -> {
            Radio.Country c = countriesShown.get(pos);
            activeLabel = Station.flagOf(c.cc) + " " + c.name;
            qCc = c.cc; qTag = ""; qLang = ""; qName = "";
            searchEt.setText("");
            rebuildChips();
            showTab(1);
            runSearch(true, true);
        });
        box.addView(cList, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return box;
    }

    private void loadCountries() {
        pool.execute(() -> {
            List<Radio.Country> r = null;
            try { r = Radio.countries(); } catch (Exception ignored) { }
            final List<Radio.Country> res = r;
            runOnUiThread(() -> {
                if (res == null) return;
                countries.clear();
                countries.addAll(res);
                filterCountries("");
            });
        });
    }

    private void filterCountries(String q) {
        countriesShown.clear();
        String l = q.toLowerCase(Locale.ROOT);
        for (Radio.Country c : countries) if (l.isEmpty() || c.name.toLowerCase(Locale.ROOT).contains(l)) countriesShown.add(c);
        cAdapter.notifyDataSetChanged();
    }

    // =================================================================== GENRES
    private View buildGenres() {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(10), dp(10), dp(14));
        TextView h = tv("Browse by genre", 18, Color.WHITE, true);
        h.setPadding(dp(6), 0, 0, dp(8));
        box.addView(h);
        for (int i = 0; i < GENRES.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            for (int k = 0; k < 2; k++) {
                if (i + k >= GENRES.length) { row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f)); continue; }
                final String[] g = GENRES[i + k];
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setGravity(Gravity.CENTER);
                c.setPadding(dp(8), dp(16), dp(8), dp(16));
                c.setBackground(shape(CARD, EDGE, 16));
                TextView e = tv(g[0], 28, Color.WHITE, false);
                e.setGravity(Gravity.CENTER);
                TextView n = tv(g[1], 14, Color.WHITE, true);
                n.setGravity(Gravity.CENTER);
                n.setPadding(0, dp(4), 0, 0);
                c.addView(e);
                c.addView(n);
                c.setOnClickListener(v -> {
                    activeLabel = g[0] + " " + g[1];
                    qTag = g[2]; qCc = ""; qLang = ""; qName = "";
                    searchEt.setText("");
                    rebuildChips();
                    showTab(1);
                    runSearch(true, true);
                });
                row.addView(c, lp(0, WRAP, 1f, 4, 4, 4, 4));
            }
            box.addView(row);
        }
        sv.addView(box);
        return sv;
    }

    // ================================================================ FAVOURITES
    private View buildFavorites() {
        FrameLayout box = new FrameLayout(this);
        fAdapter = new StationAdapter(favs.all());
        fList = new ListView(this);
        fList.setDivider(null);
        fList.setPadding(dp(10), dp(8), dp(10), dp(6));
        fList.setClipToPadding(false);
        fList.setAdapter(fAdapter);
        fList.setOnItemClickListener((p, v, pos, id) -> playStation(favs.all().get(pos)));
        fEmpty = tv("\u2661\n\nNo saved stations yet.\nTap the heart on any station to keep it here.", 15, GRAY, false);
        fEmpty.setGravity(Gravity.CENTER);
        box.addView(fList, new FrameLayout.LayoutParams(MATCH, MATCH));
        box.addView(fEmpty, new FrameLayout.LayoutParams(MATCH, MATCH));
        return box;
    }

    private void refreshFavs() {
        if (fAdapter == null) return;
        fAdapter.notifyDataSetChanged();
        fEmpty.setVisibility(favs.all().isEmpty() ? View.VISIBLE : View.GONE);
        if (sAdapter != null) sAdapter.notifyDataSetChanged();
    }

    // ============================================================ STATION ROWS
    private class StationAdapter extends BaseAdapter {
        private final List<Station> items;
        StationAdapter(List<Station> items) { this.items = items; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            if (convert == null) {
                LinearLayout wrap = new LinearLayout(MainActivity.this);
                wrap.setPadding(0, dp(3), 0, dp(3));
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(9), dp(4), dp(9));
                Logo logo = new Logo(50);
                LinearLayout mid = new LinearLayout(MainActivity.this);
                mid.setOrientation(LinearLayout.VERTICAL);
                mid.setPadding(dp(12), 0, dp(6), 0);
                TextView name = tv("", 16, Color.WHITE, true);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView sub = tv("", 12, GRAY, false);
                sub.setSingleLine(true);
                sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
                mid.addView(name);
                mid.addView(sub);
                TextView heart = tv("\u2661", 24, ACCENT, true);
                heart.setGravity(Gravity.CENTER);
                heart.setPadding(dp(12), dp(6), dp(12), dp(6));
                row.addView(logo, new LinearLayout.LayoutParams(dp(50), dp(50)));
                row.addView(mid, new LinearLayout.LayoutParams(0, WRAP, 1f));
                row.addView(heart);
                wrap.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
                wrap.setTag(new View[]{row, logo, name, sub, heart});
                convert = wrap;
            }
            View[] v = (View[]) convert.getTag();
            final Station s = items.get(pos);
            boolean now = cur != null && cur.uuid.equals(s.uuid) && (pstate == RadioService.PLAYING || pstate == RadioService.LOADING);
            v[0].setBackground(shape(now ? 0xFF10294A : CARD, now ? ACCENT : EDGE, 14));
            ((Logo) v[1]).set(s);
            ((TextView) v[2]).setText((now ? "\u25B6  " : "") + s.name);
            ((TextView) v[3]).setText(s.subtitle());
            TextView heart = (TextView) v[4];
            boolean fav = favs.has(s.uuid);
            heart.setText(fav ? "\u2665" : "\u2661");
            heart.setTextColor(fav ? RED : ACCENT);
            heart.setOnClickListener(x -> { favs.toggle(s); refreshFavs(); updateTunerFav(); updatePlayerUi(); });
            return convert;
        }
    }

    // ================================================================ NOW PLAYING
    private FrameLayout buildNowPlaying() {
        FrameLayout fl = new FrameLayout(this);
        fl.setBackgroundColor(0xFF0A1424);
        fl.setClickable(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(14));

        TextView close = tv("\u25BE  Close", 15, GRAY, true);
        close.setPadding(dp(8), dp(10), dp(8), dp(10));
        close.setOnClickListener(v -> overlay.setVisibility(View.GONE));
        box.addView(close, lp(WRAP, WRAP, 0, 0, 0, 0, 6));

        nLogo = new Logo(170);
        box.addView(nLogo, lp(dp(170), dp(170), 0, 0, 4, 0, 12));
        nName = tv("", 24, Color.WHITE, true);
        nName.setGravity(Gravity.CENTER);
        nSub = tv("", 13, GRAY, false);
        nSub.setGravity(Gravity.CENTER);
        nNow = tv("", 16, ORANGE, true);
        nNow.setGravity(Gravity.CENTER);
        nNow.setPadding(dp(6), dp(10), dp(6), dp(4));
        nStatus = tv("", 13, GREEN, true);
        nStatus.setGravity(Gravity.CENTER);
        eq = new EqView(this);
        box.addView(nName, lp(MATCH, WRAP, 0, 0, 0, 0, 0));
        box.addView(nSub, lp(MATCH, WRAP, 0, 0, 2, 0, 0));
        box.addView(nNow, lp(MATCH, WRAP, 0, 0, 0, 0, 0));
        box.addView(nStatus, lp(MATCH, WRAP, 0, 0, 0, 0, 0));
        box.addView(eq, lp(MATCH, dp(56), 0, 10, 8, 10, 8));

        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER);
        nFav = tv("\u2661", 28, ACCENT, true);
        nPlay = tv("\u23F8", 32, Color.BLACK, true);
        nSleep = tv("\u23F1", 26, ACCENT, true);
        TextView share = tv("\u2197", 28, ACCENT, true);
        for (TextView t : new TextView[]{nFav, nSleep, share}) { t.setGravity(Gravity.CENTER); t.setPadding(dp(18), dp(8), dp(18), dp(8)); }
        nPlay.setGravity(Gravity.CENTER);
        nPlay.setBackground(shape(ACCENT, ACCENT, 38));
        nPlay.setOnClickListener(v -> togglePlay());
        nFav.setOnClickListener(v -> { if (cur != null) { favs.toggle(cur); refreshFavs(); updatePlayerUi(); updateTunerFav(); } });
        nSleep.setOnClickListener(v -> chooseSleep());
        share.setOnClickListener(v -> {
            if (cur == null) return;
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, "\uD83C\uDFA7 " + cur.name + (cur.country.isEmpty() ? "" : " (" + cur.country + ")") + " \u2014 listening on Telegradio\nhttps://telegradio.com/");
            startActivity(Intent.createChooser(i, null));
        });
        ctl.addView(nFav);
        ctl.addView(nPlay, new LinearLayout.LayoutParams(dp(76), dp(76)));
        ctl.addView(nSleep);
        ctl.addView(share);
        box.addView(ctl, lp(MATCH, WRAP, 0, 0, 8, 0, 8));

        TextView site = tv("\uD83C\uDF10  Open telegradio.com", 14, ACCENT, true);
        site.setPadding(dp(10), dp(10), dp(10), dp(10));
        site.setOnClickListener(v -> openUrl("https://telegradio.com/"));
        box.addView(site);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        fl.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        return fl;
    }

    private void openNowPlaying() {
        if (cur == null) return;
        overlay.setVisibility(View.VISIBLE);
        updatePlayerUi();
    }

    // ================================================================= PLAYBACK
    private void openUrl(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); } catch (Exception ignored) { }
    }

    private void playStation(Station s) {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotif = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        }
        cur = s;
        pstate = RadioService.LOADING;
        nowTxt = "";
        waitingForStation = true;
        searchNoise = false;
        if (!noise.isRunning()) noise.start(0.28f); else noise.setVolume(0.28f);
        Radio.click(s.uuid);
        Intent i = new Intent(this, RadioService.class).setAction(RadioService.ACT_PLAY).putExtra("s", s.toJson().toString());
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        updatePlayerUi();
    }

    private void togglePlay() {
        if (RadioService.inst != null) {
            waitingForStation = true;
            RadioService.inst.toggle();
        } else if (cur != null) {
            playStation(cur);
        }
    }

    private void stopRadio() {
        startService(new Intent(this, RadioService.class).setAction(RadioService.ACT_STOP));
        noise.stop();
        waitingForStation = false;
        pstate = RadioService.IDLE;
        updatePlayerUi();
    }

    private void onPlayerState(int st, Station s, String now) {
        if (s != null) cur = s;
        pstate = st;
        nowTxt = now == null ? "" : now;
        if (st == RadioService.PLAYING) {
            if (waitingForStation) { noise.fadeOut(450); waitingForStation = false; }
        } else if (st == RadioService.ERROR) {
            noise.stop();
            waitingForStation = false;
            Toast.makeText(this, "Could not play this station \u2014 try another one", Toast.LENGTH_LONG).show();
        } else if (st == RadioService.IDLE) {
            noise.stop();
            waitingForStation = false;
        }
        updatePlayerUi();
    }

    private void updatePlayerUi() {
        boolean has = cur != null && pstate != RadioService.IDLE;
        mini.setVisibility(has ? View.VISIBLE : View.GONE);
        boolean playing = pstate == RadioService.PLAYING || pstate == RadioService.LOADING;
        String status;
        switch (pstate) {
            case RadioService.LOADING: status = "Tuning\u2026"; break;
            case RadioService.PLAYING: status = "\u25CF LIVE"; break;
            case RadioService.PAUSED: status = "Paused"; break;
            case RadioService.ERROR: status = "Could not play"; break;
            default: status = "";
        }
        if (cur != null) {
            miniLogo.set(cur);
            miniName.setText(cur.name);
            miniSub.setText(!nowTxt.isEmpty() ? "\u266A " + nowTxt : status + (cur.country.isEmpty() ? "" : "  \u2022  " + cur.country));
            nLogo.set(cur);
            nName.setText(cur.name);
            nSub.setText(cur.subtitle());
            nNow.setText(nowTxt.isEmpty() ? "" : "\u266A " + nowTxt);
            nStatus.setText(status);
            nStatus.setTextColor(pstate == RadioService.ERROR ? RED : GREEN);
            boolean fav = favs.has(cur.uuid);
            nFav.setText(fav ? "\u2665" : "\u2661");
            nFav.setTextColor(fav ? RED : ACCENT);
        }
        miniPlay.setText(playing ? "\u23F8" : "\u25B6");
        nPlay.setText(playing ? "\u23F8" : "\u25B6");
        tPlay.setText(playing ? "\u23F8" : "\u25B6");
        eq.setActive(pstate == RadioService.PLAYING);
        if (tab != 0 || pstate != RadioService.LOADING) {
            if (pstate == RadioService.PLAYING) tStatus.setText("\u25CF LIVE \u2014 " + (cur == null ? "" : cur.name));
            else if (pstate == RadioService.PAUSED) tStatus.setText("Paused");
        }
        sAdapter.notifyDataSetChanged();
        fAdapter.notifyDataSetChanged();
    }

    // ================================================================ SLEEP TIMER
    private final Runnable sleepTick = new Runnable() {
        @Override public void run() {
            if (RadioService.sleepAtMs > 0) {
                long left = Math.max(0, RadioService.sleepAtMs - System.currentTimeMillis()) / 60000L;
                nSleep.setText("\u23F1 " + (left + 1) + "m");
                nSleep.setTextSize(15);
            } else {
                nSleep.setText("\u23F1");
                nSleep.setTextSize(26);
            }
            handler.postDelayed(this, 20000);
        }
    };

    private void chooseSleep() {
        final int[] mins = {15, 30, 45, 60, 90, 0};
        String[] names = {"15 minutes", "30 minutes", "45 minutes", "1 hour", "1.5 hours", "Off"};
        new AlertDialog.Builder(this).setTitle("Sleep timer").setItems(names, (d, w) -> {
            if (RadioService.inst == null) return;
            startService(new Intent(this, RadioService.class).setAction(RadioService.ACT_SLEEP).putExtra("min", mins[w]));
            Toast.makeText(this, mins[w] == 0 ? "Sleep timer off" : "The radio will stop in " + names[w], Toast.LENGTH_SHORT).show();
            handler.postDelayed(sleepTick, 400);
        }).show();
    }
}
