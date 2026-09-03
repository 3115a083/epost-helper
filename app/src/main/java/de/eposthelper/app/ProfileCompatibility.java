package de.eposthelper.app;

import android.content.Context;

public final class ProfileCompatibility {
    private ProfileCompatibility(){}

    public static boolean compatible(Profile p,JobOptions o){
        if(p==null||!p.active)return false;
        if(DebugProfileManager.isDebug(p))return true;

        if(Profile.PROVIDER_POST.equals(p.provider)){
            if(o.c4)return false;
            if(p.color!=o.color||p.duplex!=o.duplex)return false;
            String registered=p.registeredMail==null?"Nein":p.registeredMail;
            return registered.equals(o.registered);
        }

        if(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)){
            if("Einschreiben Rückschein".equals(o.registered))return false;
            return true;
        }

        return false;
    }

    public static Profile findFirst(Context c,JobOptions o){
        for(Profile p:SecureStore.load(c))if(compatible(p,o))return p;
        return null;
    }
}
