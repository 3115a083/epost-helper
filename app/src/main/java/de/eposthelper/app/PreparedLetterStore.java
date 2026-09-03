package de.eposthelper.app;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public final class PreparedLetterStore {
    private static final String PREF="prepared_letters";
    private static final String KEY="items";
    private PreparedLetterStore(){}

    public static List<PreparedLetter> load(Context c){
        ArrayList<PreparedLetter> out=new ArrayList<>();
        String raw=c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]");
        try{
            JSONArray a=new JSONArray(raw);
            for(int i=0;i<a.length();i++)out.add(PreparedLetter.fromJson(a.getJSONObject(i)));
        }catch(Exception ignored){}
        return out;
    }

    public static void save(Context c,List<PreparedLetter> items){
        try{
            JSONArray a=new JSONArray();
            for(PreparedLetter p:items)a.put(p.toJson());
            c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,a.toString()).apply();
        }catch(Exception ignored){}
    }

    public static void upsert(Context c,PreparedLetter letter){
        List<PreparedLetter> all=load(c);
        boolean found=false;
        for(int i=0;i<all.size();i++){
            if(all.get(i).id.equals(letter.id)){all.set(i,letter);found=true;break;}
        }
        if(!found)all.add(letter);
        save(c,all);
    }

    public static PreparedLetter find(Context c,String id){
        if(id==null)return null;
        for(PreparedLetter p:load(c))if(id.equals(p.id))return p;
        return null;
    }

    public static void remove(Context c,String id){
        List<PreparedLetter> all=load(c);
        all.removeIf(p->p.id.equals(id));
        save(c,all);
    }

    public static boolean hasSourceUri(Context c,String uri){
        for(PreparedLetter p:load(c))
            for(OutboxItem i:p.sources)
                if(i.uri.equals(uri))return true;
        return false;
    }

    public static void addAutoImported(Context c,OutboxItem item){
        if(item==null||!item.hasPreset||hasSourceUri(c,item.uri))return;
        PreparedLetter p=new PreparedLetter();
        p.name=item.name;
        p.sources.add(OutboxItem.fromJson(json(item)));
        p.mergeAsOne=true;
        p.options=item.presetOptions();
        p.addressEdited=false;
        upsert(c,p);
    }

    private static org.json.JSONObject json(OutboxItem i){
        try{return i.toJson();}catch(Exception e){return new org.json.JSONObject();}
    }
}
