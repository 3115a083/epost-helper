package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    private static final RectF EDIT_VIEWPORT=new RectF(0f,0f,0.62f,0.38f);

    private AddressConfigView preview;
    private Profile profile;
    private final JobOptions options=new JobOptions();

    private RectF sourceSender=new RectF();
    private RectF sourceRecipient=new RectF();
    private RectF targetSender=new RectF();
    private RectF targetRecipient=new RectF();

    private boolean sourceMode=true;
    private boolean targetCustomized=false;
    private MaterialSwitch snap;
    private TextView hint;
    private TextView previewStatus;

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

        RectF senderAnchor=profile==null?AddressLayoutRules.normalSender():AddressLayoutRules.targetSender(profile,options);
        RectF recipientAnchor=profile==null?AddressLayoutRules.normalRecipient():AddressLayoutRules.targetRecipient(profile,options);
        targetSender=rectExtra(EXTRA_TARGET_SENDER,AddressLayoutRules.moveLike(sourceSender,senderAnchor));
        targetRecipient=rectExtra(EXTRA_TARGET_RECIPIENT,AddressLayoutRules.moveLike(sourceRecipient,recipientAnchor));

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
        back.setContentDescription("Zurück");
        back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        bar.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));

        LinearLayout titles=new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(UiKit.heading(this,"Adresslayout bearbeiten",22));
        TextView sub=UiKit.body(this,"Vergrößerter Adressbereich der ersten Seite");
        sub.setTextSize(12);
        titles.addView(sub);
        bar.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(bar);

        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(UiKit.dp(this,16),0,UiKit.dp(this,16),UiKit.dp(this,8));

        MaterialButtonToggleGroup modes=new MaterialButtonToggleGroup(this);
        modes.setSingleSelection(true);
        modes.setSelectionRequired(true);

        MaterialButton source=UiKit.tonal(this,"1 · Originalbereiche");
        source.setId(View.generateViewId());
        source.setTextColor(UiKit.resolveText(this));

        MaterialButton target=UiKit.tonal(this,"2 · Zielposition");
        target.setId(View.generateViewId());
        target.setTextColor(UiKit.resolveText(this));

        modes.addView(source,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
        modes.addView(target,new LinearLayout.LayoutParams(0,UiKit.dp(this,48),1f));
        modes.check(source.getId());
        modes.addOnButtonCheckedListener((group,id,checked)->{
            if(!checked)return;
            saveCurrentBoxes();
            sourceMode=id==source.getId();
            if(!sourceMode&&!targetCustomized&&profile!=null){
                targetSender=AddressLayoutRules.moveLike(sourceSender,AddressLayoutRules.targetSender(profile,options));
                targetRecipient=AddressLayoutRules.moveLike(sourceRecipient,AddressLayoutRules.targetRecipient(profile,options));
            }
            applyMode();
        });
        controls.addView(modes);

        hint=UiKit.body(this,"");
        hint.setTextSize(13);
        hint.setPadding(0,UiKit.dp(this,8),0,UiKit.dp(this,8));
        controls.addView(hint);

        snap=new MaterialSwitch(this);
        snap.setText("An Adresslinie einrasten");
        snap.setTextColor(UiKit.resolveText(this));
        snap.setChecked(false);
        snap.setOnCheckedChangeListener((button,checked)->preview.setSnapEnabled(checked));
        controls.addView(snap);

        page.addView(controls);

        previewStatus=UiKit.body(this,"PDF wird geladen…");
        previewStatus.setGravity(Gravity.CENTER);
        previewStatus.setPadding(UiKit.dp(this,16),UiKit.dp(this,4),UiKit.dp(this,16),UiKit.dp(this,8));
        page.addView(previewStatus);

        preview=new AddressConfigView(this);
        preview.setViewport(EDIT_VIEWPORT);
        preview.setInteractive(true);
        preview.setSnapEnabled(false);
        preview.setBackgroundColor(android.graphics.Color.WHITE);
        preview.setListener((sender,recipient)->{
            if(sourceMode){
                sourceSender=new RectF(sender);
                sourceRecipient=new RectF(recipient);
            }else{
                targetSender=new RectF(sender);
                targetRecipient=new RectF(recipient);
                targetCustomized=true;
            }
            updateHint();
        });

        int availableWidth=getResources().getDisplayMetrics().widthPixels-UiKit.dp(this,24);
        float cropRatio=(EDIT_VIEWPORT.height()*297f)/(EDIT_VIEWPORT.width()*210f);
        int previewHeight=Math.max(UiKit.dp(this,300),Math.round(availableWidth*cropRatio));
        LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,previewHeight);
        plp.setMargins(UiKit.dp(this,12),0,UiKit.dp(this,12),0);
        page.addView(preview,plp);

        LinearLayout spacer=new LinearLayout(this);
        page.addView(spacer,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        LinearLayout bottom=new LinearLayout(this);
        bottom.setPadding(UiKit.dp(this,16),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,16));
        MaterialButton save=UiKit.primary(this,"Adresslayout übernehmen");
        save.setOnClickListener(v->save());
        bottom.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)));
        page.addView(bottom);

        setContentView(page);
        SystemUi.apply(this,page);
        applyMode();
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
            preview.setWindowArea(null,null);
            preview.setRecipientSafetyArea(null,null);
            if(snap.isChecked())snap.setChecked(false);
        }else{
            preview.setBoxes(targetSender,targetRecipient);
            RectF window=profile==null?new RectF():AddressLayoutRules.window(profile,options);
            RectF postage=profile==null?new RectF():AddressLayoutRules.postage(profile,options);
            RectF allowedRecipient=profile==null?new RectF():AddressLayoutRules.targetRecipient(profile,options);
            RectF overflow=AddressLayoutRules.recipientOverflow(targetRecipient,allowedRecipient);
            preview.setWindowArea(window,window.isEmpty()?null:"Brief-Sichtfenster");
            preview.setReservedArea(postage,postage.isEmpty()?null:"Porto / DV-Freimachung");
            preview.setRecipientSafetyArea(overflow,overflow.isEmpty()?null:"Adressanteil außerhalb Sollfeld");
        }
        updateHint();
    }

    private void updateHint(){
        if(sourceMode){
            hint.setText("Ziehe die beiden Rahmen auf Absender und Empfänger im vorhandenen Brief. Ohne Einrasten kannst du Position und Größe frei ändern.");
        }else if(preview!=null&&preview.hasPostageCollision()){
            hint.setText("Rot: Ein verschobener Adressbereich ragt in das Porto-/DV-Feld. Verschiebe oder verkleinere den Rahmen, bevor du die Korrektur übernimmst.");
        }else if(preview!=null&&preview.hasWindowClip()){
            hint.setText("Rot: Ein Teil des verschobenen Adressbereichs liegt außerhalb des Brief-Sichtfensters und könnte im Umschlag abgeschnitten werden.");
        }else{
            RectF allowed=profile==null?new RectF():AddressLayoutRules.targetRecipient(profile,options);
            RectF overflow=AddressLayoutRules.recipientOverflow(targetRecipient,allowed);
            hint.setText(overflow.isEmpty()
                    ?"Zielposition passt in Sichtfenster und Portobereich. Die Rahmen zeigen die Position, die tatsächlich in die ausgegebene PDF geschrieben wird."
                    :"Violett: Dieser untere Teil entsteht, weil das ursprüngliche Empfängerfeld höher als der vorgesehene Zielbereich ist. Er bleibt sichtbar, solange er noch innerhalb des Brief-Sichtfensters liegt.");
        }
    }

    private void loadPdf(){
        String path=getIntent().getStringExtra(EXTRA_FILE);
        if(path==null||path.isBlank()){
            previewStatus.setText("PDF-Datei fehlt.");
            return;
        }

        File file=new File(path);
        if(!file.exists()||!file.canRead()){
            previewStatus.setText("PDF-Datei ist nicht mehr verfügbar.");
            return;
        }

        new Thread(()->{
            try{
                Bitmap bitmap=PdfPreviewRenderer.renderFirstPage(file,1400,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                runOnUiThread(()->{
                    preview.setBitmap(bitmap);
                    previewStatus.setText("Adressbereich · Seite 1");
                    applyMode();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    previewStatus.setText("PDF konnte nicht angezeigt werden.");
                    DebugUtil.error(this,preview,"PDF-Vorschau",e);
                });
            }
        },"address-crop-preview").start();
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
        super.onDestroy();
    }
}
