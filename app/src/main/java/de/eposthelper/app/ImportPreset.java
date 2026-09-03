package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ImportPreset {
    private ImportPreset(){}

    public static JobOptions parse(String folderName){
        if(folderName==null)return null;
        String n=folderName.toLowerCase(Locale.ROOT)
                .replace('ä','a').replace('ö','o').replace('ü','u').replace('ß','s');

        boolean recognized=n.contains("sw")||n.contains("schwarz")||n.contains("farbe")||
                n.contains("einseit")||n.contains("duplex")||n.contains("doppelseit")||
                n.contains("national")||n.contains("international")||n.contains("korrektur")||
                n.contains("einschreiben")||n.contains("einwurf")||n.contains("ruckschein")||
                n.contains("rueckschein")||n.contains("c4");
        if(!recognized)return null;

        JobOptions o=new JobOptions();
        o.color=n.contains("farbe");
        o.duplex=n.contains("duplex")||n.contains("doppelseit");
        o.shipping=n.contains("international")?"international":"national";
        o.addressCorrection=n.contains("korrektur");
        o.c4=n.contains("c4");

        if(n.contains("ruckschein")||n.contains("rueckschein"))o.registered="Einschreiben Rückschein";
        else if(n.contains("einwurf"))o.registered="Einschreiben Einwurf";
        else if(n.contains("einschreiben"))o.registered="Einschreiben";
        else o.registered="Nein";
        return o;
    }

    public static int createBaseFolders(Context c) throws Exception{
        DocumentFile root=root(c);
        int created=0;
        String[] colors={"SW","Farbe"};
        String[] sides={"einseitig","duplex"};
        String[] shipping={"national","international"};
        String[] correction={"","_korrektur"};
        for(String color:colors)for(String side:sides)for(String ship:shipping)for(String corr:correction){
            created+=ensure(root,color+"_"+side+"_"+ship+corr)?1:0;
        }
        return created;
    }

    public static int createRegisteredFolders(Context c) throws Exception{
        DocumentFile root=root(c);
        int created=0;
        String[] colors={"SW","Farbe"};
        String[] sides={"einseitig","duplex"};
        String[] registered={"einschreiben","einwurf"};
        String[] correction={"","_korrektur"};
        for(String color:colors)for(String side:sides)for(String reg:registered)for(String corr:correction){
            created+=ensure(root,color+"_"+side+"_national_"+reg+corr)?1:0;
        }
        return created;
    }

    private static DocumentFile root(Context c){
        String uri=SettingsStore.outboxFolder(c);
        if(uri==null||uri.isBlank())throw new IllegalStateException("Bitte zuerst einen Standardordner wählen.");
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(uri));
        if(root==null||!root.canWrite())throw new IllegalStateException("Der Standardordner ist nicht beschreibbar.");
        return root;
    }

    private static boolean ensure(DocumentFile root,String name){
        for(DocumentFile f:root.listFiles()){
            if(f.isDirectory()&&name.equalsIgnoreCase(f.getName()))return false;
        }
        return root.createDirectory(name)!=null;
    }
}
