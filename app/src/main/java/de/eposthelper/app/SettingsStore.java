package de.eposthelper.app;

import android.app.Activity;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import org.json.JSONObject;

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
    public static void applySavedAppearance(Context c){applyAppearance(appearance(c));}
    private static void applyAppearance(String mode){
        int value=AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if("light".equals(mode))value=AppCompatDelegate.MODE_NIGHT_NO;
        else if("dark".equals(mode))value=AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(value);
    }

    public static String palette(Context c){
        return c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(PALETTE,"material_you");
    }
    public static void setPalette(Context c,String palette){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(PALETTE,palette).apply();
    }
    public static boolean materialYou(Context c){return "material_you".equals(palette(c));}
    public static void applyDynamicColors(Activity activity){
        if(materialYou(activity))DynamicColors.applyToActivityIfAvailable(activity);
    }

    public static int primary(Context c){
        if(materialYou(c))return MaterialColors.getColor(c,com.google.android.material.R.attr.colorPrimary,0xFF2457E6);
        switch(palette(c)){
            case "forest":return 0xFF287A61;
            case "sunset":return 0xFFE76F51;
            case "aurora":return 0xFF5B5BD6;
            case "lavender":return 0xFF775DA6;
            case "rose":return 0xFFB5486B;
            case "sand":return 0xFF8A6D3B;
            case "graphite":return 0xFF4D5562;
            default:return 0xFF2457E6;
        }
    }

    public static int secondary(Context c){
        if(materialYou(c))return MaterialColors.getColor(c,com.google.android.material.R.attr.colorSecondary,0xFF2AC7A2);
        switch(palette(c)){
            case "forest":return 0xFF4AB985;
            case "sunset":return 0xFFF2A65A;
            case "aurora":return 0xFF2AC7A2;
            case "lavender":return 0xFFA477D5;
            case "rose":return 0xFFE28BA7;
            case "sand":return 0xFFC7A768;
            case "graphite":return 0xFF707985;
            default:return 0xFF1BC56C;
        }
    }

    public static int[] gradient(Context c){
        return new int[]{primary(c),secondary(c)};
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

    public static JSONObject exportJson(Context c) throws Exception{
        JSONObject o=new JSONObject();
        o.put("appearance",appearance(c));
        o.put("palette",palette(c));
        o.put("debug",debugMode(c));
        o.put("outboxFolder",outboxFolder(c));
        return o;
    }

    public static void importJson(Context c,JSONObject o){
        if(o==null)return;
        String restoredFolder=o.optString("outboxFolder","");
        if(!restoredFolder.isBlank()){
            boolean permitted=false;
            try{
                for(android.content.UriPermission p:c.getContentResolver().getPersistedUriPermissions()){
                    if(p.isReadPermission()&&p.getUri().toString().equals(restoredFolder)){permitted=true;break;}
                }
            }catch(Exception ignored){}
            if(!permitted)restoredFolder="";
        }
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putString(MODE,o.optString("appearance","system"))
                .putString(PALETTE,o.optString("palette","material_you"))
                .putBoolean(DEBUG,o.optBoolean("debug",false))
                .putString(OUTBOX_FOLDER,restoredFolder)
                .apply();
        applySavedAppearance(c);
    }
}
