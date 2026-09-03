package de.eposthelper.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public final class DeviceTransferStore {
    private static final String PREF="portable_backup";
    private static final String KEY="state";
    private static final String RESTORED="restored";
    private DeviceTransferStore(){}

    public static void refresh(Context c){
        try{
            JSONObject root=new JSONObject();
            root.put("settings",SettingsStore.exportJson(c));

            JSONArray profiles=new JSONArray();
            for(Profile p:SecureStore.load(c)){
                if(DebugProfileManager.isDebug(p))continue;
                JSONObject o=p.toJson();
                // API keys never enter Android cloud/device-transfer backup.
                o.put("apiKey","");
                profiles.put(o);
            }
            root.put("profiles",profiles);

            JSONArray stats=new JSONArray();
            for(SendStat s:SendStatsStore.load(c))stats.put(s.toJson());
            root.put("stats",stats);

            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,root.toString()).apply();
        }catch(Exception ignored){}
    }

    public static void restoreIfNeeded(Context c){
        if(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getBoolean(RESTORED,false))return;
        String raw=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"");
        if(raw.isBlank())return;
        try{
            JSONObject root=new JSONObject(raw);
            if(SecureStore.load(c).isEmpty()){
                ArrayList<Profile> profiles=new ArrayList<>();
                JSONArray pa=root.optJSONArray("profiles");
                if(pa!=null)for(int i=0;i<pa.length();i++){
                    JSONObject o=pa.optJSONObject(i);if(o!=null)profiles.add(Profile.fromJson(o));
                }
                SecureStore.save(c,profiles);
            }
            if(SendStatsStore.load(c).isEmpty()){
                ArrayList<SendStat> stats=new ArrayList<>();
                JSONArray sa=root.optJSONArray("stats");
                if(sa!=null)for(int i=0;i<sa.length();i++){
                    JSONObject o=sa.optJSONObject(i);if(o!=null)stats.add(SendStat.fromJson(o));
                }
                SendStatsStore.save(c,stats);
            }
            SettingsStore.importJson(c,root.optJSONObject("settings"));
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putBoolean(RESTORED,true).apply();
        }catch(Exception ignored){}
    }
}
