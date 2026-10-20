package de.eposthelper.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import com.google.android.material.snackbar.Snackbar;

public final class DebugUtil {
    private DebugUtil(){}

    public static void error(Context context,View anchor,String where,Throwable error){
        String kind=error==null?"Fehler":error.getClass().getSimpleName();
        if(error instanceof DiagnosticException){
            DiagnosticException d=(DiagnosticException)error;
            show(context,anchor,d.userMessage(),d.debugDetails(),kind);
        }else{
            String user=where+": "+(error==null||error.getMessage()==null?"Unbekannter Fehler":error.getMessage());
            show(context,anchor,user,user,kind);
        }
    }
    public static void error(Context context,View anchor,String message){
        show(context,anchor,message,message,"Diagnose");
    }
    private static void show(Context context,View anchor,String userMessage,String debugDetails,String kind){
        if(anchor==null)return;
        if(SettingsStore.debugMode(context)){
            Snackbar.make(anchor,debugDetails,Snackbar.LENGTH_INDEFINITE)
                .setAction("Diagnose kopieren",view->{
                    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText(
                        "E-POST Helper Diagnose",DiagnosticRedactor.forCopy(debugDetails,kind)));
                    Snackbar.make(anchor,"Bereinigte Diagnose kopiert.",Snackbar.LENGTH_SHORT).show();
                }).show();
        }else Snackbar.make(anchor,userMessage,Snackbar.LENGTH_SHORT).show();
    }
}
