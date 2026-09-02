package de.eposthelper.app;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public final class SettingsStore {
    private static final String PREF="ui_settings";
    private static final String MODE="appearance";
    private static final String PALETTE="palette";
    private static final String DEBUG="debug";
    private static final String OUTBOX_FOLDER="outbox_folder";
    private SettingsStore(){}

    public static String appearance(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(MODE,"system");
    }
    public static void setAppearance(Context c,String mode){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(MODE,mode).apply();
        applyAppearance(mode);
    }
    public static void applySavedAppearance(Context c){ applyAppearance(appearance(c)); }
    private static void applyAppearance(String mode){
        int value=AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if("light".equals(mode)) value=AppCompatDelegate.MODE_NIGHT_NO;
        else if("dark".equals(mode)) value=AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(value);
    }

    public static String palette(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(PALETTE,"ocean");
    }
    public static void setPalette(Context c,String palette){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(PALETTE,palette).apply();
    }

    public static int primary(Context c){
        switch(palette(c)){
            case "forest": return 0xFF287A61;
            case "sunset": return 0xFFE76F51;
            case "aurora": return 0xFF5B5BD6;
            case "lavender": return 0xFF775DA6;
            case "graphite": return 0xFF4D5562;
            default: return 0xFF2457E6;
        }
    }
    public static boolean debugMode(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(DEBUG,false);
    }
    public static void setDebugMode(Context c,boolean enabled){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(DEBUG,enabled).apply();
    }

    public static String outboxFolder(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(OUTBOX_FOLDER,"");
    }
    public static void setOutboxFolder(Context c,String uri){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(OUTBOX_FOLDER,uri==null?"":uri).apply();
    }

    public static int[] gradient(Context c){
        switch(palette(c)){
            case "forest": return new int[]{0xFF216B57,0xFF4AB985};
            case "sunset": return new int[]{0xFFE05D44,0xFFF2A65A};
            case "aurora": return new int[]{0xFF4C56C9,0xFF2AC7A2};
            case "lavender": return new int[]{0xFF6950A1,0xFFA477D5};
            case "graphite": return new int[]{0xFF3F4650,0xFF707985};
            default: return new int[]{0xFF1769C2,0xFF1BC56C};
        }
    }
}
