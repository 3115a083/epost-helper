package de.eposthelper.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public final class Profile {
    public static final String PROVIDER_POST="deutsche_post";
    public static final String PROVIDER_LETTERXPRESS="letterxpress";
    public static final String PROVIDER_DEBUG="debug";

    public static final String TYPE_WEBDAV="WEBDAV";
    public static final String TYPE_IPP="IPP";
    public static final String TYPE_LXP_API="LXP_API";
    public static final String TYPE_LXP_SFTP="LXP_SFTP";
    public static final String TYPE_DEBUG="DEBUG";

    public String id=UUID.randomUUID().toString();
    public String name="Standard";
    public String provider=PROVIDER_POST;
    public String type=TYPE_WEBDAV;
    public String url="";
    public String username="";
    public String password="";
    public String apiKey="";
    public String certificatePin="";
    public String sshHostKey="";
    public boolean active=true;
    public boolean duplex=false;
    public boolean color=false;
    public String registeredMail="Nein";
    public boolean addressCorrection=false;
    public String recipientWindow="";
    public String senderWindow="";
    public boolean connectionVerified=false;
    public long connectionVerifiedAt=0L;
    public String lastConnectionMessage="";

    public JSONObject toJson() throws JSONException{
        JSONObject o=new JSONObject();
        o.put("id",id);o.put("name",name);o.put("provider",provider);o.put("type",type);o.put("url",url);
        o.put("username",username);o.put("password",password);o.put("apiKey",apiKey);
        o.put("certificatePin",certificatePin);o.put("sshHostKey",sshHostKey);
        o.put("active",active);o.put("duplex",duplex);o.put("color",color);o.put("registeredMail",registeredMail);
        o.put("addressCorrection",addressCorrection);o.put("recipientWindow",recipientWindow);o.put("senderWindow",senderWindow);
        o.put("connectionVerified",connectionVerified);o.put("connectionVerifiedAt",connectionVerifiedAt);o.put("lastConnectionMessage",lastConnectionMessage);
        return o;
    }

    public static Profile fromJson(JSONObject o){
        Profile p=new Profile();
        p.id=o.optString("id",UUID.randomUUID().toString());
        p.name=o.optString("name","Profil");
        p.provider=o.optString("provider",PROVIDER_POST);
        p.type=o.optString("type",TYPE_WEBDAV);
        p.url=o.optString("url","");
        p.username=o.optString("username","");
        p.password=o.optString("password","");
        p.apiKey=o.optString("apiKey","");
        p.certificatePin=o.optString("certificatePin","");
        p.sshHostKey=o.optString("sshHostKey","");
        p.active=o.optBoolean("active",true);
        p.duplex=o.optBoolean("duplex",false);
        p.color=o.optBoolean("color",false);
        p.registeredMail=o.optString("registeredMail","Nein");
        if("Einwurf".equals(p.registeredMail))p.registeredMail="Einschreiben Einwurf";
        if("Rückschein".equals(p.registeredMail))p.registeredMail="Einschreiben Rückschein";
        p.addressCorrection=o.optBoolean("addressCorrection",false);
        p.recipientWindow=o.optString("recipientWindow","");
        p.senderWindow=o.optString("senderWindow","");
        p.connectionVerified=o.optBoolean("connectionVerified",false);
        p.connectionVerifiedAt=o.optLong("connectionVerifiedAt",0L);
        p.lastConnectionMessage=o.optString("lastConnectionMessage","");
        return p;
    }
}
