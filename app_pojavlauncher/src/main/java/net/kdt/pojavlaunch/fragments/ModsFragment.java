package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * S-Launcher mod browser. Searches Modrinth for mods, shaders and resource packs and installs
 * the file into the right folder of the profile the user picks. Profiles that can run the
 * content (matching Minecraft version and mod loader) are listed as recommended.
 */
public class ModsFragment extends Fragment {
    public static final String TAG = "ModsFragment";

    private static final String API = "https://api.modrinth.com/v2";
    private static final String[] TYPES = {"mod", "shader", "resourcepack"};
    private static final String[] TYPE_LABELS = {"MODLAR", "SHADER", "KAYNAK PAKETİ"};
    private static final String[] TYPE_DIRS = {"mods", "shaderpacks", "resourcepacks"};
    private static final Pattern MC_PATTERN = Pattern.compile(
            "(?<![\\d.])(1\\.\\d{1,2}(?:\\.\\d{1,2})?|2[6-9]\\.\\d{1,2}(?:\\.\\d{1,2})?)(?![\\d.])");

    private static class Hit {
        String id, title, author, description;
        long downloads;
    }

    private static class Option {
        String label;
        MinecraftProfile profile;
        String mcVersion;
        String loader;
        JsonObject version;
    }

    private final List<Hit> mHits = new ArrayList<>();
    private final Button[] mChips = new Button[3];
    private int mType = 0;
    private EditText mSearchEdit;
    private TextView mStatus;
    private BaseAdapter mAdapter;
    private int mSearchToken = 0;
    private static final int PAGE_SIZE = 50;
    private int mOffset = 0;
    private int mTotalHits = 0;
    private boolean mLoadingPage = false;
    private String mLastQuery = "";

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int gold() {
        return ContextCompat.getColor(requireContext(), R.color.s_gold);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.s_bg_main);
        root.setPadding(dp(14), dp(10), dp(14), dp(10));

        TextView title = new TextView(requireContext());
        title.setText("MODLAR  ·  MODRINTH");
        title.setTextColor(gold());
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.15f);
        root.addView(title);

        LinearLayout chips = new LinearLayout(requireContext());
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        chipLp.setMargins(dp(3), dp(10), dp(3), dp(6));
        for (int i = 0; i < 3; i++) {
            final int index = i;
            Button b = new Button(requireContext());
            b.setText(TYPE_LABELS[i]);
            b.setTextSize(12);
            b.setTypeface(Typeface.DEFAULT_BOLD);
            b.setOnClickListener(v -> {
                mType = index;
                refreshChips();
                search();
            });
            mChips[i] = b;
            chips.addView(b, chipLp);
        }
        root.addView(chips);

        LinearLayout searchRow = new LinearLayout(requireContext());
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        mSearchEdit = new EditText(requireContext());
        mSearchEdit.setHint("Ara…");
        mSearchEdit.setHintTextColor(Color.parseColor("#80FFFFFF"));
        mSearchEdit.setTextColor(Color.WHITE);
        mSearchEdit.setSingleLine(true);
        mSearchEdit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        mSearchEdit.setBackgroundResource(R.drawable.s_card_bg);
        mSearchEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        mSearchEdit.setOnEditorActionListener((v, actionId, event) -> {
            search();
            return true;
        });
        searchRow.addView(mSearchEdit, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button go = new Button(requireContext());
        go.setText("ARA");
        go.setTextSize(12);
        go.setBackgroundResource(R.drawable.mine_button_unfocused);
        go.setTextColor(Color.BLACK);
        go.setTypeface(Typeface.DEFAULT_BOLD);
        go.setOnClickListener(v -> search());
        LinearLayout.LayoutParams goLp = new LinearLayout.LayoutParams(dp(80), dp(44));
        goLp.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(go, goLp);
        root.addView(searchRow);

        mStatus = new TextView(requireContext());
        mStatus.setTextColor(Color.parseColor("#BDBDBD"));
        mStatus.setTextSize(12);
        mStatus.setPadding(dp(4), dp(8), dp(4), dp(4));
        root.addView(mStatus);

        ListView list = new ListView(requireContext());
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        list.setClipToPadding(false);
        mAdapter = new BaseAdapter() {
            @Override public int getCount() { return mHits.size(); }
            @Override public Object getItem(int position) { return mHits.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                TextView t1, t2;
                if (convertView == null) {
                    row = new LinearLayout(parent.getContext());
                    row.setOrientation(LinearLayout.VERTICAL);
                    row.setBackgroundResource(R.drawable.s_card_bg);
                    row.setPadding(dp(14), dp(10), dp(14), dp(10));
                    t1 = new TextView(parent.getContext());
                    t1.setTextColor(Color.WHITE);
                    t1.setTextSize(15);
                    t1.setTypeface(Typeface.DEFAULT_BOLD);
                    t1.setSingleLine(true);
                    t1.setEllipsize(TextUtils.TruncateAt.END);
                    t2 = new TextView(parent.getContext());
                    t2.setTextColor(Color.parseColor("#BDBDBD"));
                    t2.setTextSize(12);
                    t2.setMaxLines(2);
                    t2.setEllipsize(TextUtils.TruncateAt.END);
                    row.addView(t1);
                    row.addView(t2);
                } else {
                    row = (LinearLayout) convertView;
                    t1 = (TextView) row.getChildAt(0);
                    t2 = (TextView) row.getChildAt(1);
                }
                Hit h = mHits.get(position);
                t1.setText(h.title);
                t2.setText(h.author + "  •  " + formatCount(h.downloads) + " indirme\n" + h.description);
                return row;
            }
        };
        list.setAdapter(mAdapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                if (total > 0 && first + visible >= total - 6 && !mLoadingPage && mHits.size() < mTotalHits) {
                    loadPage();
                }
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> onHitClicked(mHits.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        refreshChips();
        search();
        return root;
    }

    private void refreshChips() {
        for (int i = 0; i < 3; i++) {
            boolean active = i == mType;
            mChips[i].setBackgroundResource(active ? R.drawable.mine_button_unfocused : R.drawable.s_card_bg);
            mChips[i].setTextColor(active ? Color.BLACK : Color.WHITE);
        }
    }

    private static String formatCount(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format(Locale.US, "%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    private void ui(Runnable r) {
        Activity a = getActivity();
        if (a != null) a.runOnUiThread(r);
    }

    // ------------------------------------------------------------------ search

    /** Starts a new search from the first page */
    private void search() {
        mSearchToken++;
        mHits.clear();
        mAdapter.notifyDataSetChanged();
        mOffset = 0;
        mTotalHits = 0;
        mLoadingPage = false;
        mLastQuery = mSearchEdit.getText().toString().trim();
        loadPage();
    }

    /** Loads the next page of the current search (called again while scrolling) */
    private void loadPage() {
        if (mLoadingPage) return;
        mLoadingPage = true;
        final String query = mLastQuery;
        final int type = mType;
        final int token = mSearchToken;
        final int offset = mOffset;
        mStatus.setText(offset == 0 ? "Aranıyor…" : "Daha fazla yükleniyor…");
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String facets = "[[\"project_type:" + TYPES[type] + "\"]]";
                String url = API + "/search?limit=" + PAGE_SIZE + "&offset=" + offset
                        + "&index=" + (query.isEmpty() ? "downloads" : "relevance")
                        + "&facets=" + URLEncoder.encode(facets, "UTF-8")
                        + (query.isEmpty() ? "" : "&query=" + URLEncoder.encode(query, "UTF-8"));
                JsonObject root = new JsonParser().parse(DownloadUtils.downloadString(url)).getAsJsonObject();
                final int total = root.has("total_hits") ? root.get("total_hits").getAsInt() : 0;
                JsonArray hits = root.getAsJsonArray("hits");
                final List<Hit> result = new ArrayList<>();
                for (JsonElement e : hits) {
                    JsonObject o = e.getAsJsonObject();
                    Hit h = new Hit();
                    h.id = o.get("project_id").getAsString();
                    h.title = str(o, "title");
                    h.author = str(o, "author");
                    h.description = str(o, "description");
                    h.downloads = o.has("downloads") ? o.get("downloads").getAsLong() : 0;
                    result.add(h);
                }
                ui(() -> {
                    if (token != mSearchToken) return;
                    mLoadingPage = false;
                    mHits.addAll(result);
                    mOffset = offset + result.size();
                    mTotalHits = total;
                    mAdapter.notifyDataSetChanged();
                    mStatus.setText(mHits.isEmpty() ? "Sonuç bulunamadı." : mHits.size() + " / " + total + " sonuç");
                });
            } catch (Exception e) {
                ui(() -> {
                    if (token != mSearchToken) return;
                    mLoadingPage = false;
                    mStatus.setText("Bağlantı hatası: " + e.getMessage());
                });
            }
        });
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }

    // ----------------------------------------------------------- profile logic

    @Nullable
    private static String resolveMcVersion(String versionId) {
        if (versionId == null) return null;
        String id = versionId;
        try {
            File json = new File(Tools.DIR_HOME_VERSION + "/" + id + "/" + id + ".json");
            if (json.exists()) {
                JsonObject o = new JsonParser().parse(readFile(json)).getAsJsonObject();
                if (o.has("inheritsFrom")) id = o.get("inheritsFrom").getAsString();
            }
        } catch (Exception ignored) {
        }
        Matcher m = MC_PATTERN.matcher(id);
        String last = null;
        while (m.find()) last = m.group(1);
        if (last != null) return last;
        Matcher n = Pattern.compile("neoforge-(\\d+)\\.(\\d+)").matcher(versionId);
        if (n.find()) {
            return "0".equals(n.group(2)) ? "1." + n.group(1) : "1." + n.group(1) + "." + n.group(2);
        }
        return null;
    }

    private static String detectLoader(String versionId) {
        String l = versionId == null ? "" : versionId.toLowerCase(Locale.ROOT);
        if (l.contains("neoforge")) return "neoforge";
        if (l.contains("forge")) return "forge";
        if (l.contains("fabric")) return "fabric";
        if (l.contains("quilt")) return "quilt";
        return "vanilla";
    }

    private static String readFile(File f) throws Exception {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static boolean arrayHas(JsonObject o, String key, String value) {
        if (!o.has(key)) return false;
        for (JsonElement e : o.getAsJsonArray(key)) {
            if (value.equals(e.getAsString())) return true;
        }
        return false;
    }

    /** Newest version of the project that can run on the given profile, or null */
    @Nullable
    private static JsonObject findCompatible(JsonArray versions, int type, String mc, String loader) {
        if (mc == null) return null;
        for (JsonElement e : versions) {
            JsonObject v = e.getAsJsonObject();
            if (!arrayHas(v, "game_versions", mc)) continue;
            if (type == 0) {
                boolean ok = arrayHas(v, "loaders", loader)
                        || ("quilt".equals(loader) && arrayHas(v, "loaders", "fabric"));
                if (!ok) continue;
            }
            return v;
        }
        return null;
    }

    // ------------------------------------------------------------------ install

    private void onHitClicked(Hit hit) {
        final int type = mType;
        Toast.makeText(requireContext(), "Sürümler alınıyor…", Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                JsonArray versions = new JsonParser().parse(
                        DownloadUtils.downloadString(API + "/project/" + hit.id + "/version")).getAsJsonArray();
                LauncherProfiles.load();
                String current = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
                final List<Option> options = new ArrayList<>();
                for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                    MinecraftProfile p = entry.getValue();
                    if (p == null || p.lastVersionId == null) continue;
                    String mc = resolveMcVersion(p.lastVersionId);
                    String loader = detectLoader(p.lastVersionId);
                    JsonObject v = findCompatible(versions, type, mc, loader);
                    if (v == null) continue;
                    Option o = new Option();
                    o.profile = p;
                    o.mcVersion = mc;
                    o.loader = loader;
                    o.version = v;
                    String name = TextUtils.isEmpty(p.name) ? entry.getKey() : p.name;
                    boolean isCurrent = entry.getKey().equals(current);
                    o.label = (isCurrent ? "★ Önerilen: " : "✓ ") + name + "  (" + mc
                            + ("vanilla".equals(loader) ? "" : ", " + loader) + ")";
                    if (isCurrent) options.add(0, o); else options.add(o);
                }
                final Set<String> supported = new LinkedHashSet<>();
                for (JsonElement e : versions) {
                    for (JsonElement g : e.getAsJsonObject().getAsJsonArray("game_versions")) {
                        if (supported.size() < 12) supported.add(g.getAsString());
                    }
                }
                ui(() -> showTargetDialog(hit, type, options, supported));
            } catch (Exception e) {
                ui(() -> Toast.makeText(requireContext(), "Hata: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showTargetDialog(Hit hit, int type, List<Option> options, Set<String> supported) {
        if (!isAdded()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(requireContext());
        b.setTitle(hit.title);
        if (options.isEmpty()) {
            b.setMessage("Kurulu profillerinde bu içerikle uyumlu bir sürüm yok.\n\nDesteklenen sürümler: "
                    + TextUtils.join(", ", supported)
                    + ("mod".equals(TYPES[type]) ? "\n\nİpucu: Modlar için Fabric/Forge profili gerekir." : ""));
            b.setPositiveButton(android.R.string.ok, null);
        } else {
            String[] labels = new String[options.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = options.get(i).label;
            b.setTitle(hit.title + " — hangi sürüme kurulsun?");
            b.setItems(labels, (d, which) -> install(options.get(which), type));
            b.setNegativeButton(android.R.string.cancel, null);
        }
        b.show();
    }

    private void install(Option option, int type) {
        final android.content.Context ctx = requireContext().getApplicationContext();
        Toast.makeText(ctx, "İndiriliyor…", Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File dir = new File(Tools.getGameDirPath(option.profile), TYPE_DIRS[type]);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                List<String> installed = new ArrayList<>();
                installed.add(downloadVersionFile(option.version, dir));

                // Required dependencies (e.g. Fabric API) are fetched automatically
                if (type == 0 && option.version.has("dependencies")) {
                    for (JsonElement e : option.version.getAsJsonArray("dependencies")) {
                        JsonObject dep = e.getAsJsonObject();
                        if (!"required".equals(str(dep, "dependency_type")) || str(dep, "project_id").isEmpty()) continue;
                        try {
                            String url = API + "/project/" + str(dep, "project_id") + "/version"
                                    + "?game_versions=" + URLEncoder.encode("[\"" + option.mcVersion + "\"]", "UTF-8")
                                    + "&loaders=" + URLEncoder.encode("[\"" + option.loader + "\"]", "UTF-8");
                            JsonArray list = new JsonParser().parse(DownloadUtils.downloadString(url)).getAsJsonArray();
                            if (list.size() > 0) installed.add(downloadVersionFile(list.get(0).getAsJsonObject(), dir));
                        } catch (Exception ignored) {
                        }
                    }
                }
                final String message = TextUtils.join("\n", installed) + "\n\n→ " + dir.getAbsolutePath();
                ui(() -> {
                    if (isAdded()) new AlertDialog.Builder(requireContext())
                            .setTitle("✓ Kuruldu")
                            .setMessage(message)
                            .setPositiveButton(android.R.string.ok, null).show();
                    else Toast.makeText(ctx, "Kuruldu", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                ui(() -> Toast.makeText(ctx, "İndirme hatası: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static String downloadVersionFile(JsonObject version, File dir) throws Exception {
        JsonArray files = version.getAsJsonArray("files");
        JsonObject chosen = files.get(0).getAsJsonObject();
        for (JsonElement e : files) {
            JsonObject f = e.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) { chosen = f; break; }
        }
        String name = new File(str(chosen, "filename")).getName();
        DownloadUtils.downloadFile(str(chosen, "url"), new File(dir, name));
        return name;
    }
}
