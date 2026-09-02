package de.eposthelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;

public final class DebugUtil {
    private DebugUtil(){}

    public static void error(Context context, View anchor, String where, Throwable error){
        if(error instanceof DiagnosticException){
            DiagnosticException d=(DiagnosticException)error;
            show(context,anchor,d.userMessage(),d.debugDetails());
            return;
        }
        String user=where+": "+(error==null||error.getMessage()==null?"Unbekannter Fehler":error.getMessage());
        show(context,anchor,user,user);
    }

    public static void error(Context context, View anchor, String msg){
        show(context,anchor,msg,msg);
    }

    private static void show(Context context,View anchor,String userMessage,String debugDetails){
        if(SettingsStore.debugMode(context)){
            ClipboardManager cm=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null) cm.setPrimaryClip(ClipData.newPlainText("E-POST Helper Debug",debugDetails));
            Snackbar.make(anchor,debugDetails,Snackbar.LENGTH_INDEFINITE)
                    .setAction("Schließen",v->{})
                    .show();
        }else{
            Snackbar.make(anchor,userMessage,Snackbar.LENGTH_SHORT).show();
        }
    }
}
