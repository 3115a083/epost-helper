package de.eposthelper.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

/** One process-wide lock and a durable undo journal for the multi-store backup import. */
public final class AppDataTransaction extends Application {
    static final Object LOCK=new Object();
    private static boolean blocked;
    static boolean importing;
    static void check(){if(blocked)throw new IllegalStateException("Datenspeicher gesperrt. Speicher freigeben und App für die Wiederherstellung neu starten.");}
    private static final String[] PREFS={"ui_settings","secure_profiles","send_stats","portable_backup"};

    @Override public void onCreate() {
        super.onCreate();
        synchronized(LOCK) {
            try { recover(this); }
            catch(Exception error) { throw new IllegalStateException("Backup-Wiederherstellung konnte nicht abgeschlossen werden.",error); }
        }
    }

    interface Write { void run() throws Exception; }

    static void replace(Context context, Write write) throws Exception {
        synchronized(LOCK) {
            recover(context);
            AtomicFile journal=journal(context);
            JSONObject previous=new JSONObject();
            for(String name:PREFS) {
                JSONObject values=new JSONObject();
                for(Map.Entry<String,?> item:context.getSharedPreferences(name,Context.MODE_PRIVATE).getAll().entrySet())
                    values.put(item.getKey(),pack(item.getValue()));
                previous.put(name,values);
            }
            FileOutputStream stream=journal.startWrite();
            try { stream.write(previous.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); journal.finishWrite(stream); }
            catch(Exception error) { journal.failWrite(stream); throw error; }
            importing=true;
            try {
                write.run();
                journal.delete();
            } catch(Exception error) {
                try { recover(context); } catch(Exception rollback) { blocked=true; error.addSuppressed(rollback); }
                throw error;
            } finally { importing=false; }
        }
    }

    static void recover(Context context) throws Exception {
        AtomicFile journal=journal(context);
        if(!journal.getBaseFile().exists()&&!new File(journal.getBaseFile()+".bak").exists())return;
        JSONObject previous;
        try(java.io.InputStream in=journal.openRead()) {
            // The journal holds existing local state, rather than a foreign archive.
            byte[] bytes=readJournal(in);
            previous=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        }
        for(String name:PREFS) {
            JSONObject values=previous.getJSONObject(name);
            SharedPreferences.Editor editor=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear();
            java.util.Iterator<String> keys=values.keys();
            while(keys.hasNext()) {
                String key=keys.next(); Object value=values.get(key);
                JSONObject entry=(JSONObject)value;
                String kind=entry.getString("kind");
                switch(kind){
                    case "boolean": editor.putBoolean(key,entry.getBoolean("value"));break;
                    case "int": editor.putInt(key,entry.getInt("value"));break;
                    case "long": editor.putLong(key,entry.getLong("value"));break;
                    case "float": editor.putFloat(key,(float)entry.getDouble("value"));break;
                    case "string": editor.putString(key,entry.getString("value"));break;
                    case "set":
                        org.json.JSONArray items=entry.getJSONArray("value");
                        java.util.Set<String> set=new java.util.HashSet<>();
                        for(int i=0;i<items.length();i++)set.add(items.getString(i));
                        editor.putStringSet(key,set);break;
                    default: throw new IllegalArgumentException("Unbekannter Journaleintrag.");
                }
            }
            if(!editor.commit())throw new java.io.IOException("Alter Datenstand konnte nicht wiederhergestellt werden.");
        }
        journal.delete();
    }

    private static JSONObject pack(Object value) throws Exception {
        JSONObject entry=new JSONObject();
        String kind;
        if(value instanceof Boolean)kind="boolean";
        else if(value instanceof Integer)kind="int";
        else if(value instanceof Long)kind="long";
        else if(value instanceof Float)kind="float";
        else if(value instanceof String)kind="string";
        else if(value instanceof java.util.Set) {
            return entry.put("kind","set").put("value",new org.json.JSONArray((java.util.Set<?>)value));
        } else throw new IllegalArgumentException("Unbekannter Einstellungsdatentyp.");
        return entry.put("kind",kind).put("value",value);
    }

    private static byte[] readJournal(java.io.InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] b=new byte[32768]; int n;
        while((n=in.read(b))!=-1) {
            if(n>32*1024*1024-out.size())throw new java.io.IOException("Wiederherstellungsdatei zu groß.");
            out.write(b,0,n);
        }
        return out.toByteArray();
    }
    private static AtomicFile journal(Context c) {
        return new AtomicFile(new File(c.getNoBackupFilesDir(),"backup-import-undo.json"));
    }
}
