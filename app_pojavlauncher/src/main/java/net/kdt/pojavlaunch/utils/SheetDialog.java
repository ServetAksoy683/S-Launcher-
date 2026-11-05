package net.kdt.pojavlaunch.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import net.kdt.pojavlaunch.R;

/** Instagram-style bottom sheet: slides up from the bottom, rounded top, gold outline */
public final class SheetDialog {
    private SheetDialog() {}

    public static Dialog show(Context context, String title, View content) {
        float d = context.getResources().getDisplayMetrics().density;
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundResource(R.drawable.s_sheet_bg);
        sheet.setPadding(Math.round(16 * d), Math.round(10 * d), Math.round(16 * d), Math.round(18 * d));

        View handle = new View(context);
        handle.setBackgroundResource(R.drawable.s_sheet_handle);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(Math.round(44 * d), Math.round(5 * d));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        sheet.addView(handle, hp);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(context.getResources().getColor(R.color.s_gold));
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setLetterSpacing(0.15f);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = Math.round(12 * d);
        tp.bottomMargin = Math.round(10 * d);
        sheet.addView(titleView, tp);

        ScrollView scroll = new ScrollView(context);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content);
        sheet.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(sheet);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
            int width = Math.min(screenWidth, Math.round(560 * d));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setDimAmount(0.6f);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.windowAnimations = R.style.S_SheetAnimation;
            window.setAttributes(lp);
        }
        dialog.show();
        return dialog;
    }
}
