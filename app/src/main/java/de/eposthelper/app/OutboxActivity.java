package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class OutboxActivity extends AppCompatActivity {
    private LinearLayout root,queueBox,finalBox;
    private List<OutboxItem> items=new ArrayList<>();
    private File merged;
    private String selectedProfileId;

    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),uris->{
                if(uris==null)return;
                for(Uri uri:uris){
                    try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                    catch(Exception ignored){}
                    OutboxStore.add(this,uri,displayName(uri),false);
                }
                reload();
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        renderShell();
    }

    @Override protected void onResume(){
        super.onResume();
        OutboxStore.importFolder(this);
        reload();
    }

    private String displayName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        String last=uri.getLastPathSegment();
        return last==null?"PDF":last;
    }

    private void renderShell(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(UiKit.resolveSurface(this));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        top.addView(UiKit.heading(this,"Druckausgang",22),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28));
        scroll.addView(root);page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(page);SystemUi.apply(this,page);
        reload();
    }

    private void reload(){
        if(root==null)return;
        items=OutboxStore.load(this);
        root.removeAllViews();

        TextView intro=UiKit.body(this,"PDFs hinzufügen, Reihenfolge festlegen und zu einem Brief zusammenführen. Dateien aus dem konfigurierten Druckausgangsordner werden automatisch übernommen und nach erfolgreichem Versand gelöscht.");
        intro.setPadding(0,0,0,UiKit.dp(this,10));root.addView(intro);

        MaterialButton add=UiKit.primary(this,"PDFs hinzufügen");
        add.setOnClickListener(v->picker.launch(new String[]{"application/pdf"}));
        root.addView(add,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)));

        queueBox=new LinearLayout(this);queueBox.setOrientation(LinearLayout.VERTICAL);
        if(items.isEmpty()){
            TextView empty=UiKit.body(this,"Noch keine PDFs im Druckausgang.");empty.setPadding(0,UiKit.dp(this,16),0,UiKit.dp(this,16));queueBox.addView(empty);
        }else{
            for(int i=0;i<items.size();i++)queueBox.addView(itemCard(i,items.get(i)));
        }
        root.addView(queueBox);

        if(!items.isEmpty()){
            MaterialButton next=UiKit.primary(this,items.size()>1?"PDFs verbinden & Vorschau":"Vorschau & Profile");
            next.setOnClickListener(v->prepareFinal(next));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));lp.setMargins(0,UiKit.dp(this,10),0,0);root.addView(next,lp);
        }

        finalBox=new LinearLayout(this);finalBox.setOrientation(LinearLayout.VERTICAL);root.addView(finalBox);
    }

    private View itemCard(int index,OutboxItem item){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView pos=UiKit.heading(this,String.valueOf(index+1),18);pos.setGravity(Gravity.CENTER);row.addView(pos,new LinearLayout.LayoutParams(UiKit.dp(this,38),UiKit.dp(this,44)));
        LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
        text.addView(UiKit.heading(this,item.name,15));
        int pages=PdfMergeUtil.countPages(this,item.asUri());
        TextView meta=UiKit.body(this,pages+" Seite"+(pages==1?"":"n")+(item.deleteAfterSend?" · Auto-Import":""));meta.setTextSize(12);text.addView(meta);
        row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        MaterialButton up=UiKit.tonal(this,"↑");up.setEnabled(index>0);up.setContentDescription("Nach oben");
        up.setOnClickListener(v->move(index,-1));row.addView(up,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,44)));
        MaterialButton down=UiKit.tonal(this,"↓");down.setEnabled(index<items.size()-1);down.setContentDescription("Nach unten");
        down.setOnClickListener(v->move(index,1));row.addView(down,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,44)));
        box.addView(row);

        MaterialButton remove=UiKit.tonal(this,"Entfernen");remove.setOnClickListener(v->remove(index));
        box.addView(remove,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,44)));
        return UiKit.surfaceCard(this,box);
    }

    private void move(int index,int delta){
        int target=index+delta;if(target<0||target>=items.size())return;
        java.util.Collections.swap(items,index,target);OutboxStore.save(this,items);reload();
    }

    private void remove(int index){
        items.remove(index);OutboxStore.save(this,items);reload();
    }

    private void prepareFinal(MaterialButton button){
        button.setEnabled(false);button.setText("PDF wird vorbereitet…");
        List<OutboxItem> snapshot=new ArrayList<>(items);
        new Thread(()->{
            try{
                File file=PdfMergeUtil.merge(this,snapshot);
                runOnUiThread(()->{
                    if(merged!=null)merged.delete();
                    merged=file;
                    showFinal(snapshot);
                    button.setEnabled(true);button.setText("Vorschau aktualisieren");
                });
            }catch(Exception e){
                runOnUiThread(()->{button.setEnabled(true);button.setText("Erneut versuchen");DebugUtil.error(this,button,"PDFs verbinden",e);});
            }
        },"pdf-merge").start();
    }

    private void showFinal(List<OutboxItem> snapshot){
        finalBox.removeAllViews();
        finalBox.addView(section("Vorschau"));

        LinearLayout previewCard=new LinearLayout(this);previewCard.setOrientation(LinearLayout.VERTICAL);
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setContentDescription("Vorschau der ersten Seite");
        previewCard.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,360)));
        int pages=PdfMergeUtil.countPages(merged);
        TextView meta=UiKit.body(this,snapshot.size()+" PDF"+(snapshot.size()==1?"":"s")+" · "+pages+" Seite"+(pages==1?"":"n"));
        previewCard.addView(meta);
        finalBox.addView(UiKit.surfaceCard(this,previewCard));
        renderPreview(preview,merged);

        finalBox.addView(section("Versandprofil & Kosten"));
        RadioGroup group=new RadioGroup(this);
        List<Profile> profiles=SecureStore.load(this);
        for(Profile p:profiles){
            if(!p.active)continue;
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);
            RadioButton rb=new RadioButton(this);rb.setId(View.generateViewId());rb.setTag(p.id);
            rb.setText((Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?"LetterXpress":"Deutsche Post")+" · "+p.name);
            if(selectedProfileId==null)selectedProfileId=p.id;rb.setChecked(p.id.equals(selectedProfileId));row.addView(rb);
            TextView price=UiKit.body(this,"Kosten werden ermittelt…");price.setTextSize(13);row.addView(price);
            group.addView(row);
            loadPrice(p,pages,price);
        }
        group.setOnCheckedChangeListener((g,id)->{
            View candidate=g.findViewById(id);
            if(candidate!=null)selectedProfileId=String.valueOf(candidate.getTag());
        });
        finalBox.addView(UiKit.surfaceCard(this,group));

        MaterialButton send=UiKit.primary(this,"Jetzt versenden");
        send.setOnClickListener(v->send(snapshot,send));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,58));lp.setMargins(0,UiKit.dp(this,12),0,0);finalBox.addView(send,lp);
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,19);t.setPadding(0,UiKit.dp(this,16),0,UiKit.dp(this,6));return t;
    }

    private void loadPrice(Profile p,int pages,TextView target){
        if(!Profile.PROVIDER_LETTERXPRESS.equals(p.provider)){
            target.setText("Preis wird vom E-POST-Ziel bestimmt");return;
        }
        JobOptions o=JobOptions.fromProfile(p);
        if(Profile.TYPE_LXP_SFTP.equals(p.type)){
            double value=LetterXpressPriceEstimator.gross(o,pages);
            target.setText(value>=0?String.format(java.util.Locale.GERMANY,"ca. %.2f € brutto",value):"Preis nicht verfügbar");
            return;
        }
        new Thread(()->{
            try{
                double value=LetterXpressApiClient.price(p,o,pages);
                runOnUiThread(()->target.setText(value>=0?String.format(java.util.Locale.GERMANY,"voraussichtlich %.2f €",value):"Preis nicht verfügbar"));
            }catch(Exception e){
                runOnUiThread(()->target.setText("Preis nicht verfügbar"));
            }
        },"profile-price").start();
    }

    private void renderPreview(ImageView target,File file){
        new Thread(()->{
            Bitmap bitmap=null;
            try(ParcelFileDescriptor pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(pfd);
                PdfRenderer.Page page=renderer.openPage(0)){
                int width=900;int height=Math.round(width*(page.getHeight()/(float)page.getWidth()));
                bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap result=bitmap;runOnUiThread(()->target.setImageBitmap(result));
            }catch(Exception e){
                if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
            }
        },"outbox-preview").start();
    }

    private void send(List<OutboxItem> snapshot,MaterialButton button){
        if(merged==null||!merged.exists()){DebugUtil.error(this,button,"Vorschau zuerst erstellen.");return;}
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){DebugUtil.error(this,button,"Bitte ein Versandprofil wählen.");return;}
        button.setEnabled(false);button.setText("Wird versendet…");
        File file=merged;
        new Thread(()->{
            try{
                ProviderSender.send(this,Uri.fromFile(file),p,JobOptions.fromProfile(p));
                OutboxStore.removeSent(this,snapshot);
                runOnUiThread(()->{
                    Snackbar.make(button,"Versand erfolgreich übergeben.",Snackbar.LENGTH_LONG).show();
                    if(merged!=null){merged.delete();merged=null;}
                    reload();
                });
            }catch(Exception e){
                runOnUiThread(()->{button.setEnabled(true);button.setText("Erneut versenden");DebugUtil.error(this,button,"Versand",e);});
            }
        },"outbox-send").start();
    }

    @Override protected void onDestroy(){
        if(merged!=null)merged.delete();
        super.onDestroy();
    }
}
