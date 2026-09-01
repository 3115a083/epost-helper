package de.eposthelper.app;

import android.net.Uri;
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
import java.util.concurrent.Executors;

public class EPostPrintService extends PrintService {
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
                    if(!p.active) continue;
                    PrinterId id=generatePrinterId(p.id);
                    PrinterCapabilitiesInfo caps=new PrinterCapabilitiesInfo.Builder(id)
                            .addMediaSize(PrintAttributes.MediaSize.ISO_A4,true)
                            .addResolution(new PrintAttributes.Resolution("300","300 dpi",300,300),true)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME|PrintAttributes.COLOR_MODE_COLOR,
                                    p.color?PrintAttributes.COLOR_MODE_COLOR:PrintAttributes.COLOR_MODE_MONOCHROME)
                            .build();
                    infos.add(new PrinterInfo.Builder(id,"E-POST · "+p.name,PrinterInfo.STATUS_IDLE)
                            .setDescription(Profile.TYPE_IPP.equals(p.type)?"Sicherer IPP-Versand":"Sicherer Sammelkorb-Versand")
                            .setCapabilities(caps).build());
                }
                addPrinters(infos);
            }
        };
    }

    @Override protected void onPrintJobQueued(PrintJob printJob){
        Profile profile=SecureStore.find(this,printJob.getInfo().getPrinterId().getLocalId());
        if(profile==null){printJob.fail("Versandprofil nicht gefunden");return;}
        printJob.start();
        Executors.newSingleThreadExecutor().execute(()->{
            File tmp=null;
            try{
                ParcelFileDescriptor pfd=printJob.getDocument().getData();
                if(pfd==null) throw new IllegalStateException("Druckdaten fehlen");
                tmp=File.createTempFile("epost-print-",".pdf",getCacheDir());
                try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(pfd); FileOutputStream out=new FileOutputStream(tmp)){
                    byte[] buf=new byte[64*1024]; int n; while((n=in.read(buf))>=0) out.write(buf,0,n);
                }
                Sender.send(this,Uri.fromFile(tmp),profile); printJob.complete();
            }catch(Exception e){
                printJob.fail("E-POST-Versand fehlgeschlagen: "+e.getMessage());
            }finally{if(tmp!=null) tmp.delete();}
        });
    }

    @Override protected void onRequestCancelPrintJob(PrintJob printJob){printJob.cancel();}
}
