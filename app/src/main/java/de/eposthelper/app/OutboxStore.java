package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OutboxStore {
    private static final String PREF="outbox";
    private static final String KEY="items";
    private static final String SENT_BLOCK="sent_block";
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

    private static Set<String> blocked(Context c){
        return new HashSet<>(c.getSharedPreferences(PREF,Context.MODE_PRIVATE)
                .getStringSet(SENT_BLOCK,java.util.Collections.emptySet()));
    }

    private static void saveBlocked(Context c,Set<String> blocked){
        c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
                .putStringSet(SENT_BLOCK,new HashSet<>(blocked)).apply();
    }

    public static OutboxItem add(Context c,Uri uri,String name,boolean deleteAfterSend){
        return add(c,uri,name,deleteAfterSend,null,"");
    }

    public static OutboxItem add(Context c,Uri uri,String name,boolean deleteAfterSend,JobOptions preset,String source){
        if(uri==null||blocked(c).contains(uri.toString()))return null;
        List<OutboxItem> items=load(c);
        for(OutboxItem i:items)if(i.uri.equals(uri.toString()))return i;

        OutboxItem item=new OutboxItem();
        item.uri=uri.toString();
        item.name=name==null?"PDF":name;
        item.deleteAfterSend=deleteAfterSend;

        if(preset!=null){
            item.hasPreset=true;
            item.presetColor=preset.color;
            item.presetDuplex=preset.duplex;
            item.presetRegistered=preset.registered;
            item.presetC4=preset.c4;
            item.presetShipping=preset.shipping;
            item.presetCorrection=preset.addressCorrection;
            item.presetSource=source==null?"":source;
        }

        items.add(item);
        save(c,items);
        return item;
    }

    public static int importFolder(Context c){
        String folder=SettingsStore.outboxFolder(c);
        if(folder==null||folder.isBlank())return 0;
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(folder));
        if(root==null||!root.canRead())return 0;

        Set<String> blocked=blocked(c);
        Set<String> existing=new HashSet<>();
        int count=0;

        // Root PDFs enter only the input basket.
        for(DocumentFile f:root.listFiles()){
            if(f.isFile()&&isPdf(f)){
                String u=f.getUri().toString();existing.add(u);
                if(blocked.contains(u))continue;
                int before=load(c).size();
                add(c,f.getUri(),safeName(f),true);
                if(load(c).size()>before)count++;
            }
        }

        // One-level option folders. "debug" is intentionally never imported.
        for(DocumentFile dir:root.listFiles()){
            if(!dir.isDirectory())continue;
            String folderName=dir.getName()==null?"":dir.getName();
            if("debug".equalsIgnoreCase(folderName))continue;

            JobOptions preset=ImportPreset.parse(folderName);
            if(preset==null)continue;

            for(DocumentFile f:dir.listFiles()){
                if(!f.isFile()||!isPdf(f))continue;
                String u=f.getUri().toString();existing.add(u);
                if(blocked.contains(u))continue;

                int before=load(c).size();
                OutboxItem item=add(c,f.getUri(),safeName(f),true,preset,folderName);
                if(load(c).size()>before)count++;
                if(item!=null)PreparedLetterStore.addAutoImported(c,item);
            }
        }

        // Keep blocked URIs only while they still exist in the watched tree.
        blocked.removeIf(u->!existing.contains(u));
        saveBlocked(c,blocked);
        return count;
    }

    private static boolean isPdf(DocumentFile f){
        String name=f.getName()==null?"":f.getName().toLowerCase(java.util.Locale.ROOT);
        String mime=f.getType()==null?"":f.getType();
        return name.endsWith(".pdf")||"application/pdf".equals(mime);
    }

    private static String safeName(DocumentFile f){
        return f.getName()==null?"PDF":f.getName();
    }

    public static int removeSent(Context c,List<OutboxItem> sent){
        List<OutboxItem> all=load(c);
        Set<String> ids=new HashSet<>();
        Set<String> blocked=blocked(c);
        int deleteFailures=0;

        for(OutboxItem i:sent){
            ids.add(i.id);
            if(i.deleteAfterSend){
                boolean deleted=false;
                try{
                    DocumentFile d=DocumentFile.fromSingleUri(c,i.asUri());
                    deleted=d!=null&&d.delete();
                }catch(Exception ignored){}
                if(!deleted){
                    blocked.add(i.uri);
                    deleteFailures++;
                }else{
                    blocked.remove(i.uri);
                }
            }
        }

        all.removeIf(i->ids.contains(i.id));
        save(c,all);
        saveBlocked(c,blocked);
        return deleteFailures;
    }
}
