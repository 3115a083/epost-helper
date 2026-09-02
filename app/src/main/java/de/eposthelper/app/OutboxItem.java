package de.eposthelper.app;

import android.net.Uri;
import org.json.JSONObject;
import java.util.UUID;

public final class OutboxItem {
    public String id=UUID.randomUUID().toString();
    public String uri="";
    public String name="PDF";
    public boolean deleteAfterSend=false;

    public JSONObject toJson() throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("uri",uri);o.put("name",name);o.put("deleteAfterSend",deleteAfterSend);
        return o;
    }

    public static OutboxItem fromJson(JSONObject o){
        OutboxItem i=new OutboxItem();
        i.id=o.optString("id",UUID.randomUUID().toString());
        i.uri=o.optString("uri","");
        i.name=o.optString("name","PDF");
        i.deleteAfterSend=o.optBoolean("deleteAfterSend",false);
        return i;
    }

    public Uri asUri(){return Uri.parse(uri);}
}
