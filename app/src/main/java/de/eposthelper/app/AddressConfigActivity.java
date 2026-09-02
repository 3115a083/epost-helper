package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddressConfigActivity extends AppCompatActivity {
    private Profile profile;
    private AddressConfigView preview;
    private TextView values;
    private Uri pdfUri;

    private RectF senderBox=new RectF(0.105f,0.055f,0.535f,0.105f);
    private RectF recipientBox=new RectF(0.105f,0.115f,0.535f,0.235f);

    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),uri->{
                if(uri==null)return;
                try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                catch(SecurityException ignored){}
                pdfUri=uri;
                renderFirstPage(uri);
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?null:SecureStore.find(this,id);
        parseStored();
        render();
    }

    private RectF parseRect(String value,RectF fallback){
        try{
            if(value!=null&&value.startsWith("rect:")){
                String[] p=value.substring(5).split(",");
                if(p.length==4)return new RectF(
                        Float.parseFloat(p[0]),Float.parseFloat(p[1]),
                        Float.parseFloat(p[2]),Float.parseFloat(p[3]));
            }
        }catch(Exception ignored){}
        return new RectF(fallback);
    }

    private void parseStored(){
        if(profile==null)return;
        senderBox=parseRect(profile.senderWindow,senderBox);
        recipientBox=parseRect(profile.recipientWindow,recipientBox);
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout topBar=new LinearLayout(this);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        back.setContentDescription("Zurück");
        topBar.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        topBar.addView(UiKit.heading(this,"Adressbereiche festlegen",22),
                new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(topBar);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28));
        scroll.addView(root);

        TextView intro=UiKit.body(this,
                "Lade einen typischen Brief als PDF. Im oberen Drittel liegen bereits Felder für Absender und Empfänger. "
                +"Ziehe beide Felder auf die tatsächlichen Bereiche deines Brieflayouts.");
        intro.setPadding(0,0,0,UiKit.dp(this,10));
        root.addView(intro);

        MaterialButton choose=UiKit.primary(this,"PDF auswählen");
        choose.setOnClickListener(v->picker.launch(new String[]{"application/pdf"}));
        root.addView(choose,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)));

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        MaterialSwitch snap=new MaterialSwitch(this);
        snap.setText("An typischer Adresslinie einrasten");
        snap.setChecked(true);
        controls.addView(snap);
        TextView snapHelp=UiKit.body(this,
                "Mit Einrasten bleiben Absender und Empfänger auf derselben typischen vertikalen Linie. "
                +"Du kannst sie unabhängig nach oben und unten verschieben, damit zwischen beiden Bereichen Platz für die Frankierzone bleibt. "
                +"Schalte Einrasten aus, um frei zu verschieben und die Größe über die Ecke unten rechts anzupassen.");
        snapHelp.setTextSize(13);
        controls.addView(snapHelp);
        root.addView(UiKit.surfaceCard(this,controls));

        preview=new AddressConfigView(this);
        preview.setBoxes(senderBox,recipientBox);
        preview.setSnapEnabled(true);
        preview.setListener((sender,recipient)->{
            senderBox=new RectF(sender);
            recipientBox=new RectF(recipient);
            updateValues();
        });
        snap.setOnCheckedChangeListener((button,checked)->preview.setSnapEnabled(checked));

        LinearLayout previewBox=new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.addView(UiKit.heading(this,"Kopfbereich des Briefs",17));
        TextView help=UiKit.body(this,"Nur das obere Drittel wird angezeigt. Ziehen verändert die Felder, nicht den Brief.");
        help.setTextSize(13);
        help.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8));
        previewBox.addView(help);
        previewBox.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,370)));
        root.addView(UiKit.surfaceCard(this,previewBox));

        values=UiKit.mono(this,"");
        updateValues();
        root.addView(UiKit.surfaceCard(this,values));

        MaterialButton save=UiKit.primary(this,profile==null?"Bereiche prüfen":"Bereiche im Profil speichern");
        save.setOnClickListener(v->save(save));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));
        slp.setMargins(0,UiKit.dp(this,10),0,0);
        root.addView(save,slp);

        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(page);
        SystemUi.apply(this,page);
    }

    private void renderFirstPage(Uri uri){
        new Thread(()->{
            Bitmap bitmap=null;
            try(ParcelFileDescriptor pfd=getContentResolver().openFileDescriptor(uri,"r")){
                if(pfd==null)throw new IllegalStateException("PDF kann nicht geöffnet werden.");
                try(PdfRenderer renderer=new PdfRenderer(pfd)){
                    if(renderer.getPageCount()<1)throw new IllegalStateException("PDF enthält keine Seiten.");
                    try(PdfRenderer.Page page=renderer.openPage(0)){
                        int maxWidth=1200;
                        float factor=Math.min(1.7f,maxWidth/(float)Math.max(page.getWidth(),1));
                        int w=Math.max(1,Math.round(page.getWidth()*factor));
                        int h=Math.max(1,Math.round(page.getHeight()*factor));
                        bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                }
                Bitmap result=bitmap;
                runOnUiThread(()->preview.setBitmap(result));
            }catch(Exception e){
                if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
                runOnUiThread(()->DebugUtil.error(this,preview,"PDF-Vorschau",e));
            }
        },"epost-pdf-preview").start();
    }

    private void updateValues(){
        if(values==null)return;
        values.setText(String.format(Locale.GERMANY,
                "Absender   x %.1f–%.1f %% · y %.1f–%.1f %%\nEmpfänger x %.1f–%.1f %% · y %.1f–%.1f %%",
                senderBox.left*100,senderBox.right*100,senderBox.top*100,senderBox.bottom*100,
                recipientBox.left*100,recipientBox.right*100,recipientBox.top*100,recipientBox.bottom*100));
    }

    private static String encode(RectF r){
        return String.format(Locale.US,"rect:%.6f,%.6f,%.6f,%.6f",r.left,r.top,r.right,r.bottom);
    }

    private void save(MaterialButton anchor){
        if(profile==null){
            Snackbar.make(anchor,"Bereiche festgelegt. Öffne den Assistenten aus einem Profil, um sie zu speichern.",Snackbar.LENGTH_SHORT).show();
            return;
        }
        try{
            profile.senderWindow=encode(senderBox);
            profile.recipientWindow=encode(recipientBox);
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            boolean found=false;
            for(int i=0;i<list.size();i++){
                if(list.get(i).id.equals(profile.id)){list.set(i,profile);found=true;break;}
            }
            if(!found)list.add(profile);
            SecureStore.save(this,list);
            Snackbar.make(anchor,"Adressbereiche gespeichert.",Snackbar.LENGTH_SHORT).show();
        }catch(Exception e){
            DebugUtil.error(this,anchor,"Adressbereiche speichern",e);
        }
    }

    @Override protected void onDestroy(){
        if(preview!=null)preview.clearBitmap();
        super.onDestroy();
    }
}
