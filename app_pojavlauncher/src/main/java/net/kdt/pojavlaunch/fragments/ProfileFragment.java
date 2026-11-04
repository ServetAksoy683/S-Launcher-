package net.kdt.pojavlaunch.fragments;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import net.kdt.pojavlaunch.utils.SkinUtils;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * S-Launcher profile panel: character preview and skin import on the left,
 * nickname, account switching and sign out on the right.
 */
public class ProfileFragment extends Fragment {
    public static final String TAG = "ProfileFragment";
    private static final Pattern NICK_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final ActivityResultLauncher<String> mSkinPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onSkinPicked);

    private MinecraftAccount mAccount;
    private ImageView mCharacter;
    private TextView mNameView;
    private TextView mTypeView;
    private EditText mNickEdit;
    private Button mSaveNick;
    private Button mCopyUuid;
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

        mNickEdit = new EditText(requireContext());
        mNickEdit.setSingleLine(true);
        mNickEdit.setTextColor(Color.WHITE);
        mNickEdit.setHintTextColor(Color.parseColor("#80FFFFFF"));
        mNickEdit.setBackgroundResource(R.drawable.s_card_bg);
        mNickEdit.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        el.topMargin = dp(4);
        right.addView(mNickEdit, el);

        mSaveNick = button("KAYDET", true);
        mSaveNick.setOnClickListener(v -> saveNick());
        right.addView(mSaveNick);

        mCopyUuid = button("UUID'Yİ KOPYALA", false);
        mCopyUuid.setOnClickListener(v -> {
            if (mAccount == null) return;
            ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("uuid", mAccount.profileId));
            Toast.makeText(requireContext(), "Kopyalandı", Toast.LENGTH_SHORT).show();
        });
        right.addView(mCopyUuid);

        View divider = new View(requireContext());
        divider.setBackgroundColor(Color.parseColor("#59FFC400"));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dl.topMargin = dp(16);
        dl.bottomMargin = dp(8);
        right.addView(divider, dl);

        Button addAccount = button("2. HESAP EKLE", false);
        addAccount.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), SelectAuthFragment.class, SelectAuthFragment.TAG, null));
        right.addView(addAccount);

        Button switchAccount = button("HESAP DEĞİŞTİR", false);
        switchAccount.setOnClickListener(v -> showSwitchDialog());
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
            mNameView.setText("Hesap yok");
            mTypeView.setText("Henüz giriş yapılmadı — \"2. HESAP EKLE\" ile başla");
            mNickEdit.setText("");
            mNickEdit.setEnabled(false);
            mSaveNick.setEnabled(false);
            mCopyUuid.setEnabled(false);
            mCharacter.setImageBitmap(SkinUtils.placeholder(null, h / 2));
            return;
        }
        boolean local = acc.isLocal();
        mNameView.setText(acc.username);
        mTypeView.setText(local ? "Çevrimdışı hesap" : "Microsoft hesabı");
        mNickEdit.setText(acc.username);
        mNickEdit.setEnabled(local);
        mSaveNick.setEnabled(local);
        mSaveNick.setAlpha(local ? 1f : 0.4f);
        mNickEdit.setAlpha(local ? 1f : 0.5f);
        if (!local) mNickEdit.setHint("Microsoft hesabında ad değiştirilemez");
        mCopyUuid.setEnabled(!local);
        mCopyUuid.setAlpha(local ? 0.4f : 1f);
        loadCharacter(acc, h);
    }

    private void loadCharacter(MinecraftAccount acc, int h) {
        Bitmap skin = SkinUtils.loadSkin(acc.username);
        if (skin != null) {
            mCharacter.setImageBitmap(SkinUtils.characterFromSkin(skin, h));
            return;
        }
        mCharacter.setImageBitmap(SkinUtils.placeholder(acc.username, h / 2));
        if (acc.isLocal() || acc.profileId == null) return;
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

    // ----------------------------------------------------------------- actions

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

    private void saveNick() {
        MinecraftAccount acc = mAccount;
        if (acc == null || !acc.isLocal()) return;
        String newName = mNickEdit.getText().toString().trim();
        if (!NICK_PATTERN.matcher(newName).matches()) {
            Toast.makeText(requireContext(), "Ad 3-16 karakter olmalı (harf, rakam, _)", Toast.LENGTH_LONG).show();
            return;
        }
        String oldName = acc.username;
        if (newName.equals(oldName)) return;
        if (new File(Tools.DIR_ACCOUNT_NEW, newName + ".json").exists()) {
            Toast.makeText(requireContext(), "Bu adda bir hesap zaten var", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            acc.username = newName;
            acc.save();
            File old = new File(Tools.DIR_ACCOUNT_NEW, oldName + ".json");
            //noinspection ResultOfMethodCallIgnored
            if (old.exists()) old.delete();
            File oldSkin = SkinUtils.skinFile(oldName);
            //noinspection ResultOfMethodCallIgnored
            if (oldSkin.exists()) oldSkin.renameTo(SkinUtils.skinFile(newName));
            PojavProfile.setCurrentProfile(requireContext(), newName);
            Toast.makeText(requireContext(), "Ad değiştirildi: " + newName, Toast.LENGTH_SHORT).show();
            requireActivity().recreate();
        } catch (Exception e) {
            acc.username = oldName;
            Toast.makeText(requireContext(), "Kaydedilemedi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private List<String> accountNames() {
        List<String> names = new ArrayList<>();
        String[] files = new File(Tools.DIR_ACCOUNT_NEW).list();
        if (files != null) {
            for (String f : files) {
                if (f.endsWith(".json")) names.add(f.substring(0, f.length() - 5));
            }
        }
        return names;
    }

    private void showSwitchDialog() {
        final List<String> names = accountNames();
        if (names.isEmpty()) {
            Toast.makeText(requireContext(), "Kayıtlı hesap yok", Toast.LENGTH_SHORT).show();
            return;
        }
        String current = mAccount == null ? null : mAccount.username;
        int checked = current == null ? -1 : names.indexOf(current);
        new AlertDialog.Builder(requireContext())
                .setTitle("Hesap seç")
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (d, which) -> {
                    d.dismiss();
                    PojavProfile.setCurrentProfile(requireContext(), names.get(which));
                    requireActivity().recreate();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

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
