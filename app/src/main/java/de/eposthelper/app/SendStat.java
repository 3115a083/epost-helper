package de.eposthelper.app;

import org.json.JSONObject;
import java.util.UUID;

public final class SendStat {
    public String id=UUID.randomUUID().toString();
    public long timestamp=System.currentTimeMillis();
    public String profileId="";
    public String provider="";
    public String registered="Nein";
    public boolean color=false;
    public boolean duplex=false;
    public String shipping="national";
    public double cost=-1d;
    public int pages=0;

    public JSONObject toJson() throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("timestamp",timestamp);o.put("profileId",profileId);o.put("provider",provider);
        o.put("registered",registered);o.put("color",color);o.put("duplex",duplex);o.put("shipping",shipping);
        o.put("cost",cost);o.put("pages",pages);
        return o;
    }

    public static SendStat fromJson(JSONObject o){
        SendStat s=new SendStat();
        s.id=o.optString("id",UUID.randomUUID().toString());
        s.timestamp=o.optLong("timestamp",System.currentTimeMillis());
        s.profileId=o.optString("profileId","");
        s.provider=o.optString("provider","");
        s.registered=o.optString("registered","Nein");
        s.color=o.optBoolean("color",false);s.duplex=o.optBoolean("duplex",false);
        s.shipping=o.optString("shipping","national");s.cost=o.optDouble("cost",-1d);s.pages=o.optInt("pages",0);
        return s;
    }
}
