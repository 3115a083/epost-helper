package de.eposthelper.app;

import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PreparedLetter {
    public String id=UUID.randomUUID().toString();
    public String name="Vorbereiteter Brief";
    public long createdAt=System.currentTimeMillis();
    public final List<OutboxItem> sources=new ArrayList<>();
    public boolean mergeAsOne=true;
    public String profileId="";
    public JobOptions options=new JobOptions();
    public boolean addressEdited=false;
    public RectF sourceSender=new RectF();
    public RectF sourceRecipient=new RectF();
    public RectF targetSender=new RectF();
    public RectF targetRecipient=new RectF();

    public JSONObject toJson() throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("name",name);o.put("createdAt",createdAt);
        o.put("mergeAsOne",mergeAsOne);o.put("profileId",profileId);
        JSONObject opts=new JSONObject();
        opts.put("color",options.color);opts.put("duplex",options.duplex);
        opts.put("registered",options.registered);opts.put("c4",options.c4);
        opts.put("shipping",options.shipping);opts.put("addressCorrection",options.addressCorrection);
        o.put("options",opts);
        o.put("addressEdited",addressEdited);
        o.put("sourceSender",AddressCorrectionProcessor.encode(sourceSender));
        o.put("sourceRecipient",AddressCorrectionProcessor.encode(sourceRecipient));
        o.put("targetSender",AddressCorrectionProcessor.encode(targetSender));
        o.put("targetRecipient",AddressCorrectionProcessor.encode(targetRecipient));
        JSONArray src=new JSONArray();
        for(OutboxItem i:sources)src.put(i.toJson());
        o.put("sources",src);
        return o;
    }

    public static PreparedLetter fromJson(JSONObject o){
        PreparedLetter p=new PreparedLetter();
        p.id=o.optString("id",UUID.randomUUID().toString());
        p.name=o.optString("name","Vorbereiteter Brief");
        p.createdAt=o.optLong("createdAt",System.currentTimeMillis());
        p.mergeAsOne=o.optBoolean("mergeAsOne",true);
        p.profileId=o.optString("profileId","");
        JSONObject opts=o.optJSONObject("options");
        if(opts!=null){
            p.options.color=opts.optBoolean("color",false);
            p.options.duplex=opts.optBoolean("duplex",false);
            p.options.registered=opts.optString("registered","Nein");
            p.options.c4=opts.optBoolean("c4",false);
            p.options.shipping=opts.optString("shipping","national");
            p.options.addressCorrection=opts.optBoolean("addressCorrection",false);
        }
        p.addressEdited=o.optBoolean("addressEdited",false);
        p.sourceSender=AddressCorrectionProcessor.decode(o.optString("sourceSender",""));
        p.sourceRecipient=AddressCorrectionProcessor.decode(o.optString("sourceRecipient",""));
        p.targetSender=AddressCorrectionProcessor.decode(o.optString("targetSender",""));
        p.targetRecipient=AddressCorrectionProcessor.decode(o.optString("targetRecipient",""));
        JSONArray src=o.optJSONArray("sources");
        if(src!=null)for(int i=0;i<src.length();i++){
            JSONObject x=src.optJSONObject(i);
            if(x!=null)p.sources.add(OutboxItem.fromJson(x));
        }
        return p;
    }
}
