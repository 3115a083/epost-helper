package de.eposthelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;

public final class DebugUtil {
    private DebugUtil(){}

    public static void error(Context context, View anchor, String where, Throwable error){
        String msg=where+": "+(error==null||error.getMessage()==null?"Unbekannter Fehler":error.getMessage());
        error(context,anchor,msg);
    }

    public static void error(Context context, View anchor, String msg){
        if(SettingsStore.debugMode(context)){
            ClipboardManager cm=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("E-POST Helper Debug",msg));
            Snackbar.make(anchor,msg+" · In Zwischenablage kopiert",Snackbar.LENGTH_INDEFINITE)
                    .setAction("Schließen",v->{})
                    .show();
        } else {
            Snackbar.make(anchor,msg,Snackbar.LENGTH_SHORT).show();
        }
    }
}
