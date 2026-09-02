package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;

public class AddressEditActivity extends AppCompatActivity {
    public static final String EXTRA_FILE="file";
    public static final String EXTRA_PROFILE="profile";
    public static final String EXTRA_REGISTERED="registered";
    public static final String EXTRA_SOURCE_SENDER="sourceSender";
    public static final String EXTRA_SOURCE_RECIPIENT="sourceRecipient";
    public static final String EXTRA_TARGET_SENDER="targetSender";
    public static final String EXTRA_TARGET_RECIPIENT="targetRecipient";

    private AddressConfigView preview;
    private Profile profile;
    private JobOptions options=new JobOptions();

    private RectF sourceSender=new RectF();
    private RectF sourceRecipient=new RectF();
    private RectF targetSender=new RectF();
    private RectF targetRecipient=new RectF();

    private boolean sourceMode=true;
    private MaterialSwitch snap;
    private TextView hint;
    private HorizontalScrollView horizontal;
    private Bitmap bitmap;
    private float zoom=1.8f;

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);

        String profileId=getIntent().getStringExtra(EXTRA_PROFILE);
        profile=SecureStore.find(this,profileId);
        options.registered=getIntent().getStringExtra(EXTRA_REGISTERED);
        if(options.registered==null)options.registered="Nein";

        sourceSender=rectExtra(EXTRA_SOURCE_SENDER,profile==null?new RectF():AddressCorrectionProcessor.decode(profile.senderWindow));
        sourceRecipient=rectExtra(EXTRA_SOURCE_RECIPIENT,profile==null?new RectF():AddressCorrectionProcessor.decode(profile.recipientWindow));
        if(sourceSender.isEmpty())sourceSender=AddressLayoutRules.normalSender();
        if(sourceRecipient.isEmpty())sourceRecipient=AddressLayoutRules.normalRecipient();

        targetSender=rectExtra(EXTRA_TARGET_SENDER,profile==null?AddressLayoutRules.normalSender():AddressLayoutRules.targetSender(profile,options));
        targetRecipient=rectExtra(EXTRA_TARGET_RECIPIENT,profile==null?AddressLayoutRules.normalRecipient():AddressLayoutRules.targetRecipient(profile,options));

        render();
        loadPdf();
    }

    private RectF rectExtra(String key,RectF fallback){
        RectF r=AddressCorrectionProcessor.decode(getIntent().getStringExtra(key));
        return r.isEmpty()?new RectF(fallback):r;
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout bar=new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        bar.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.heading(this,"Adresslayout bearbeiten",22));
        TextView sub=UiKit.body(this,"Große Vorschau des Briefkopfs");sub.setTextSize(12);titles.addView(sub);
        bar.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(bar);

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(UiKit.dp(this,16),0,UiKit.dp(this,16),UiKit.dp(this,8));

        MaterialButtonToggleGroup modes=new MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true);modes.setSelectionRequired(true);
        MaterialButton source=UiKit.tonal(this,"1 · Originalbereiche");
        source.setId(View.generateViewId());
        MaterialButton target=UiKit.tonal(this,"2 · Zielposition");
        target.setId(View.generateViewId());
        modes.addView(source,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
        modes.addView(target,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
        modes.check(source.getId());
        modes.addOnButtonCheckedListener((group,id,checked)->{
            if(!checked)return;
            saveCurrentBoxes();
            sourceMode=id==source.getId();
            applyMode();
        });
        controls.addView(modes);

        hint=UiKit.body(this,"");
        hint.setTextSize(13);
        hint.setPadding(0,UiKit.dp(this,7),0,UiKit.dp(this,6));
        controls.addView(hint);

        LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);
        snap=new MaterialSwitch(this);snap.setText("Einrasten");
        snap.setChecked(false);
        snap.setOnCheckedChangeListener((b,checked)->preview.setSnapEnabled(checked));
        tools.addView(snap,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        MaterialButton minus=UiKit.tonal(this,"−");
        minus.setContentDescription("Vorschau verkleinern");
        minus.setOnClickListener(v->{zoom=Math.max(1.0f,zoom-0.3f);updateZoom();});
        tools.addView(minus,new LinearLayout.LayoutParams(UiKit.dp(this,52),UiKit.dp(this,44)));
        MaterialButton plus=UiKit.tonal(this,"+");
        plus.setContentDescription("Vorschau vergrößern");
        plus.setOnClickListener(v->{zoom=Math.min(2.7f,zoom+0.3f);updateZoom();});
        tools.addView(plus,new LinearLayout.LayoutParams(UiKit.dp(this,52),UiKit.dp(this,44)));
        controls.addView(tools);
        page.addView(controls);

        horizontal=new HorizontalScrollView(this);
        horizontal.setFillViewport(false);
        horizontal.setHorizontalScrollBarEnabled(true);
        preview=new AddressConfigView(this);
        preview.setInteractive(true);
        preview.setSnapEnabled(false);
        preview.setListener((sender,recipient)->{
            if(sourceMode){sourceSender=new RectF(sender);sourceRecipient=new RectF(recipient);}
            else{targetSender=new RectF(sender);targetRecipient=new RectF(recipient);}
            updateHint();
        });
        horizontal.addView(preview,new HorizontalScrollView.LayoutParams(UiKit.dp(this,720),ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(horizontal,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        LinearLayout bottom=new LinearLayout(this);
        bottom.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,16));
        MaterialButton save=UiKit.primary(this,"Adresslayout übernehmen");
        save.setOnClickListener(v->save());
        bottom.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)));
        page.addView(bottom);

        setContentView(page);
        SystemUi.apply(this,page);
        applyMode();
        updateZoom();
    }

    private void updateZoom(){
        if(preview==null)return;
        int screen=getResources().getDisplayMetrics().widthPixels;
        int width=Math.round((screen-UiKit.dp(this,24))*zoom);
        ViewGroup.LayoutParams lp=preview.getLayoutParams();
        if(lp==null)lp=new HorizontalScrollView.LayoutParams(width,ViewGroup.LayoutParams.MATCH_PARENT);
        lp.width=Math.max(screen-UiKit.dp(this,24),width);
        preview.setLayoutParams(lp);
    }

    private void saveCurrentBoxes(){
        if(preview==null)return;
        if(sourceMode){
            sourceSender=preview.senderBox();
            sourceRecipient=preview.recipientBox();
        }else{
            targetSender=preview.senderBox();
            targetRecipient=preview.recipientBox();
        }
    }

    private void applyMode(){
        if(preview==null)return;
        preview.setInteractive(true);
        if(sourceMode){
            preview.setBoxes(sourceSender,sourceRecipient);
            preview.setReservedArea(null,null);
            snap.setChecked(false);
        }else{
            preview.setBoxes(targetSender,targetRecipient);
            RectF reserved=profile==null?new RectF():AddressLayoutRules.reserved(profile,options);
            preview.setReservedArea(reserved,reserved.isEmpty()?null:"Reserviert");
        }
        updateHint();
    }

    private void updateHint(){
        if(sourceMode){
            hint.setText("Ziehe Absender und Empfänger genau über die Bereiche, die im aktuellen PDF bereits vorhanden sind. Ohne Einrasten kannst du Größe und Position frei anpassen.");
        }else if(preview!=null&&preview.hasCollision()){
            hint.setText("Die Zielposition kollidiert mit einer reservierten Fläche. Verschiebe die Felder, bis die rote Markierung verschwindet.");
        }else{
            hint.setText("Lege fest, wo Absender und Empfänger im versendeten Brief erscheinen sollen. Einrasten hält beide Bereiche auf derselben typischen Adresslinie.");
        }
    }

    private void loadPdf(){
        String path=getIntent().getStringExtra(EXTRA_FILE);
        if(path==null)return;
        new Thread(()->{
            Bitmap result=null;
            try(ParcelFileDescriptor pfd=ParcelFileDescriptor.open(new File(path),ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer=new PdfRenderer(pfd);
                PdfRenderer.Page page=renderer.openPage(0)){
                int width=1400;
                int height=Math.max(1,Math.round(width*(page.getHeight()/(float)page.getWidth())));
                result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
                page.render(result,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap finalResult=result;
                runOnUiThread(()->{
                    bitmap=finalResult;
                    preview.setBitmap(finalResult);
                    applyMode();
                });
            }catch(Exception e){
                if(result!=null&&!result.isRecycled())result.recycle();
                runOnUiThread(()->DebugUtil.error(this,preview,"PDF-Vorschau",e));
            }
        },"address-full-preview").start();
    }

    private void save(){
        saveCurrentBoxes();
        Intent result=new Intent();
        result.putExtra(EXTRA_SOURCE_SENDER,AddressCorrectionProcessor.encode(sourceSender));
        result.putExtra(EXTRA_SOURCE_RECIPIENT,AddressCorrectionProcessor.encode(sourceRecipient));
        result.putExtra(EXTRA_TARGET_SENDER,AddressCorrectionProcessor.encode(targetSender));
        result.putExtra(EXTRA_TARGET_RECIPIENT,AddressCorrectionProcessor.encode(targetRecipient));
        setResult(RESULT_OK,result);
        finish();
    }

    @Override protected void onDestroy(){
        if(preview!=null)preview.clearBitmap();
        else if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
        super.onDestroy();
    }
}
