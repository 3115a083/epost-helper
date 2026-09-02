package de.eposthelper.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public final class Profile {
    public static final String TYPE_WEBDAV = "WEBDAV";
    public static final String TYPE_IPP = "IPP";

    public String id = UUID.randomUUID().toString();
    public String name = "Standard";
    public String type = TYPE_WEBDAV;
    public String url = "";
    public String username = "";
    public String password = "";
    public String certificatePin = "";
    public boolean active = true;
    public boolean duplex = false;
    public boolean color = false;
    public String registeredMail = "Nein";
    public String recipientWindow = "";
    public String senderWindow = "";

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("name", name); o.put("type", type); o.put("url", url);
        o.put("username", username); o.put("password", password); o.put("certificatePin", certificatePin);
        o.put("active", active); o.put("duplex", duplex); o.put("color", color);
        o.put("registeredMail", registeredMail); o.put("recipientWindow", recipientWindow); o.put("senderWindow", senderWindow);
        return o;
    }

    public static Profile fromJson(JSONObject o) {
        Profile p = new Profile();
        p.id = o.optString("id", UUID.randomUUID().toString());
        p.name = o.optString("name", "Profil");
        p.type = o.optString("type", TYPE_WEBDAV);
        p.url = o.optString("url", "");
        p.username = o.optString("username", "");
        p.password = o.optString("password", "");
        p.certificatePin = o.optString("certificatePin", "");
        p.active = o.optBoolean("active", true);
        p.duplex = o.optBoolean("duplex", false);
        p.color = o.optBoolean("color", false);
        p.registeredMail = o.optString("registeredMail", "Nein");
        if ("Einwurf".equals(p.registeredMail)) p.registeredMail = "Einschreiben Einwurf";
        if ("Rückschein".equals(p.registeredMail)) p.registeredMail = "Einschreiben Rückschein";
        p.recipientWindow = o.optString("recipientWindow", "");
        p.senderWindow = o.optString("senderWindow", "");
        return p;
    }
}
