package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.SheetDialog;
import net.kdt.pojavlaunch.utils.SkinUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * S-Launcher profile panel: character preview and skin import on the left,
 * nickname, friends (beta), account switching and sign out on the right.
 */
public class ProfileFragment extends Fragment {
    public static final String TAG = "ProfileFragment";
    private static final Pattern NICK_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final String FRIENDS_PREFS = "s_friends";
    private static final String FRIENDS_KEY = "list";

    private interface TextHandler {
        /** @return true if the input was accepted and the dialog can close */
        boolean accept(String text);
    }

    private final ActivityResultLauncher<String> mSkinPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onSkinPicked);

    private MinecraftAccount mAccount;
    private ImageView mCharacter;
    private TextView mNameView;
    private TextView mTypeView;
    private TextView mNickValue;
    private boolean mLandscape;

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private int gold() {
        return getResources().getColor(R.color.s_gold);
    }

    private void ui(Runnable r) {
        if (getActivity() != null) getActivity().runOnUiThread(r);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.s_card_bg);
        l.setPadding(dp(16), dp(14), dp(16), dp(16));
        return l;
    }

    private Button button(String text, boolean primary) {
        Button b = new Button(requireContext());
        b.setText(text);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackgroundResource(primary ? R.drawable.mine_button_unfocused : R.drawable.s_card_bg);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private TextView label(String text, int color, float size, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextColor(color);
        t.setTextSize(size);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.s_bg_main);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(mLandscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        scroll.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ------------------------------------------------------------- left: character
        LinearLayout left = card();
        left.setGravity(Gravity.CENTER_HORIZONTAL);
        mCharacter = new ImageView(requireContext());
        mCharacter.setAdjustViewBounds(true);
        mCharacter.setScaleType(ImageView.ScaleType.FIT_CENTER);
        left.addView(mCharacter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(mLandscape ? 200 : 260)));

        mNameView = label("", Color.WHITE, 20, true);
        mNameView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.topMargin = dp(8);
        left.addView(mNameView, nameLp);
        mTypeView = label("", gold(), 12, false);
        mTypeView.setGravity(Gravity.CENTER);
        left.addView(mTypeView);

        Button importSkin = button("SKİN İÇE AKTAR", true);
        importSkin.setOnClickListener(v -> {
            if (mAccount == null) {
                Toast.makeText(requireContext(), "Önce bir hesap ekle", Toast.LENGTH_SHORT).show();
                return;
            }
            mSkinPicker.launch("image/*");
        });
        left.addView(importSkin);
        Button resetSkin = button("SKİNİ SIFIRLA", false);
        resetSkin.setOnClickListener(v -> {
            if (mAccount == null) return;
            SkinUtils.deleteSkin(mAccount.username);
            refresh();
            refreshPill();
            Toast.makeText(requireContext(), "Varsayılan skin geri geldi", Toast.LENGTH_SHORT).show();
        });
        left.addView(resetSkin);
        TextView hint = label("Skin PNG dosyası 64x64 (veya 64x32) olmalı. İçe aktarılan skin launcher'da gösterilir; oyunda görünmesi için sunucu veya skin modu gerekir.",
                Color.parseColor("#9E9E9E"), 10.5f, false);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(10);
        left.addView(hint, hintLp);

        // ------------------------------------------------------------- right: actions
        LinearLayout right = card();
        TextView title = label("PROFİL", gold(), 16, true);
        title.setLetterSpacing(0.2f);
        right.addView(title);

        TextView nickLabel = label("Kullanıcı adı", Color.parseColor("#BDBDBD"), 12, false);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = dp(12);
        right.addView(nickLabel, nl);

        mNickValue = label("", Color.WHITE, 16, true);
        mNickValue.setBackgroundResource(R.drawable.s_card_bg);
        mNickValue.setGravity(Gravity.CENTER_VERTICAL);
        mNickValue.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams vl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        vl.topMargin = dp(4);
        right.addView(mNickValue, vl);
        mNickValue.setOnClickListener(v -> showRenameDialog());

        Button rename = button("ADI DEĞİŞTİR", true);
        rename.setOnClickListener(v -> showRenameDialog());
        right.addView(rename);

        Button copyUuid = button("UUID'Yİ KOPYALA", false);
        copyUuid.setOnClickListener(v -> copyUuid());
        right.addView(copyUuid);

        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.parseColor("#59FFC400"));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dl.topMargin = dp(16);
        dl.bottomMargin = dp(8);
        right.addView(divider, dl);

        Button friends = button("ARKADAŞLAR  (BETA)", false);
        friends.setOnClickListener(v -> showFriendsSheet());
        right.addView(friends);

        Button switchAccount = button("HESAP DEĞİŞTİR", false);
        switchAccount.setOnClickListener(v -> showAccountsSheet());
        right.addView(switchAccount);

        Button signOut = button("ÇIKIŞ YAP", false);
        signOut.setTextColor(Color.parseColor("#FF6B6B"));
        signOut.setOnClickListener(v -> confirmSignOut());
        right.addView(signOut);

        if (mLandscape) {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            l.setMargins(0, 0, dp(6), 0);
            content.addView(left, l);
            LinearLayout.LayoutParams r = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.3f);
            r.setMargins(dp(6), 0, 0, 0);
            content.addView(right, r);
        } else {
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            l.setMargins(0, 0, 0, dp(10));
            content.addView(left, l);
            content.addView(right, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        refresh();
        return scroll;
    }

    // ------------------------------------------------------------------- state

    private void refresh() {
        Context ctx = requireContext();
        mAccount = PojavProfile.getCurrentProfileContent(ctx, null);
        MinecraftAccount acc = mAccount;
        int h = dp(mLandscape ? 200 : 260);
        if (acc == null) {
            // The question mark is only for "no account at all"
            mNameView.setText("Hesap yok");
            mTypeView.setText("Henüz giriş yapılmadı — HESAP DEĞİŞTİR → Hesap ekle");
            mNickValue.setText("—");
            mCharacter.setImageBitmap(SkinUtils.placeholder(null, h / 2));
            return;
        }
        mNameView.setText(acc.username);
        mTypeView.setText(acc.isLocal() ? "Çevrimdışı hesap" : "Microsoft hesabı");
        mNickValue.setText(acc.username);
        loadCharacter(acc, h);
    }

    private void loadCharacter(MinecraftAccount acc, int h) {
        // imported skin, otherwise the default Steve skin
        Bitmap skin = SkinUtils.skinOrDefault(requireContext(), acc.username);
        mCharacter.setImageBitmap(SkinUtils.characterFromSkin(skin, h));

        if (acc.isLocal() || acc.profileId == null || SkinUtils.loadSkin(acc.username) != null) return;
        // Online account without an imported skin: try the real character render
        final String uuid = acc.profileId;
        final String username = acc.username;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                File f = new File(Tools.DIR_CACHE, "body_" + uuid + ".png");
                if (!f.exists() || System.currentTimeMillis() - f.lastModified() > 24L * 3600L * 1000L) {
                    DownloadUtils.downloadFile("https://mc-heads.net/player/" + uuid + "/300", f);
                }
                final Bitmap body = BitmapFactory.decodeFile(f.getAbsolutePath());
                if (body != null) ui(() -> {
                    if (isAdded() && mAccount != null && username.equals(mAccount.username)) {
                        mCharacter.setImageBitmap(body);
                    }
                });
            } catch (Exception ignored) {
            }
        });
    }

    private void refreshPill() {
        if (getActivity() instanceof LauncherActivity) ((LauncherActivity) getActivity()).refreshProfilePill();
    }

    // ----------------------------------------------------------------- generic UI

    /** Opens a small input dialog (dialog windows reliably show the keyboard) */
    private void showInputDialog(String title, String initial, String positive, TextHandler handler) {
        Context ctx = requireContext();
        FrameLayout wrapper = new FrameLayout(ctx);
        final EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(initial);
        input.setSelection(initial.length());
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.parseColor("#80FFFFFF"));
        wrapper.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(dp(20), dp(8), dp(20), 0);

        final AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(wrapper)
                .setPositiveButton(positive, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        input.requestFocus();
        // set after show() so invalid input keeps the dialog open
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (handler.accept(input.getText().toString().trim())) dialog.dismiss();
        });
    }

    private LinearLayout row(@Nullable Bitmap avatar, String title, @Nullable String subtitle,
                             @Nullable String trailing, int trailingColor,
                             @Nullable View.OnClickListener click) {
        Context ctx = requireContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.s_card_bg);
        row.setPadding(dp(12), dp(10), dp(14), dp(10));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = dp(8);
        row.setLayoutParams(rp);

        if (avatar != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageBitmap(avatar);
            row.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(label(title, Color.WHITE, 15, true));
        if (subtitle != null) texts.addView(label(subtitle, Color.parseColor("#BDBDBD"), 11.5f, false));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = avatar != null ? dp(12) : 0;
        row.addView(texts, tp);
        if (trailing != null) row.addView(label(trailing, trailingColor, 18, true));
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    // ------------------------------------------------------------------ actions

    private void onSkinPicked(@Nullable Uri uri) {
        if (uri == null || mAccount == null || !isAdded()) return;
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (!SkinUtils.isValidSkin(b)) {
                Toast.makeText(requireContext(), "Geçersiz skin: 64x64 veya 64x32 PNG olmalı", Toast.LENGTH_LONG).show();
                return;
            }
            if (SkinUtils.saveSkin(mAccount.username, b)) {
                Toast.makeText(requireContext(), "Skin içe aktarıldı", Toast.LENGTH_SHORT).show();
                refresh();
                refreshPill();
            } else {
                Toast.makeText(requireContext(), "Skin kaydedilemedi", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Skin okunamadı: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void copyUuid() {
        if (mAccount == null) {
            Toast.makeText(requireContext(), "Önce bir hesap ekle", Toast.LENGTH_SHORT).show();
            return;
        }
        String uuid = mAccount.isLocal()
                ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + mAccount.username).getBytes(StandardCharsets.UTF_8)).toString()
                : mAccount.profileId;
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("uuid", uuid));
        Toast.makeText(requireContext(), "UUID kopyalandı: " + uuid, Toast.LENGTH_LONG).show();
    }

    private void showRenameDialog() {
        final MinecraftAccount acc = mAccount;
        if (acc == null) {
            Toast.makeText(requireContext(), "Önce bir hesap ekle", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!acc.isLocal()) {
            Toast.makeText(requireContext(), "Microsoft hesabında ad değiştirilemez", Toast.LENGTH_LONG).show();
            return;
        }
        showInputDialog("Kullanıcı adı", acc.username, "KAYDET", text -> {
            if (!NICK_PATTERN.matcher(text).matches()) {
                Toast.makeText(requireContext(), "Ad 3-16 karakter olmalı (harf, rakam, _)", Toast.LENGTH_LONG).show();
                return false;
            }
            if (text.equals(acc.username)) return true;
            if (new File(Tools.DIR_ACCOUNT_NEW, text + ".json").exists()) {
                Toast.makeText(requireContext(), "Bu adda bir hesap zaten var", Toast.LENGTH_LONG).show();
                return false;
            }
            String oldName = acc.username;
            try {
                acc.username = text;
                acc.save();
                File old = new File(Tools.DIR_ACCOUNT_NEW, oldName + ".json");
                //noinspection ResultOfMethodCallIgnored
                if (old.exists()) old.delete();
                File oldSkin = SkinUtils.skinFile(oldName);
                //noinspection ResultOfMethodCallIgnored
                if (oldSkin.exists()) oldSkin.renameTo(SkinUtils.skinFile(text));
                PojavProfile.setCurrentProfile(requireContext(), text);
                Toast.makeText(requireContext(), "Ad değiştirildi: " + text, Toast.LENGTH_SHORT).show();
                requireActivity().recreate();
                return true;
            } catch (Exception e) {
                acc.username = oldName;
                Toast.makeText(requireContext(), "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return false;
            }
        });
    }

    private List<String> accountNames() {
        List<String> names = new ArrayList<>();
        String[] files = new File(Tools.DIR_ACCOUNT_NEW).list();
        if (files != null) {
            for (String f : files) {
                if (f.endsWith(".json")) names.add(f.substring(0, f.length() - 5));
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Bottom sheet with all accounts and the "add account" row */
    private void showAccountsSheet() {
        Context ctx = requireContext();
        final Dialog[] holder = new Dialog[1];
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        String current = mAccount == null ? null : mAccount.username;

        for (String name : accountNames()) {
            MinecraftAccount acc = MinecraftAccount.load(name);
            if (acc == null) continue;
            final boolean isCurrent = name.equals(current);
            list.addView(row(SkinUtils.avatarFor(ctx, acc, dp(44)), name,
                    acc.isLocal() ? "Çevrimdışı hesap" : "Microsoft hesabı",
                    isCurrent ? "✓" : null, gold(), v -> {
                        holder[0].dismiss();
                        if (!isCurrent) {
                            PojavProfile.setCurrentProfile(requireContext(), name);
                            requireActivity().recreate();
                        }
                    }));
        }
        list.addView(row(null, "＋  Hesap ekle", "Microsoft veya çevrimdışı hesap", null, 0, v -> {
            holder[0].dismiss();
            Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        }));
        holder[0] = SheetDialog.show(ctx, "HESAPLAR", list);
    }

    // ------------------------------------------------------------------ friends (beta)

    private Set<String> loadFriends() {
        SharedPreferences sp = requireContext().getSharedPreferences(FRIENDS_PREFS, Context.MODE_PRIVATE);
        return new HashSet<>(sp.getStringSet(FRIENDS_KEY, new HashSet<>()));
    }

    private void saveFriends(Set<String> friends) {
        requireContext().getSharedPreferences(FRIENDS_PREFS, Context.MODE_PRIVATE)
                .edit().putStringSet(FRIENDS_KEY, friends).apply();
    }

    private void showFriendsSheet() {
        Context ctx = requireContext();
        final Dialog[] holder = new Dialog[1];
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);

        TextView note = label("BETA: arkadaş listesi şimdilik bu cihazda tutulur. Çevrimiçi durum ve davetler sunucu desteğiyle sonraki sürümde gelecek.",
                Color.parseColor("#9E9E9E"), 11, false);
        note.setPadding(dp(4), 0, dp(4), dp(10));
        list.addView(note);

        List<String> sorted = new ArrayList<>(loadFriends());
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        if (sorted.isEmpty()) {
            TextView empty = label("Henüz arkadaş eklemedin.", Color.WHITE, 13, false);
            empty.setPadding(dp(4), dp(4), dp(4), dp(12));
            list.addView(empty);
        }
        for (String friend : sorted) {
            list.addView(row(SkinUtils.placeholder(friend, dp(44)), friend, "Durum: yakında", "✕",
                    Color.parseColor("#FF6B6B"), v -> {
                        Set<String> friends = loadFriends();
                        friends.remove(friend);
                        saveFriends(friends);
                        holder[0].dismiss();
                        showFriendsSheet();
                    }));
        }
        list.addView(row(null, "＋  Arkadaş ekle", "Kullanıcı adıyla", null, 0, v -> {
            holder[0].dismiss();
            showInputDialog("Arkadaş ekle", "", "EKLE", text -> {
                if (!NICK_PATTERN.matcher(text).matches()) {
                    Toast.makeText(requireContext(), "Geçerli bir kullanıcı adı yaz (3-16, harf/rakam/_)", Toast.LENGTH_LONG).show();
                    return false;
                }
                Set<String> friends = loadFriends();
                friends.add(text);
                saveFriends(friends);
                showFriendsSheet();
                return true;
            });
        }));
        holder[0] = SheetDialog.show(ctx, "ARKADAŞLAR (BETA)", list);
    }

    // ---------------------------------------------------------------- sign out

    private void confirmSignOut() {
        if (mAccount == null) {
            Toast.makeText(requireContext(), "Zaten giriş yapılmamış", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Çıkış yap")
                .setMessage(mAccount.username + " hesabı bu cihazdan kaldırılsın mı?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("ÇIKIŞ YAP", (d, w) -> signOut())
                .show();
    }

    private void signOut() {
        mcAccountSpinner spinner = requireActivity().findViewById(R.id.account_spinner);
        if (spinner != null) spinner.removeCurrentAccount();
        List<String> remaining = accountNames();
        if (!remaining.isEmpty()) {
            PojavProfile.setCurrentProfile(requireContext(), remaining.get(0));
            requireActivity().recreate();
        } else {
            Tools.backToMainMenu(requireActivity());
        }
    }
}
