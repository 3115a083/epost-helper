package de.eposthelper.app;

import android.graphics.RectF;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PreparedJob {
    public String id=UUID.randomUUID().toString();
    public String name="Brief";
    public String filePath="";
    public String profileId="";
    public boolean color=false;
    public boolean duplex=false;
    public String registered="Nein";
    public boolean c4=false;
    public String shipping="national";
    public boolean addressCorrection=false;
    public RectF sourceSender=new RectF();
    public RectF sourceRecipient=new RectF();
    public RectF targetSender=new RectF();
    public RectF targetRecipient=new RectF();
    public String recipientKey="";
    public long createdAt=System.currentTimeMillis();
    public String sourceUri="";
    public boolean deleteSourceAfterSend=false;
    public final List<String> inputNames=new ArrayList<>();

    public JobOptions options(){
        JobOptions o=new JobOptions();
        o.color=color;o.duplex=duplex;o.registered=registered;o.c4=c4;o.shipping=shipping;o.addressCorrection=addressCorrection;
        return o;
    }

    public JSONObject toJson() throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("name",name);o.put("filePath",filePath);o.put("profileId",profileId);
        o.put("color",color);o.put("duplex",duplex);o.put("registered",registered);o.put("c4",c4);o.put("shipping",shipping);
        o.put("addressCorrection",addressCorrection);
        o.put("sourceSender",AddressCorrectionProcessor.encode(sourceSender));
        o.put("sourceRecipient",AddressCorrectionProcessor.encode(sourceRecipient));
        o.put("targetSender",AddressCorrectionProcessor.encode(targetSender));
        o.put("targetRecipient",AddressCorrectionProcessor.encode(targetRecipient));
        o.put("recipientKey",recipientKey);o.put("createdAt",createdAt);o.put("sourceUri",sourceUri);o.put("deleteSourceAfterSend",deleteSourceAfterSend);
        JSONArray a=new JSONArray();for(String n:inputNames)a.put(n);o.put("inputNames",a);
        return o;
    }

    public static PreparedJob fromJson(JSONObject o){
        PreparedJob j=new PreparedJob();
        j.id=o.optString("id",UUID.randomUUID().toString());j.name=o.optString("name","Brief");j.filePath=o.optString("filePath","");
        j.profileId=o.optString("profileId","");j.color=o.optBoolean("color",false);j.duplex=o.optBoolean("duplex",false);
        j.registered=o.optString("registered","Nein");j.c4=o.optBoolean("c4",false);j.shipping=o.optString("shipping","national");
        j.addressCorrection=o.optBoolean("addressCorrection",false);
        j.sourceSender=AddressCorrectionProcessor.decode(o.optString("sourceSender",""));
        j.sourceRecipient=AddressCorrectionProcessor.decode(o.optString("sourceRecipient",""));
        j.targetSender=AddressCorrectionProcessor.decode(o.optString("targetSender",""));
        j.targetRecipient=AddressCorrectionProcessor.decode(o.optString("targetRecipient",""));
        j.recipientKey=o.optString("recipientKey","");j.createdAt=o.optLong("createdAt",System.currentTimeMillis());j.sourceUri=o.optString("sourceUri","");j.deleteSourceAfterSend=o.optBoolean("deleteSourceAfterSend",false);
        JSONArray a=o.optJSONArray("inputNames");if(a!=null)for(int i=0;i<a.length();i++)j.inputNames.add(a.optString(i));
        return j;
    }
}
