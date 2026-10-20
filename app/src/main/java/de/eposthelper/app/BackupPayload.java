package de.eposthelper.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Parse and validate the entire payload before touching any live application data. */
final class BackupPayload {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    final JSONObject settings;
    final List<Profile> profiles;
    final List<SendStat> stats;

    private BackupPayload(JSONObject settings, List<Profile> profiles, List<SendStat> stats) {
        this.settings=settings; this.profiles=profiles; this.stats=stats;
    }

    static byte[] readBounded(InputStream in) throws Exception {
        if(in==null)throw new IllegalArgumentException("Backup-Datei konnte nicht geöffnet werden.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[32768]; int n;
        while((n=in.read(buffer))!=-1) {
            if(n>MAX_BYTES-out.size())throw new IllegalArgumentException("Backup ist größer als 8 MiB.");
            out.write(buffer,0,n);
        }
        return out.toByteArray();
    }

    static BackupPayload parse(String text) throws Exception {
        JSONObject root=new JSONObject(text);
        if(!(root.get("format") instanceof Number)||root.getDouble("format")!=1)throw new IllegalArgumentException("Unbekanntes Backup-Format.");
        JSONObject settings=root.getJSONObject("settings");
        String appearance=settings.getString("appearance");
        if(!java.util.Arrays.asList("system","light","dark").contains(appearance))
            throw new IllegalArgumentException("Ungültige Darstellung im Backup.");
        String palette=settings.getString("palette");
        if(!java.util.Arrays.asList("material_you","ocean","forest","sunset","aurora","lavender","rose","sand","graphite").contains(palette))
            throw new IllegalArgumentException("Ungültige Farbpalette im Backup.");
        if(!(settings.get("debug") instanceof Boolean)||!(settings.get("outboxFolder") instanceof String))
            throw new IllegalArgumentException("Ungültige Einstellungen im Backup.");
        JSONArray pa=root.getJSONArray("profiles"), sa=root.getJSONArray("stats");
        if(pa.length()>1000||sa.length()>50000)throw new IllegalArgumentException("Backup enthält zu viele Einträge.");
        List<Profile> profiles=new ArrayList<>();
        HashSet<String> ids=new HashSet<>();
        for(int i=0;i<pa.length();i++) {
            JSONObject item=pa.getJSONObject(i);
            requireString(item,"id"); requireString(item,"name");
            requireString(item,"provider"); requireString(item,"type");
            Profile p=Profile.fromJson(item);
            if(p.id.isBlank()||p.name.isBlank()||!ids.add(p.id))
                throw new IllegalArgumentException("Profil fehlt oder ist doppelt.");
            if(!java.util.Arrays.asList(Profile.PROVIDER_POST,Profile.PROVIDER_LETTERXPRESS).contains(p.provider))
                throw new IllegalArgumentException("Unbekannter Anbieter im Backup.");
            if(!java.util.Arrays.asList(Profile.TYPE_WEBDAV,Profile.TYPE_IPP,Profile.TYPE_LXP_API,Profile.TYPE_LXP_SFTP).contains(p.type))
                throw new IllegalArgumentException("Unbekannter Profiltyp im Backup.");
            // Old backups may omit optional fields, but must never change their JSON types.
            checkTypes(item, p.toJson());
            profiles.add(p);
        }
        List<SendStat> stats=new ArrayList<>(); ids.clear();
        for(int i=0;i<sa.length();i++) {
            JSONObject item=sa.getJSONObject(i);
            requireString(item,"id");
            SendStat stat=SendStat.fromJson(item);
            checkTypes(item,stat.toJson());
            if(stat.id.isBlank()||!ids.add(stat.id)||stat.timestamp<0||stat.pages<0||!Double.isFinite(stat.cost)||stat.cost < -1)
                throw new IllegalArgumentException("Ungültige Versandstatistik.");
            stats.add(stat);
        }
        return new BackupPayload(settings,profiles,stats);
    }

    private static void requireString(JSONObject o,String key) throws Exception {
        if(!(o.get(key) instanceof String))throw new IllegalArgumentException("Ungültiges Feld: "+key);
    }

    private static void checkTypes(JSONObject value,JSONObject expected) throws Exception {
        java.util.Iterator<String> keys=expected.keys();
        while(keys.hasNext()) {
            String key=keys.next();
            if(!value.has(key))continue;
            Object item=value.get(key), model=expected.get(key);
            if(model instanceof String && !(item instanceof String) ||
               model instanceof Boolean && !(item instanceof Boolean) ||
               model instanceof Number && !(item instanceof Number))
                throw new IllegalArgumentException("Ungültiger Datentyp: "+key);
        }
    }
}
