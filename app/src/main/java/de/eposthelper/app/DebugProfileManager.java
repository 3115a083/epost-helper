package de.eposthelper.app;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public final class DebugProfileManager {
    private static final String NAME="Debug-Ausgabe";
    private DebugProfileManager(){}

    public static void setEnabled(Context c,boolean enabled){
        SettingsStore.setDebugMode(c,enabled);
        try{
            List<Profile> profiles=new ArrayList<>(SecureStore.load(c));
            profiles.removeIf(p->Profile.PROVIDER_DEBUG.equals(p.provider)||Profile.TYPE_DEBUG.equals(p.type));
            if(enabled){
                Profile p=new Profile();
                p.name=NAME;
                p.provider=Profile.PROVIDER_DEBUG;
                p.type=Profile.TYPE_DEBUG;
                p.active=true;
                p.connectionVerified=true;
                p.connectionVerifiedAt=System.currentTimeMillis();
                p.lastConnectionMessage="Lokale Debug-Ausgabe";
                p.duplex=false;
                p.color=false;
                p.registeredMail="Nein";
                profiles.add(p);
            }
            SecureStore.save(c,profiles);
        }catch(Exception ignored){}
    }

    public static void ensure(Context c){
        if(SettingsStore.debugMode(c))setEnabled(c,true);
    }

    public static boolean isDebug(Profile p){
        return p!=null&&(Profile.PROVIDER_DEBUG.equals(p.provider)||Profile.TYPE_DEBUG.equals(p.type));
    }
}
