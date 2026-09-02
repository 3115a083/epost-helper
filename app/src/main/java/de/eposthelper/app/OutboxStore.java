package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public final class OutboxStore {
    private static final String PREF="outbox";
    private static final String KEY="items";
    private OutboxStore(){}

    public static List<OutboxItem> load(Context c){
        List<OutboxItem> out=new ArrayList<>();
        String raw=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++)out.add(OutboxItem.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        return out;
    }

    public static void save(Context c,List<OutboxItem> items){
        try{
            JSONArray a=new JSONArray();
            for(OutboxItem i:items)a.put(i.toJson());
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public static void add(Context c,Uri uri,String name,boolean deleteAfterSend){
        List<OutboxItem> items=load(c);
        for(OutboxItem i:items)if(i.uri.equals(uri.toString()))return;
        OutboxItem item=new OutboxItem();
        item.uri=uri.toString();item.name=name==null?"PDF":name;item.deleteAfterSend=deleteAfterSend;
        items.add(item);save(c,items);
    }

    public static int importFolder(Context c){
        String folder=SettingsStore.outboxFolder(c);
        if(folder.isBlank())return 0;
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(folder));
        if(root==null||!root.canRead())return 0;
        int count=0;
        for(DocumentFile f:root.listFiles()){
            if(!f.isFile())continue;
            String name=f.getName()==null?"":f.getName();
            String mime=f.getType()==null?"":f.getType();
            if(!name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")&&!"application/pdf".equals(mime))continue;
            int before=load(c).size();
            add(c,f.getUri(),name,true);
            if(load(c).size()>before)count++;
        }
        return count;
    }

    public static void removeSent(Context c,List<OutboxItem> sent){
        List<OutboxItem> all=load(c);
        java.util.HashSet<String> ids=new java.util.HashSet<>();
        for(OutboxItem i:sent){
            ids.add(i.id);
            if(i.deleteAfterSend){
                try{DocumentFile d=DocumentFile.fromSingleUri(c,i.asUri());if(d!=null)d.delete();}
                catch(Exception ignored){}
            }
        }
        all.removeIf(i->ids.contains(i.id));
        save(c,all);
    }
}
