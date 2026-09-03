package de.eposthelper.app;

import android.content.Intent;
import android.content.ClipData;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OutboxActivity extends AppCompatActivity {
    public static final String EXTRA_PREPARED_ID="preparedId";
    private LinearLayout root;
    private int step=1;
    private final Set<String> selectedIds=new HashSet<>();
    private List<OutboxItem> allItems=new ArrayList<>();
    private List<OutboxItem> working=new ArrayList<>();
    private File merged;
    private Bitmap previewBitmap;
    private int previewIndex=0;
    private ImageView orderPreviewImage;
    private TextView orderPreviewTitle;
    private TextView orderPreviewMeta;
    private Bitmap orderPreviewBitmap;
    private final java.util.Map<String,Integer> pageCountCache=new java.util.HashMap<>();
    private boolean pageCountLoading=false;
    private int mergedPageCount=0;

    private String selectedProfileId;
    private MaterialSwitch color,duplex,localCorrection,c4,mergeLetters,keepSheetBoundaries;
    private Spinner registered,shipping;
    private PreparedJob editingPrepared;
    private AddressConfigView addressPreview;
    private TextView layoutHint;
    private RadioGroup profileGroup;
    private LinearLayout profilePriceBox;
    private final List<RadioButton> profileButtons=new ArrayList<>();

    private RectF sourceSender=AddressLayoutRules.normalSender();
    private RectF sourceRecipient=AddressLayoutRules.normalRecipient();
    private RectF targetSender=AddressLayoutRules.normalSender();
    private RectF targetRecipient=AddressLayoutRules.normalRecipient();
    private boolean addressEdited=false;
    private boolean editorRequestedBySwitch=false;

    private final ActivityResultLauncher<Intent> addressEditor=registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),result->{
                if(result.getResultCode()!=RESULT_OK||result.getData()==null){
                    if(editorRequestedBySwitch&&localCorrection!=null)localCorrection.setChecked(false);
                    editorRequestedBySwitch=false;
                    return;
                }
                Intent data=result.getData();
                sourceSender=AddressCorrectionProcessor.decode(data.getStringExtra(AddressEditActivity.EXTRA_SOURCE_SENDER));
                sourceRecipient=AddressCorrectionProcessor.decode(data.getStringExtra(AddressEditActivity.EXTRA_SOURCE_RECIPIENT));
                targetSender=AddressCorrectionProcessor.decode(data.getStringExtra(AddressEditActivity.EXTRA_TARGET_SENDER));
                targetRecipient=AddressCorrectionProcessor.decode(data.getStringExtra(AddressEditActivity.EXTRA_TARGET_RECIPIENT));
                addressEdited=!sourceSender.isEmpty()&&!sourceRecipient.isEmpty()&&!targetSender.isEmpty()&&!targetRecipient.isEmpty();
                editorRequestedBySwitch=false;
                if(localCorrection!=null&&!localCorrection.isChecked())localCorrection.setChecked(true);
                Profile p=SecureStore.find(this,selectedProfileId);
                applyLayoutForProfile(p,true);
            });

    private final ActivityResultLauncher<String[]> picker=registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),uris->{
                if(uris==null)return;
                for(Uri uri:uris){
                    try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
                    catch(Exception ignored){}
                    OutboxStore.add(this,uri,displayName(uri),false);
                }
                refreshItems();
            });

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);
        renderShell();
        String preparedId=getIntent().getStringExtra(EXTRA_PREPARED_ID);
        if(preparedId!=null){
            editingPrepared=PreparedJobStore.find(this,preparedId);
            if(editingPrepared!=null){
                File f=new File(editingPrepared.filePath);
                if(f.exists()){
                    merged=f;
                    selectedProfileId=editingPrepared.profileId;
                    sourceSender=new RectF(editingPrepared.sourceSender);
                    sourceRecipient=new RectF(editingPrepared.sourceRecipient);
                    targetSender=new RectF(editingPrepared.targetSender);
                    targetRecipient=new RectF(editingPrepared.targetRecipient);
                    addressEdited=!sourceSender.isEmpty()&&!sourceRecipient.isEmpty()&&!targetSender.isEmpty()&&!targetRecipient.isEmpty();
                    step=3;
                    renderStep();
                    loadPreparedPreviewAsync(f);
                }
            }
        }
    }

    @Override protected void onResume(){
        super.onResume();
        if(editingPrepared==null)refreshItems();
    }

    private String displayName(Uri uri){
        try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())return c.getString(0);
        }catch(Exception ignored){}
        String last=uri.getLastPathSegment();
        return last==null?"PDF":last;
    }

    private void renderShell(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->back());
        back.setContentDescription("Zurück");
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        top.addView(UiKit.heading(this,"Druckausgang",22),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28));
        scroll.addView(root);
        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        setContentView(page);
        SystemUi.apply(this,page);
        refreshItems();
    }

    private void back(){
        if(step>1){
            step--;
            if(step==1)working.clear();
            renderStep();
        }else{
            getOnBackPressedDispatcher().onBackPressed();
        }
    }

    private void refreshItems(){
        if(root==null)return;
        allItems=OutboxStore.load(this);
        if(step==1)renderStep();
        loadPageCountsAsync();
    }

    private void loadPageCountsAsync(){
        if(pageCountLoading)return;
        List<OutboxItem> missing=new ArrayList<>();
        for(OutboxItem item:allItems)if(!pageCountCache.containsKey(item.uri))missing.add(item);
        if(missing.isEmpty())return;
        pageCountLoading=true;
        new Thread(()->{
            java.util.Map<String,Integer> loaded=new java.util.HashMap<>();
            for(OutboxItem item:missing)loaded.put(item.uri,PdfMergeUtil.countPages(this,item.asUri()));
            runOnUiThread(()->{
                pageCountLoading=false;
                pageCountCache.putAll(loaded);
                if(step==1)renderStep();
                else if(step==2)loadOrderPreview();
            });
        },"outbox-page-counts").start();
    }

    private void loadPreparedPreviewAsync(File file){
        new Thread(()->{
            try{
                Bitmap bitmap=renderFirstPage(file,900);
                int pages=PdfMergeUtil.countPages(file);
                runOnUiThread(()->{
                    if(isFinishing()||isDestroyed()){
                        if(!bitmap.isRecycled())bitmap.recycle();
                        return;
                    }
                    if(previewBitmap!=null&&!previewBitmap.isRecycled())previewBitmap.recycle();
                    previewBitmap=bitmap;
                    mergedPageCount=pages;
                    if(step==3)renderStep();
                });
            }catch(Exception ignored){}
        },"prepared-preview").start();
    }

    private void renderStep(){
        root.removeAllViews();
        if(step==1)renderSelect();
        else if(step==2)renderOrder();
        else renderOptions();
    }

    private void stepHeader(String number,String title,String subtitle){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        TextView n=UiKit.body(this,"Schritt "+number+" von 3");n.setTextSize(12);box.addView(n);
        TextView t=UiKit.heading(this,title,22);t.setPadding(0,UiKit.dp(this,3),0,0);box.addView(t);
        TextView s=UiKit.body(this,subtitle);s.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,10));box.addView(s);
        root.addView(box);
    }

    private void renderSelect(){
        stepHeader("1","PDFs auswählen","Wähle eine oder mehrere Dateien aus dem Druckausgang. Automatisch importierte PDFs bleiben dort, bis sie erfolgreich versendet wurden.");

        int[] g=SettingsStore.gradient(this);
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(UiKit.heroTitle(this,allItems.isEmpty()?"PDFs hinzufügen":allItems.size()+" PDF"+(allItems.size()==1?" verfügbar":"s verfügbar"),22));
        hero.addView(UiKit.heroBody(this,"Dateien können manuell oder über den eingestellten Importordner in den Druckausgang gelangen."));
        MaterialButton add=UiKit.primary(this,"PDFs hinzufügen");
        add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        add.setOnClickListener(v->picker.launch(new String[]{"application/pdf"}));
        LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,50));alp.setMargins(0,UiKit.dp(this,10),0,0);hero.addView(add,alp);
        root.addView(UiKit.hero(this,hero,g[0],g[1]));

        if(allItems.isEmpty())return;

        for(OutboxItem item:allItems){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            CheckBox check=new CheckBox(this);
            check.setChecked(selectedIds.contains(item.id));
            check.setOnCheckedChangeListener((b,checked)->{
                if(checked)selectedIds.add(item.id);else selectedIds.remove(item.id);
            });
            row.addView(check,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));

            LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);
            TextView name=UiKit.heading(this,item.name,15);name.setMaxLines(2);text.addView(name);
            Integer pages=pageCountCache.get(item.uri);
            String pageText=pages==null?"Seiten werden ermittelt…":pages+" Seite"+(pages==1?"":"n");
            TextView meta=UiKit.body(this,pageText+(item.deleteAfterSend?" · Auto-Import":""));
            meta.setTextSize(12);text.addView(meta);
            row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            root.addView(UiKit.surfaceCard(this,row));
        }

        MaterialButton next=UiKit.primary(this,"Auswahl weiter");
        next.setOnClickListener(v->{
            working.clear();
            for(OutboxItem i:allItems)if(selectedIds.contains(i.id))working.add(i);
            if(working.isEmpty()){DebugUtil.error(this,next,"Bitte mindestens eine PDF auswählen.");return;}
            previewIndex=0;
            step=2;
            renderStep();
        });
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));nlp.setMargins(0,UiKit.dp(this,10),0,0);root.addView(next,nlp);
    }

    private void renderOrder(){
        stepHeader("2","Reihenfolge & Vorschau","Blättere durch die ausgewählten PDFs und ziehe sie unten an der Griffleiste in die gewünschte Reihenfolge.");

        if(working.isEmpty()){
            TextView empty=UiKit.body(this,"Keine PDFs für diesen Versand ausgewählt.");
            root.addView(UiKit.surfaceCard(this,empty));
            return;
        }

        previewIndex=Math.max(0,Math.min(previewIndex,working.size()-1));

        LinearLayout previewCard=new LinearLayout(this);
        previewCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout previewHead=new LinearLayout(this);
        previewHead.setGravity(Gravity.CENTER_VERTICAL);
        orderPreviewTitle=UiKit.heading(this,"",17);
        previewHead.addView(orderPreviewTitle,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        orderPreviewMeta=UiKit.body(this,"");
        orderPreviewMeta.setTextSize(12);
        previewHead.addView(orderPreviewMeta);
        previewCard.addView(previewHead);

        orderPreviewImage=new ImageView(this);
        orderPreviewImage.setAdjustViewBounds(true);
        orderPreviewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        orderPreviewImage.setBackgroundColor(android.graphics.Color.WHITE);
        orderPreviewImage.setContentDescription("Vorschau der aktuell ausgewählten PDF");
        previewCard.addView(orderPreviewImage,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,360)));

        LinearLayout pager=new LinearLayout(this);
        pager.setGravity(Gravity.CENTER_VERTICAL);
        ImageView previous=new ImageView(this);
        previous.setImageResource(R.drawable.ic_chevron_left);
        previous.setColorFilter(SettingsStore.primary(this));
        previous.setContentDescription("Vorherige PDF");
        previous.setPadding(UiKit.dp(this,12),UiKit.dp(this,12),UiKit.dp(this,12),UiKit.dp(this,12));
        previous.setOnClickListener(v->{
            if(previewIndex>0){previewIndex--;loadOrderPreview();}
        });
        pager.addView(previous,new LinearLayout.LayoutParams(UiKit.dp(this,52),UiKit.dp(this,52)));

        TextView pagerText=UiKit.body(this,"Durch die PDFs blättern");
        pagerText.setGravity(Gravity.CENTER);
        pager.addView(pagerText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        ImageView nextPreview=new ImageView(this);
        nextPreview.setImageResource(R.drawable.ic_chevron_right);
        nextPreview.setColorFilter(SettingsStore.primary(this));
        nextPreview.setContentDescription("Nächste PDF");
        nextPreview.setPadding(UiKit.dp(this,12),UiKit.dp(this,12),UiKit.dp(this,12),UiKit.dp(this,12));
        nextPreview.setOnClickListener(v->{
            if(previewIndex<working.size()-1){previewIndex++;loadOrderPreview();}
        });
        pager.addView(nextPreview,new LinearLayout.LayoutParams(UiKit.dp(this,52),UiKit.dp(this,52)));
        previewCard.addView(pager);

        root.addView(UiKit.surfaceCard(this,previewCard));
        loadOrderPreview();

        TextView sortTitle=UiKit.heading(this,"Reihenfolge",17);
        sortTitle.setPadding(0,UiKit.dp(this,10),0,UiKit.dp(this,4));
        root.addView(sortTitle);

        LinearLayout compactList=new LinearLayout(this);
        compactList.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<working.size();i++)compactList.addView(compactOrderRow(i,working.get(i)));
        root.addView(UiKit.surfaceCard(this,compactList));

        MaterialButton next=UiKit.primary(this,"Versand vorbereiten");
        next.setOnClickListener(v->{
            if(working.isEmpty()){DebugUtil.error(this,next,"Keine PDF für diesen Versand ausgewählt.");return;}
            next.setEnabled(false);
            next.setText("PDFs werden verbunden…");
            prepareMerged(true);
        });
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));
        nlp.setMargins(0,UiKit.dp(this,12),0,UiKit.dp(this,8));
        root.addView(next,nlp);
    }

    private void loadOrderPreview(){
        if(orderPreviewImage==null||working.isEmpty())return;
        int index=Math.max(0,Math.min(previewIndex,working.size()-1));
        OutboxItem item=working.get(index);
        orderPreviewTitle.setText(item.name);
        Integer pages=pageCountCache.get(item.uri);
        orderPreviewMeta.setText((index+1)+" / "+working.size()+" · "+(pages==null?"Seiten werden ermittelt…":pages+" Seite"+(pages==1?"":"n")));
        orderPreviewImage.setImageDrawable(null);
        if(orderPreviewBitmap!=null&&!orderPreviewBitmap.isRecycled()){
            orderPreviewBitmap.recycle();
            orderPreviewBitmap=null;
        }

        new Thread(()->{
            try{
                Bitmap bitmap=PdfPreviewRenderer.renderFirstPage(this,item.asUri(),900,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                runOnUiThread(()->{
                    if(orderPreviewImage==null||index!=previewIndex){
                        if(!bitmap.isRecycled())bitmap.recycle();
                        return;
                    }
                    orderPreviewBitmap=bitmap;
                    orderPreviewImage.setImageBitmap(bitmap);
                });
            }catch(Exception e){
                runOnUiThread(()->DebugUtil.error(this,orderPreviewImage,"PDF-Vorschau",e));
            }
        },"order-preview").start();
    }

    private View compactOrderRow(int index,OutboxItem item){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dp(this,4),UiKit.dp(this,4),UiKit.dp(this,4),UiKit.dp(this,4));
        row.setTag(index);

        ImageView handle=new ImageView(this);
        handle.setImageResource(R.drawable.ic_drag_handle);
        handle.setColorFilter(UiKit.resolveSecondaryText(this));
        handle.setContentDescription("PDF verschieben");
        handle.setPadding(UiKit.dp(this,9),UiKit.dp(this,9),UiKit.dp(this,9),UiKit.dp(this,9));
        handle.setOnLongClickListener(v->{
            ClipData data=ClipData.newPlainText("fromIndex",String.valueOf(index));
            v.startDragAndDrop(data,new View.DragShadowBuilder(row),index,0);
            return true;
        });
        row.addView(handle,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));

        LinearLayout text=new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView name=UiKit.heading(this,item.name,14);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name);
        TextView pos=UiKit.body(this,"Position "+(index+1));
        pos.setTextSize(11);
        text.addView(pos);
        row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));

        ImageView remove=new ImageView(this);
        remove.setImageResource(R.drawable.ic_delete_small);
        remove.setColorFilter(0xFFB3261E);
        remove.setContentDescription("Aus diesem Versand entfernen");
        remove.setPadding(UiKit.dp(this,10),UiKit.dp(this,10),UiKit.dp(this,10),UiKit.dp(this,10));
        remove.setOnClickListener(v->{
            int current=working.indexOf(item);
            if(current>=0){
                working.remove(current);
                if(previewIndex>=working.size())previewIndex=Math.max(0,working.size()-1);
                renderStep();
            }
        });
        row.addView(remove,new LinearLayout.LayoutParams(UiKit.dp(this,48),UiKit.dp(this,48)));

        row.setOnDragListener((v,event)->{
            if(event.getAction()==DragEvent.ACTION_DROP){
                Object state=event.getLocalState();
                if(!(state instanceof Integer))return false;
                int from=(Integer)state;
                int to=working.indexOf(item);
                if(from<0||from>=working.size()||to<0||to>=working.size()||from==to)return true;
                OutboxItem moved=working.remove(from);
                working.add(Math.max(0,Math.min(to,working.size())),moved);
                previewIndex=working.indexOf(moved);
                renderStep();
                return true;
            }
            return event.getAction()==DragEvent.ACTION_DRAG_STARTED||
                    event.getAction()==DragEvent.ACTION_DRAG_ENTERED||
                    event.getAction()==DragEvent.ACTION_DRAG_LOCATION||
                    event.getAction()==DragEvent.ACTION_DRAG_EXITED||
                    event.getAction()==DragEvent.ACTION_DRAG_ENDED;
        });

        return row;
    }

    private void prepareMerged(boolean advance){
        List<OutboxItem> snapshot=new ArrayList<>(working);
        new Thread(()->{
            try{
                File next=PdfMergeUtil.merge(this,snapshot);
                Bitmap bitmap=renderFirstPage(next,900);
                int pages=PdfMergeUtil.countPages(next);
                runOnUiThread(()->{
                    if(merged!=null&&merged.exists())merged.delete();
                    if(previewBitmap!=null&&!previewBitmap.isRecycled())previewBitmap.recycle();
        if(orderPreviewBitmap!=null&&!orderPreviewBitmap.isRecycled())orderPreviewBitmap.recycle();
                    merged=next;previewBitmap=bitmap;mergedPageCount=pages;
                    if(advance){step=3;renderStep();}
                    else if(step==2)renderStep();
                });
            }catch(Exception e){
                runOnUiThread(()->DebugUtil.error(this,root,"PDFs verbinden",e));
            }
        },"outbox-merge").start();
    }

    private Bitmap renderFirstPage(File file,int width) throws Exception{
        return PdfPreviewRenderer.renderFirstPage(file,width,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
    }

    private void renderOptions(){
        stepHeader("3","Layout, Druck & Versand","Prüfe das Adresslayout, lege Druck- und Zusatzoptionen fest und wähle danach eines der kompatiblen Profile.");

        LinearLayout print= new LinearLayout(this);print.setOrientation(LinearLayout.VERTICAL);
        print.addView(UiKit.heading(this,"Druckeinstellungen",17));
        color=new MaterialSwitch(this);color.setText("Farbdruck");print.addView(color);
        duplex=new MaterialSwitch(this);duplex.setText("Doppelseitig");print.addView(duplex);
        TextView regLabel=UiKit.body(this,"Einschreiben");regLabel.setPadding(0,UiKit.dp(this,8),0,0);print.addView(regLabel);
        registered=new Spinner(this);
        registered.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Nein","Einschreiben Einwurf","Einschreiben","Einschreiben Rückschein"}));
        print.addView(registered,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        TextView shippingLabel=UiKit.body(this,"Versandziel");shippingLabel.setPadding(0,UiKit.dp(this,8),0,0);print.addView(shippingLabel);
        shipping=new Spinner(this);
        shipping.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"National","International"}));
        print.addView(shipping,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        c4=new MaterialSwitch(this);c4.setText("C4-Umschlag, ungefalzt");print.addView(c4);

        if(editingPrepared!=null){
            color.setChecked(editingPrepared.color);
            duplex.setChecked(editingPrepared.duplex);
            int regIndex=0;for(int i=0;i<registered.getCount();i++)if(String.valueOf(registered.getItemAtPosition(i)).equals(editingPrepared.registered))regIndex=i;
            registered.setSelection(regIndex);
            shipping.setSelection("international".equals(editingPrepared.shipping)?1:0);
            c4.setChecked(editingPrepared.c4);
        }else if(!working.isEmpty()&&working.get(0).hasPreset){
            JobOptions preset=working.get(0).presetOptions();
            color.setChecked(preset.color);
            duplex.setChecked(preset.duplex);
            int regIndex=0;for(int i=0;i<registered.getCount();i++)if(String.valueOf(registered.getItemAtPosition(i)).equals(preset.registered))regIndex=i;
            registered.setSelection(regIndex);
            shipping.setSelection("international".equals(preset.shipping)?1:0);
            c4.setChecked(preset.c4);
        }
        root.addView(UiKit.surfaceCard(this,print));

        if(editingPrepared==null&&working.size()>1){
            LinearLayout mergeBox=new LinearLayout(this);mergeBox.setOrientation(LinearLayout.VERTICAL);
            mergeBox.addView(UiKit.heading(this,"Mehrere PDFs",17));
            mergeLetters=new MaterialSwitch(this);
            mergeLetters.setText("Als einen Brief an denselben Empfänger zusammenführen");
            mergeLetters.setChecked(true);
            mergeBox.addView(mergeLetters);

            keepSheetBoundaries=new MaterialSwitch(this);
            keepSheetBoundaries.setText("Dokumente auf getrennten Blättern beginnen");
            keepSheetBoundaries.setChecked(true);
            mergeBox.addView(keepSheetBoundaries);

            TextView mergeHelp=UiKit.body(this,"An: Nach einem Dokument mit ungerader Seitenzahl fügt die App bei Duplex eine weiße Seite ein. Aus: Die PDFs werden ohne künstliche Leerseite direkt hintereinander zusammengeführt.");
            mergeHelp.setTextSize(12);mergeHelp.setPadding(0,UiKit.dp(this,4),0,0);mergeBox.addView(mergeHelp);

            Runnable updateSheetOption=()->{
                boolean visible=mergeLetters.isChecked()&&duplex.isChecked();
                keepSheetBoundaries.setVisibility(visible?View.VISIBLE:View.GONE);
                mergeHelp.setVisibility(visible?View.VISIBLE:View.GONE);
            };
            mergeLetters.setOnCheckedChangeListener((button,checked)->updateSheetOption.run());
            duplex.setOnCheckedChangeListener((button,checked)->updateSheetOption.run());
            keepSheetBoundaries.setOnClickListener(v->refreshProfiles());
            updateSheetOption.run();
            root.addView(UiKit.surfaceCard(this,mergeBox));
        }

        LinearLayout addressBox=new LinearLayout(this);addressBox.setOrientation(LinearLayout.VERTICAL);
        addressBox.addView(UiKit.heading(this,"Adresslayout",17));
        localCorrection=new MaterialSwitch(this);localCorrection.setText("Adressbereiche vor dem Versand verschieben");addressBox.addView(localCorrection);
        layoutHint=UiKit.body(this,"Wähle unten ein Profil. Bei Einschreiben markiert die Vorschau reservierte Flächen. Rot bedeutet, dass die aktuelle Position kollidiert.");
        layoutHint.setTextSize(13);layoutHint.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8));addressBox.addView(layoutHint);
        addressPreview=new AddressConfigView(this);
        addressPreview.setSnapEnabled(false);
        addressPreview.setInteractive(false);
        if(previewBitmap!=null)addressPreview.setBitmap(previewBitmap.copy(Bitmap.Config.ARGB_8888,false));
        addressPreview.setOnClickListener(v->openAddressEditor(v));
        addressPreview.setContentDescription("Adressvorschau. Antippen zum großen Bearbeiten.");
        addressPreview.setListener((sender,recipient)->{targetSender=new RectF(sender);targetRecipient=new RectF(recipient);updateLayoutHint();});
        addressBox.addView(addressPreview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,235)));
        MaterialButton editLarge=UiKit.primary(this,"Große Vorschau öffnen & bearbeiten");
        editLarge.setOnClickListener(v->openAddressEditor(editLarge));
        LinearLayout.LayoutParams elp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54));elp.setMargins(0,UiKit.dp(this,10),0,0);
        addressBox.addView(editLarge,elp);
        root.addView(UiKit.surfaceCard(this,addressBox));

        root.addView(section("Kompatible Profile & Kosten"));
        profileGroup=new RadioGroup(this);
        profilePriceBox=new LinearLayout(this);profilePriceBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(profilePriceBox);

        View.OnClickListener refresh=v->refreshProfiles();
        color.setOnClickListener(refresh);duplex.setOnClickListener(refresh);c4.setOnClickListener(refresh);
        registered.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){refreshProfiles();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        shipping.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){refreshProfiles();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });
        localCorrection.setOnCheckedChangeListener((b,checked)->{
            Profile p=SecureStore.find(this,selectedProfileId);
            applyLayoutForProfile(p,checked);
            if(checked&&!addressEdited&&p!=null){
                editorRequestedBySwitch=true;
                openAddressEditor(b);
            }
        });

        refreshProfiles();
        if(editingPrepared!=null&&editingPrepared.addressCorrection)localCorrection.setChecked(true);
        else if(editingPrepared==null&&!working.isEmpty()&&working.get(0).hasPreset&&working.get(0).presetCorrection)localCorrection.setChecked(true);

        MaterialButton saveOutbox=UiKit.tonal(this,editingPrepared==null?"In Ausgang legen":"Änderungen im Ausgang speichern");
        saveOutbox.setOnClickListener(v->savePrepared(saveOutbox));
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54));qlp.setMargins(0,UiKit.dp(this,12),0,0);root.addView(saveOutbox,qlp);

        MaterialButton send=UiKit.primary(this,"Brief jetzt versenden");
        send.setOnClickListener(v->send(send));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,58));slp.setMargins(0,UiKit.dp(this,8),0,0);root.addView(send,slp);
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,19);t.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,5));return t;
    }

    private JobOptions currentOptions(){
        JobOptions o=new JobOptions();
        o.color=color!=null&&color.isChecked();
        o.duplex=duplex!=null&&duplex.isChecked();
        o.registered=registered==null?"Nein":String.valueOf(registered.getSelectedItem());
        o.shipping=shipping!=null&&shipping.getSelectedItemPosition()==1?"international":"national";
        o.c4=c4!=null&&c4.isChecked();
        o.addressCorrection=localCorrection!=null&&localCorrection.isChecked();
        return o;
    }

    private boolean compatible(Profile p,JobOptions o){
        return ProfileCompatibility.compatible(p,o);
    }

    private void refreshProfiles(){
        if(profilePriceBox==null)return;
        JobOptions o=currentOptions();
        profilePriceBox.removeAllViews();
        profileButtons.clear();
        profileGroup=new RadioGroup(this);

        List<Profile> profiles=SecureStore.load(this);
        Profile selected=SecureStore.find(this,selectedProfileId);
        if(selected==null||!compatible(selected,o)){
            selectedProfileId=null;
            for(Profile candidate:profiles){
                if(compatible(candidate,o)){selectedProfileId=candidate.id;break;}
            }
        }
        int pages=effectiveMergedPageCount();
        boolean found=false;
        for(Profile p:profiles){
            if(!compatible(p,o))continue;
            found=true;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView logo=new ImageView(this);
            logo.setImageResource(DebugProfileManager.isDebug(p)?R.drawable.ic_provider_debug:
                    Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?R.drawable.ic_provider_lxp:R.drawable.ic_provider_post);
            row.addView(logo,new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40)));
            RadioButton rb=new RadioButton(this);rb.setId(View.generateViewId());rb.setTag(p.id);
            rb.setText(p.name);rb.setTextSize(16);
            rb.setChecked(p.id.equals(selectedProfileId));
            profileButtons.add(rb);
            row.addView(rb,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            card.addView(row);

            String routeText=DebugProfileManager.isDebug(p)?"Lokale Debug-Ausgabe":
                    Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?"LetterXpress · "+(Profile.TYPE_LXP_API.equals(p.type)?"API":"SFTP"):
                            "Deutsche Post · WebDAV";
            TextView route=UiKit.body(this,routeText);
            route.setTextSize(12);route.setPadding(UiKit.dp(this,48),0,0,0);card.addView(route);
            TextView price=UiKit.body(this,"Kosten werden ermittelt…");price.setPadding(UiKit.dp(this,48),UiKit.dp(this,4),0,0);card.addView(price);
            loadPrice(p,o,pages,price);

            rb.setOnCheckedChangeListener((button,checked)->{
                if(!checked)return;
                for(RadioButton other:profileButtons)if(other!=button&&other.isChecked())other.setChecked(false);
                selectedProfileId=String.valueOf(button.getTag());
                applyLayoutForProfile(SecureStore.find(this,selectedProfileId),localCorrection.isChecked());
            });
            profilePriceBox.addView(UiKit.surfaceCard(this,card));
        }

        if(!found){
            selectedProfileId=null;
            TextView none=UiKit.body(this,"Kein Profil unterstützt diese Kombination. Deutsche-Post-Profile entsprechen ihren serverseitig konfigurierten Druckoptionen; LetterXpress unterstützt kein Rückschein-Profil.");
            profilePriceBox.addView(UiKit.surfaceCard(this,none));
        }else{
            Profile p=SecureStore.find(this,selectedProfileId);
            if(p==null){
                for(Profile candidate:profiles)if(compatible(candidate,o)){selectedProfileId=candidate.id;p=candidate;break;}
            }
            applyLayoutForProfile(p,localCorrection.isChecked());
        }
    }

    private void loadPrice(Profile p,JobOptions o,int pages,TextView target){
        if(DebugProfileManager.isDebug(p)){
            target.setText("Kostenlos · lokale Testausgabe");
            return;
        }
        if(Profile.PROVIDER_POST.equals(p.provider)){
            target.setText("Preis wird vom E-POST-Ziel bestimmt");return;
        }
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

    private void applyLayoutForProfile(Profile p,boolean correctionRequested){
        if(addressPreview==null)return;
        JobOptions o=currentOptions();

        if(p==null){
            addressPreview.setReservedArea(null,null);
            addressPreview.setInteractive(false);
            localCorrection.setEnabled(false);
            layoutHint.setText("Wähle zuerst ein kompatibles Versandprofil.");
            return;
        }

        localCorrection.setEnabled(true);

        if(!addressEdited&&AddressCorrectionProcessor.configured(p)){
            sourceSender=AddressCorrectionProcessor.decode(p.senderWindow);
            sourceRecipient=AddressCorrectionProcessor.decode(p.recipientWindow);
        }
        if(!addressEdited&&sourceSender.isEmpty())sourceSender=AddressLayoutRules.normalSender();
        if(!addressEdited&&sourceRecipient.isEmpty())sourceRecipient=AddressLayoutRules.normalRecipient();

        if(correctionRequested){
            if(!addressEdited){
                targetSender=AddressLayoutRules.moveLike(sourceSender,AddressLayoutRules.targetSender(p,o));
                targetRecipient=AddressLayoutRules.moveLike(sourceRecipient,AddressLayoutRules.targetRecipient(p,o));
            }
            RectF postage=DebugProfileManager.isDebug(p)?new RectF():AddressLayoutRules.postage(p,o);
            if(!postage.isEmpty()){
                targetSender=AddressLayoutRules.lockAbovePostage(targetSender,postage);
                targetRecipient=AddressLayoutRules.lockBelowPostage(targetRecipient,postage);
            }
            if(previewBitmap!=null&&!previewBitmap.isRecycled()){
                try{
                    addressPreview.setBitmap(AddressPreviewComposer.compose(previewBitmap,sourceSender,sourceRecipient,targetSender,targetRecipient));
                }catch(Exception ignored){}
            }
            addressPreview.setBoxes(targetSender,targetRecipient);
            addressPreview.setInteractive(false);
        }else{
            if(previewBitmap!=null&&!previewBitmap.isRecycled())
                addressPreview.setBitmap(previewBitmap.copy(Bitmap.Config.ARGB_8888,false));
            addressPreview.setBoxes(sourceSender,sourceRecipient);
            addressPreview.setInteractive(false);
        }

        RectF window=DebugProfileManager.isDebug(p)?new RectF():AddressLayoutRules.window(p,o);
        RectF reserved=DebugProfileManager.isDebug(p)?new RectF():AddressLayoutRules.reserved(p,o);
        addressPreview.setWindowArea(window,window.isEmpty()?null:"Brief-Sichtfenster");
        addressPreview.setReservedArea(reserved,reserved.isEmpty()?null:"Porto / DV-Freimachung");
        addressPreview.setRecipientSafetyArea(null,null);

        if(Profile.PROVIDER_POST.equals(p.provider)&&p.addressCorrection&&correctionRequested){
            layoutHint.setText("Lokale Adresskorrektur ist eingeschaltet. Dieses Deutsche-Post-Profil ist zusätzlich als serverseitig korrigiert markiert. Prüfe in der großen Vorschau, ob dadurch eine Doppelkorrektur entstehen kann.");
        }else{
            updateLayoutHint();
        }
    }

    private void openAddressEditor(View anchor){
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){DebugUtil.error(this,anchor,"Bitte zuerst ein Versandprofil wählen.");return;}
        if(merged==null||!merged.exists()){DebugUtil.error(this,anchor,"Die PDF-Vorschau ist noch nicht verfügbar.");return;}

        if(!addressEdited&&AddressCorrectionProcessor.configured(p)){
            sourceSender=AddressCorrectionProcessor.decode(p.senderWindow);
            sourceRecipient=AddressCorrectionProcessor.decode(p.recipientWindow);
        }
        if(sourceSender.isEmpty())sourceSender=AddressLayoutRules.normalSender();
        if(sourceRecipient.isEmpty())sourceRecipient=AddressLayoutRules.normalRecipient();
        if(!addressEdited){
            targetSender=AddressLayoutRules.moveLike(sourceSender,AddressLayoutRules.targetSender(p,currentOptions()));
            targetRecipient=AddressLayoutRules.moveLike(sourceRecipient,AddressLayoutRules.targetRecipient(p,currentOptions()));
        }

        Intent intent=new Intent(this,AddressEditActivity.class);
        intent.putExtra(AddressEditActivity.EXTRA_FILE,merged.getAbsolutePath());
        intent.putExtra(AddressEditActivity.EXTRA_PROFILE,p.id);
        intent.putExtra(AddressEditActivity.EXTRA_REGISTERED,currentOptions().registered);
        intent.putExtra(AddressEditActivity.EXTRA_SOURCE_SENDER,AddressCorrectionProcessor.encode(sourceSender));
        intent.putExtra(AddressEditActivity.EXTRA_SOURCE_RECIPIENT,AddressCorrectionProcessor.encode(sourceRecipient));
        intent.putExtra(AddressEditActivity.EXTRA_TARGET_SENDER,AddressCorrectionProcessor.encode(targetSender));
        intent.putExtra(AddressEditActivity.EXTRA_TARGET_RECIPIENT,AddressCorrectionProcessor.encode(targetRecipient));
        addressEditor.launch(intent);
    }

    private void updateLayoutHint(){
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null)return;
        if(addressPreview.hasPostageCollision()){
            layoutHint.setText("Adressbereich kollidiert mit dem Porto-/DV-Feld. Öffne die große Vorschau und korrigiere die Zielposition.");
        }else if(localCorrection.isChecked()){
            layoutHint.setText(addressEdited
                    ?"Adresskorrektur aktiv. Original- und Zielbereiche wurden für diesen Brief bestätigt."
                    :"Adresskorrektur aktiv. Öffne „Groß bearbeiten“, um die Originalbereiche dieses Briefes zu prüfen.");
        }else{
            layoutHint.setText("Adresskorrektur ist aus. Über „Groß bearbeiten“ kannst du Original- und Zielbereiche in einer großen Vorschau prüfen.");
        }
    }

    private boolean mergeAsOne(){
        return editingPrepared!=null||working.size()<=1||mergeLetters==null||mergeLetters.isChecked();
    }

    private boolean preserveSheetBoundaries(){
        return mergeAsOne()&&duplex!=null&&duplex.isChecked()&&keepSheetBoundaries!=null&&keepSheetBoundaries.isChecked();
    }

    private int effectiveMergedPageCount(){
        if(!preserveSheetBoundaries()||working.size()<2)return mergedPageCount;
        int pages=0;
        for(int i=0;i<working.size();i++){
            Integer count=pageCountCache.get(working.get(i).uri);
            if(count==null)return mergedPageCount;
            pages+=count;
            if(i<working.size()-1&&count%2==1)pages++;
        }
        return pages;
    }

    private void fillPreparedJob(PreparedJob j,Profile p,JobOptions o){
        j.profileId=p.id;
        j.color=o.color;
        j.duplex=o.duplex;
        j.registered=o.registered;
        j.c4=o.c4;
        j.shipping=o.shipping;
        j.addressCorrection=o.addressCorrection;
        j.sourceSender=new RectF(sourceSender);
        j.sourceRecipient=new RectF(sourceRecipient);
        j.targetSender=new RectF(targetSender);
        j.targetRecipient=new RectF(targetRecipient);
    }

    private void savePrepared(MaterialButton button){
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){DebugUtil.error(this,button,"Bitte ein kompatibles Profil wählen.");return;}
        if(merged==null||!merged.exists()){DebugUtil.error(this,button,"Die vorbereitete PDF fehlt.");return;}
        JobOptions o=currentOptions();
        if(o.addressCorrection&&!addressEdited){
            DebugUtil.error(this,button,"Bestätige zuerst Original- und Zielposition im Adresseditor.");
            return;
        }

        button.setEnabled(false);
        button.setText("Wird gespeichert…");
        boolean combine=mergeAsOne();
        boolean preserveSheets=preserveSheetBoundaries();

        new Thread(()->{
            File separated=null;
            try{
                if(editingPrepared!=null){
                    fillPreparedJob(editingPrepared,p,o);
                    PreparedJobStore.upsert(this,editingPrepared);
                    runOnUiThread(()->{
                        button.setEnabled(true);
                        button.setText("Änderungen gespeichert");
                        Snackbar.make(button,"Ausgang aktualisiert.",Snackbar.LENGTH_LONG).show();
                    });
                    return;
                }

                if(!combine&&working.size()>1){
                    int created=0;
                    for(OutboxItem item:working){
                        PreparedJob j=new PreparedJob();
                        fillPreparedJob(j,p,o);
                        j.name=item.name;
                        File persisted=PreparedJobStore.persistUri(this,item.asUri(),j.id);
                        j.filePath=persisted.getAbsolutePath();
                        j.inputNames.add(item.name);
                        if(item.deleteAfterSend)j.sourceUris.add(item.uri);
                        RectF keyArea=sourceRecipient.isEmpty()?AddressCorrectionProcessor.decode(p.recipientWindow):sourceRecipient;
                        j.recipientKey=AddressTextExtractor.recipientKey(this,persisted,keyArea);
                        PreparedJobStore.upsert(this,j);
                        created++;
                    }
                    OutboxStore.removeQueued(this,new ArrayList<>(working));
                    int finalCreated=created;
                    runOnUiThread(()->{
                        button.setEnabled(true);
                        button.setText("Im Ausgang gespeichert");
                        Snackbar.make(button,finalCreated+" einzelne Briefe liegen im Ausgang.",Snackbar.LENGTH_LONG).show();
                    });
                    return;
                }

                File sourceForQueue=merged;
                if(working.size()>1&&preserveSheets){
                    separated=PdfMergeUtil.merge(this,working,true);
                    sourceForQueue=separated;
                }

                PreparedJob j=new PreparedJob();
                fillPreparedJob(j,p,o);
                j.name=working.isEmpty()?"Vorbereiteter Brief":working.get(0).name+(working.size()>1?" +"+(working.size()-1):"");
                File persisted=PreparedJobStore.persistPdf(this,sourceForQueue,j.id);
                j.filePath=persisted.getAbsolutePath();
                for(OutboxItem item:working){
                    j.inputNames.add(item.name);
                    if(item.deleteAfterSend)j.sourceUris.add(item.uri);
                }
                RectF keyArea=sourceRecipient.isEmpty()?AddressCorrectionProcessor.decode(p.recipientWindow):sourceRecipient;
                j.recipientKey=AddressTextExtractor.recipientKey(this,persisted,keyArea);
                PreparedJobStore.upsert(this,j);
                OutboxStore.removeQueued(this,new ArrayList<>(working));
                editingPrepared=j;

                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setText("Im Ausgang gespeichert");
                    Snackbar.make(button,"Brief liegt im Ausgang.",Snackbar.LENGTH_LONG).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setText("Erneut speichern");
                    DebugUtil.error(this,button,"Ausgang speichern",e);
                });
            }finally{
                if(separated!=null)separated.delete();
            }
        },"prepare-outbox").start();
    }

    private File copyUriToTemp(OutboxItem item) throws Exception{
        File tmp=File.createTempFile("single-letter-",".pdf",getCacheDir());
        try(java.io.InputStream in=getContentResolver().openInputStream(item.asUri());
            java.io.FileOutputStream out=new java.io.FileOutputStream(tmp)){
            if(in==null)throw new IllegalStateException("PDF kann nicht gelesen werden: "+item.name);
            byte[] buf=new byte[64*1024];int n;
            while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }catch(Exception e){
            tmp.delete();
            throw e;
        }
        return tmp;
    }

    private void sendOne(File source,Profile p,JobOptions o,String debugExtra) throws Exception{
        File corrected=null;
        try{
            File outgoing=source;
            if(o.addressCorrection){
                corrected=AddressCorrectionProcessor.apply(this,source,sourceSender,sourceRecipient,targetSender,targetRecipient);
                outgoing=corrected;
            }
            if(DebugProfileManager.isDebug(p)){
                StringBuilder info=new StringBuilder();
                info.append("profile=").append(p.name).append('\n');
                info.append("provider=debug\n");
                info.append("sourceSender=").append(AddressCorrectionProcessor.encode(sourceSender)).append('\n');
                info.append("sourceRecipient=").append(AddressCorrectionProcessor.encode(sourceRecipient)).append('\n');
                info.append("targetSender=").append(AddressCorrectionProcessor.encode(targetSender)).append('\n');
                info.append("targetRecipient=").append(AddressCorrectionProcessor.encode(targetRecipient)).append('\n');
                if(debugExtra!=null)info.append(debugExtra);
                DebugSender.send(this,Uri.fromFile(outgoing),o,info.toString());
            }else{
                ProviderSender.send(this,Uri.fromFile(outgoing),p,o);
            }
        }finally{
            if(corrected!=null)corrected.delete();
        }
    }

    private void send(MaterialButton button){
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){DebugUtil.error(this,button,"Bitte ein kompatibles Profil wählen.");return;}
        if(merged==null||!merged.exists()){DebugUtil.error(this,button,"Die vorbereitete PDF fehlt.");return;}
        JobOptions o=currentOptions();

        if(o.addressCorrection&&!addressEdited){
            DebugUtil.error(this,button,"Öffne zuerst die große Adressvorschau und bestätige Original- und Zielbereiche.");
            return;
        }
        if(addressPreview.hasCollision()){
            DebugUtil.error(this,button,o.addressCorrection
                    ?"Die korrigierte Zielposition kollidiert noch mit dem Porto-/DV-Bereich. Öffne die Adressvorschau und verschiebe die Felder."
                    :"Adresslayout kollidiert mit dem Porto-/DV-Bereich. Aktiviere und prüfe die Adresskorrektur vor dem Versand.");
            return;
        }

        button.setEnabled(false);
        button.setText("Wird versendet…");

        PreparedJob preparedAtSend=editingPrepared;
        List<OutboxItem> sentItems=new ArrayList<>(working);
        boolean combine=mergeAsOne();
        boolean preserveSheets=preserveSheetBoundaries();

        new Thread(()->{
            File separated=null;
            try{
                int deleteFailures=0;

                if(preparedAtSend!=null){
                    sendOne(merged,p,o,"preparedJob="+preparedAtSend.id+"\n");
                    if(!DebugProfileManager.isDebug(p)){
                        deleteFailures=deletePreparedSources(preparedAtSend);
                        PreparedJobStore.delete(this,preparedAtSend.id);
                    }
                }else if(!combine&&sentItems.size()>1){
                    int index=0;
                    for(OutboxItem item:sentItems){
                        File tmp=copyUriToTemp(item);
                        try{
                            sendOne(tmp,p,o,"input["+index+"]="+item.name+"\nindividual=true\n");
                        }finally{
                            tmp.delete();
                        }
                        if(!DebugProfileManager.isDebug(p)){
                            deleteFailures+=OutboxStore.removeSent(this,java.util.Collections.singletonList(item));
                        }
                        index++;
                    }
                }else{
                    File source=merged;
                    if(sentItems.size()>1&&preserveSheets){
                        separated=PdfMergeUtil.merge(this,sentItems,true);
                        source=separated;
                    }
                    StringBuilder extra=new StringBuilder();
                    extra.append("combined=").append(sentItems.size()>1).append('\n');
                    for(int i=0;i<sentItems.size();i++)extra.append("input[").append(i).append("]=").append(sentItems.get(i).name).append('\n');
                    sendOne(source,p,o,extra.toString());
                    if(!DebugProfileManager.isDebug(p)){
                        deleteFailures=OutboxStore.removeSent(this,sentItems);
                    }
                }

                int finalDeleteFailures=deleteFailures;
                runOnUiThread(()->{
                    Snackbar.make(button,finalDeleteFailures==0?"Versand erfolgreich übergeben.":"Versand erfolgreich. Einige Quelldateien konnten nicht gelöscht werden.",Snackbar.LENGTH_LONG).show();
                    selectedIds.clear();
                    working.clear();
                    if(preparedAtSend!=null){finish();return;}
                    step=1;
                    if(merged!=null){merged.delete();merged=null;}
                    refreshItems();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setText("Erneut versenden");
                    DebugUtil.error(this,button,"Versand",e);
                });
            }finally{
                if(separated!=null)separated.delete();
            }
        },"outbox-send").start();
    }

    private int deletePreparedSources(PreparedJob job){
        int failures=0;
        for(String uri:job.sourceUris){
            try{
                androidx.documentfile.provider.DocumentFile d=androidx.documentfile.provider.DocumentFile.fromSingleUri(this,Uri.parse(uri));
                if(d==null||!d.delete())failures++;
            }catch(Exception e){failures++;}
        }
        return failures;
    }

    @Override protected void onDestroy(){
        if(merged!=null&&editingPrepared==null)merged.delete();
        if(previewBitmap!=null&&!previewBitmap.isRecycled())previewBitmap.recycle();
        if(addressPreview!=null)addressPreview.clearBitmap();
        super.onDestroy();
    }
}
