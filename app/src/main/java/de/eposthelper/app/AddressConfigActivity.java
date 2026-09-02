package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Bitmap;
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
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddressConfigActivity extends AppCompatActivity {
    private Profile profile;
    private AddressConfigView preview;
    private TextView values;
    private Uri pdfUri;
    private float left=-1,top=-1,right=-1,bottom=-1;

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
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?null:SecureStore.find(this,id);
        parseStored();
        render();
    }

    private void parseStored(){
        if(profile==null||profile.recipientWindow==null)return;
        try{
            if(profile.recipientWindow.startsWith("rect:")){
                String[] p=profile.recipientWindow.substring(5).split(",");
                if(p.length==4){
                    left=Float.parseFloat(p[0]); top=Float.parseFloat(p[1]);
                    right=Float.parseFloat(p[2]); bottom=Float.parseFloat(p[3]);
                }
            }
        }catch(Exception ignored){}
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
        back.setOnClickListener(v->finish());
        back.setContentDescription("Zurück");
        topBar.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        topBar.addView(UiKit.heading(this,"Adressbereich festlegen",22),
                new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(topBar);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28));
        scroll.addView(root);

        TextView intro=UiKit.body(this,
                "Lade einen typischen Brief als PDF. Markiere auf der ersten Seite genau den Bereich, in dem die Empfängeradresse steht. "
                +"Diese Vorlage kann später verwendet werden, um abweichende Brieflayouts vor dem Versand gezielt zu korrigieren.");
        intro.setPadding(0,0,0,UiKit.dp(this,10));
        root.addView(intro);

        MaterialButton choose=UiKit.primary(this,"PDF auswählen");
        choose.setOnClickListener(v->picker.launch(new String[]{"application/pdf"}));
        root.addView(choose,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)));

        preview=new AddressConfigView(this);
        preview.setListener((l,t,r,b)->{
            left=l;top=t;right=r;bottom=b;
            updateValues();
        });
        LinearLayout previewBox=new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.addView(UiKit.heading(this,"Erste Seite",17));
        TextView help=UiKit.body(this,"Ziehe mit dem Finger ein Rechteck um die Empfängeradresse.");
        help.setTextSize(13); help.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8));
        previewBox.addView(help);
        previewBox.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,560)));
        root.addView(UiKit.surfaceCard(this,previewBox));

        values=UiKit.mono(this,"Noch kein Bereich ausgewählt.");
        root.addView(UiKit.surfaceCard(this,values));
        updateValues();

        MaterialButton save=UiKit.primary(this,profile==null?"Bereich prüfen":"Bereich im Profil speichern");
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
                        int maxWidth=1400;
                        float factor=Math.min(2.0f,maxWidth/(float)Math.max(page.getWidth(),1));
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
                runOnUiThread(()->DebugUtil.error(this,preview,"PDF-Vorschau fehlgeschlagen",e));
            }
        },"epost-pdf-preview").start();
    }

    private void updateValues(){
        if(values==null)return;
        if(left<0||top<0||right<=left||bottom<=top){
            values.setText("Noch kein Adressbereich ausgewählt.");
            return;
        }
        values.setText(String.format(Locale.GERMANY,
                "Adressbereich relativ zur Seite\nlinks %.1f %% · oben %.1f %%\nrechts %.1f %% · unten %.1f %%",
                left*100f,top*100f,right*100f,bottom*100f));
    }

    private void save(MaterialButton anchor){
        if(left<0||top<0||right<=left||bottom<=top){
            DebugUtil.error(this,anchor,"Bitte zuerst den Adressbereich im PDF markieren.");
            return;
        }
        if(profile==null){
            Snackbar.make(anchor,"Bereich erkannt. Öffne den Assistenten aus einem Profil, um ihn dort zu speichern.",Snackbar.LENGTH_SHORT).show();
            return;
        }
        try{
            profile.recipientWindow=String.format(Locale.US,"rect:%.6f,%.6f,%.6f,%.6f",left,top,right,bottom);
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            boolean found=false;
            for(int i=0;i<list.size();i++){
                if(list.get(i).id.equals(profile.id)){list.set(i,profile);found=true;break;}
            }
            if(!found)list.add(profile);
            SecureStore.save(this,list);
            Snackbar.make(anchor,"Adressbereich im Profil gespeichert.",Snackbar.LENGTH_SHORT).show();
        }catch(Exception e){
            DebugUtil.error(this,anchor,"Adressbereich speichern",e);
        }
    }

    @Override protected void onDestroy(){
        if(preview!=null)preview.clearBitmap();
        super.onDestroy();
    }
}
