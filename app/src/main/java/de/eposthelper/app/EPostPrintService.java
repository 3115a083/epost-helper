package de.eposthelper.app;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class EPostPrintService extends PrintService {
    private final Handler main=new Handler(Looper.getMainLooper());

    @Override protected PrinterDiscoverySession onCreatePrinterDiscoverySession(){
        return new PrinterDiscoverySession(){
            @Override public void onStartPrinterDiscovery(List<PrinterId> priorityList){publish();}
            @Override public void onStopPrinterDiscovery(){}
            @Override public void onValidatePrinters(List<PrinterId> printerIds){publish();}
            @Override public void onStartPrinterStateTracking(PrinterId printerId){publish();}
            @Override public void onStopPrinterStateTracking(PrinterId printerId){}
            @Override public void onDestroy(){}

            private void publish(){
                List<PrinterInfo> infos=new ArrayList<>();
                for(Profile p:SecureStore.load(EPostPrintService.this)){
                    if(!p.active)continue;
                    PrinterId id=generatePrinterId(p.id);
                    PrinterCapabilitiesInfo caps=new PrinterCapabilitiesInfo.Builder(id)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A4,true)
                            .addResolution(new PrintAttributes.Resolution("300","300 dpi",300,300),true)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME|PrintAttributes.COLOR_MODE_COLOR,
                                    p.color?PrintAttributes.COLOR_MODE_COLOR:PrintAttributes.COLOR_MODE_MONOCHROME)
                            .build();
                    String provider=Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?"LetterXpress":"Deutsche Post";
                    String route=Profile.TYPE_LXP_API.equals(p.type)?"API":
                            Profile.TYPE_LXP_SFTP.equals(p.type)?"SFTP":
                            Profile.TYPE_IPP.equals(p.type)?"IPP":"WebDAV";
                    infos.add(new PrinterInfo.Builder(id,provider+" · "+p.name,PrinterInfo.STATUS_IDLE)
                            .setDescription(route+" · Erweiterte Versandoptionen verfügbar")
                            .setCapabilities(caps).build());
                }
                addPrinters(infos);
            }
        };
    }

    @Override protected void onPrintJobQueued(PrintJob printJob){
        Profile profile=SecureStore.find(this,printJob.getInfo().getPrinterId().getLocalId());
        if(profile==null){printJob.fail("Versandprofil nicht gefunden");return;}

        ParcelFileDescriptor pfd=printJob.getDocument().getData();
        if(pfd==null){printJob.fail("Druckdaten fehlen");return;}

        JobOptions options=JobOptions.fromProfile(profile);
        if(printJob.hasAdvancedOption(AdvancedPrintOptionsActivity.OPT_DUPLEX))
            options.duplex=printJob.getAdvancedIntOption(AdvancedPrintOptionsActivity.OPT_DUPLEX)==1;
        if(printJob.hasAdvancedOption(AdvancedPrintOptionsActivity.OPT_COLOR))
            options.color=printJob.getAdvancedIntOption(AdvancedPrintOptionsActivity.OPT_COLOR)==1;
        if(printJob.hasAdvancedOption(AdvancedPrintOptionsActivity.OPT_REGISTERED))
            options.registered=printJob.getAdvancedStringOption(AdvancedPrintOptionsActivity.OPT_REGISTERED);
        if(printJob.hasAdvancedOption(AdvancedPrintOptionsActivity.OPT_C4))
            options.c4=printJob.getAdvancedIntOption(AdvancedPrintOptionsActivity.OPT_C4)==1;

        printJob.start();
        new Thread(()->{
            File tmp=null;
            try{
                tmp=File.createTempFile("epost-print-",".pdf",getCacheDir());
                try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pfd);
                    FileOutputStream out=new FileOutputStream(tmp)){
                    byte[] buf=new byte[64*1024];int n;
                    while((n=in.read(buf))!=-1)out.write(buf,0,n);
                }
                ProviderSender.send(this,Uri.fromFile(tmp),profile,options);
                main.post(printJob::complete);
            }catch(Exception e){
                String msg="Versand fehlgeschlagen: "+(e.getMessage()==null?"Unbekannter Fehler":e.getMessage());
                main.post(()->printJob.fail(msg));
            }finally{
                if(tmp!=null&&!tmp.delete())tmp.deleteOnExit();
            }
        },"letter-print-job").start();
    }

    @Override protected void onRequestCancelPrintJob(PrintJob printJob){printJob.cancel();}
}
