package net.kdt.pojavlaunch.prefs.screens;


import android.app.Activity;
import android.content.SharedPreferences;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setBackgroundResource(R.drawable.s_bg_main);
        super.onViewCreated(view, savedInstanceState);
        // S-Launcher: every setting becomes a rounded gold-outlined card
        RecyclerView list = getListView();
        if(list != null) {
            float d = getResources().getDisplayMetrics().density;
            list.setClipToPadding(false);
            list.setPadding(Math.round(10 * d), Math.round(8 * d), Math.round(10 * d), Math.round(16 * d));
            list.addItemDecoration(new CardDecoration(requireContext()));
        }
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            LauncherActivity launcherActivity = (LauncherActivity)activity;
            mRequestNotificationPermissionPreference.setVisible(!launcherActivity.checkForNotificationPermission());
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                launcherActivity.askForNotificationPermission(()->mRequestNotificationPermissionPreference.setVisible(false));
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }

    /** Draws a rounded card behind each preference row (category headers are left plain) */
    private static class CardDecoration extends RecyclerView.ItemDecoration {
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float mRadius;
        private final int mGap;

        CardDecoration(Context context) {
            float d = context.getResources().getDisplayMetrics().density;
            mFill.setColor(0xFF141414);
            mStroke.setStyle(Paint.Style.STROKE);
            mStroke.setStrokeWidth(1.5f * d);
            mStroke.setColor(0x99FFC400);
            mRadius = 14 * d;
            mGap = Math.round(4 * d);
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            outRect.set(0, mGap, 0, mGap);
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            for(int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                // Only real preference rows have a widget frame; category headers do not
                if(child.findViewById(android.R.id.widget_frame) == null) continue;
                RectF r = new RectF(child.getLeft(), child.getTop() + child.getTranslationY(),
                        child.getRight(), child.getBottom() + child.getTranslationY());
                c.drawRoundRect(r, mRadius, mRadius, mFill);
                c.drawRoundRect(r, mRadius, mRadius, mStroke);
            }
        }
    }
}
