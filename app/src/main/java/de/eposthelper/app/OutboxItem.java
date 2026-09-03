package de.eposthelper.app;

import android.net.Uri;
import org.json.JSONObject;
import java.util.UUID;

public final class OutboxItem {
    public String id=UUID.randomUUID().toString();
    public String uri="";
    public String name="PDF";
    public boolean deleteAfterSend=false;

    // Optional settings preset inherited from an import subfolder.
    public boolean hasPreset=false;
    public boolean presetColor=false;
    public boolean presetDuplex=false;
    public String presetRegistered="Nein";
    public boolean presetC4=false;
    public String presetShipping="national";
    public boolean presetCorrection=false;
    public String presetSource="";

    public JSONObject toJson() throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("uri",uri);o.put("name",name);o.put("deleteAfterSend",deleteAfterSend);
        o.put("hasPreset",hasPreset);o.put("presetColor",presetColor);o.put("presetDuplex",presetDuplex);
        o.put("presetRegistered",presetRegistered);o.put("presetC4",presetC4);
        o.put("presetShipping",presetShipping);o.put("presetCorrection",presetCorrection);
        o.put("presetSource",presetSource);
        return o;
    }

    public static OutboxItem fromJson(JSONObject o){
        OutboxItem i=new OutboxItem();
        i.id=o.optString("id",UUID.randomUUID().toString());
        i.uri=o.optString("uri","");
        i.name=o.optString("name","PDF");
        i.deleteAfterSend=o.optBoolean("deleteAfterSend",false);
        i.hasPreset=o.optBoolean("hasPreset",false);
        i.presetColor=o.optBoolean("presetColor",false);
        i.presetDuplex=o.optBoolean("presetDuplex",false);
        i.presetRegistered=o.optString("presetRegistered","Nein");
        i.presetC4=o.optBoolean("presetC4",false);
        i.presetShipping=o.optString("presetShipping","national");
        i.presetCorrection=o.optBoolean("presetCorrection",false);
        i.presetSource=o.optString("presetSource","");
        return i;
    }

    public Uri asUri(){return Uri.parse(uri);}

    public JobOptions presetOptions(){
        JobOptions o=new JobOptions();
        o.color=presetColor;
        o.duplex=presetDuplex;
        o.registered=presetRegistered;
        o.c4=presetC4;
        o.shipping=presetShipping;
        o.addressCorrection=presetCorrection;
        return o;
    }
}
