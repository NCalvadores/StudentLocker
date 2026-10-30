package com.ifelse101.studentlocker.fragments.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

public final class ThemeManager {
    public static final String THEME_MODE_KEY = "theme_mode";
    public static final String THEME_MODE_SYSTEM = "system";
    public static final String THEME_MODE_LIGHT = "light";
    public static final String THEME_MODE_DARK = "dark";

    private ThemeManager() {
        // Utility class
    }

    public static void applyFromPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        applyTheme(prefs.getString(THEME_MODE_KEY, THEME_MODE_SYSTEM));
    }

    public static void applyTheme(@Nullable String selectedTheme) {
        String theme = selectedTheme != null ? selectedTheme : THEME_MODE_SYSTEM;
        int mode = switch (theme) {
            case THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO;
            case THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES;
            case THEME_MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            default -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        };

        AppCompatDelegate.setDefaultNightMode(mode);
    }
}
