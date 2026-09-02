package de.eposthelper.app;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DebugSender {
    private DebugSender(){}

    public static void send(Context c,Uri pdf,JobOptions o,String extra) throws Exception{
        String rootUri=SettingsStore.outboxFolder(c);
        if(rootUri==null||rootUri.isBlank())
            throw new IllegalStateException("Für Debug-Ausgaben muss zuerst ein Standard-/Importordner gewählt werden.");

        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(rootUri));
        if(root==null||!root.canWrite())
            throw new IllegalStateException("Der eingestellte Standardordner ist nicht beschreibbar.");

        DocumentFile debug=findOrCreateDir(root,"debug");
        if(debug==null||!debug.canWrite())
            throw new IllegalStateException("Debug-Unterordner konnte nicht erstellt werden.");

        String stamp=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date());
        String base="testdruck-"+stamp;

        DocumentFile pdfOut=debug.createFile("application/pdf",base+".pdf");
        if(pdfOut==null)throw new IllegalStateException("Debug-PDF konnte nicht erstellt werden.");
        try(InputStream in=c.getContentResolver().openInputStream(pdf);
            OutputStream out=c.getContentResolver().openOutputStream(pdfOut.getUri(),"w")){
            if(in==null||out==null)throw new IllegalStateException("Debug-PDF konnte nicht geschrieben werden.");
            byte[] buf=new byte[64*1024];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }

        StringBuilder info=new StringBuilder();
        info.append("timestamp=").append(stamp).append('\n');
        info.append("color=").append(o.color?"color":"monochrome").append('\n');
        info.append("duplex=").append(o.duplex).append('\n');
        info.append("registered=").append(o.registered).append('\n');
        info.append("c4=").append(o.c4).append('\n');
        info.append("shipping=").append(o.shipping).append('\n');
        info.append("pageCount=").append(PdfMergeUtil.countPages(c,pdf)).append('\n');
        info.append("addressCorrection=").append(o.addressCorrection).append('\n');
        if(extra!=null&&!extra.isBlank()){
            info.append('\n').append(extra.trim()).append('\n');
        }

        DocumentFile txtOut=debug.createFile("text/plain",base+".txt");
        if(txtOut==null)throw new IllegalStateException("Debug-Textdatei konnte nicht erstellt werden.");
        try(OutputStream out=c.getContentResolver().openOutputStream(txtOut.getUri(),"w")){
            if(out==null)throw new IllegalStateException("Debug-Textdatei konnte nicht geschrieben werden.");
            out.write(info.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static DocumentFile findOrCreateDir(DocumentFile root,String name){
        for(DocumentFile f:root.listFiles()){
            if(f.isDirectory()&&name.equalsIgnoreCase(f.getName()))return f;
        }
        return root.createDirectory(name);
    }
}
