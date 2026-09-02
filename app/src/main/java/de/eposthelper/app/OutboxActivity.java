package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
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
    private LinearLayout root;
    private int step=1;
    private final Set<String> selectedIds=new HashSet<>();
    private List<OutboxItem> allItems=new ArrayList<>();
    private List<OutboxItem> working=new ArrayList<>();
    private File merged;
    private Bitmap previewBitmap;

    private String selectedProfileId;
    private MaterialSwitch color,duplex,localCorrection,c4;
    private Spinner registered;
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
    }

    @Override protected void onResume(){
        super.onResume();
        OutboxStore.importFolder(this);
        refreshItems();
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
            int pages=PdfMergeUtil.countPages(this,item.asUri());
            TextView meta=UiKit.body(this,pages+" Seite"+(pages==1?"":"n")+(item.deleteAfterSend?" · Auto-Import":""));
            meta.setTextSize(12);text.addView(meta);
            row.addView(text,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            root.addView(UiKit.surfaceCard(this,row));
        }

        MaterialButton next=UiKit.primary(this,"Auswahl weiter");
        next.setOnClickListener(v->{
            working.clear();
            for(OutboxItem i:allItems)if(selectedIds.contains(i.id))working.add(i);
            if(working.isEmpty()){DebugUtil.error(this,next,"Bitte mindestens eine PDF auswählen.");return;}
            step=2;prepareMerged(false);renderStep();
        });
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));nlp.setMargins(0,UiKit.dp(this,10),0,0);root.addView(next,nlp);
    }

    private void renderOrder(){
        stepHeader("2","Reihenfolge & Vorschau","Ordne die ausgewählten PDFs. Entfernen nimmt eine Datei nur aus diesem Versand, nicht aus dem Druckausgang.");

        LinearLayout previewCard=new LinearLayout(this);previewCard.setOrientation(LinearLayout.VERTICAL);
        previewCard.addView(UiKit.heading(this,"Erste Vorschau",17));
        ImageView preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setContentDescription("Vorschau der zusammengeführten PDF");
        if(previewBitmap!=null)preview.setImageBitmap(previewBitmap);
        previewCard.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,330)));
        int pages=merged==null?working.stream().mapToInt(i->PdfMergeUtil.countPages(this,i.asUri())).sum():PdfMergeUtil.countPages(merged);
        previewCard.addView(UiKit.body(this,working.size()+" PDF"+(working.size()==1?"":"s")+" · "+pages+" Seite"+(pages==1?"":"n")));
        root.addView(UiKit.surfaceCard(this,previewCard));

        for(int i=0;i<working.size();i++)root.addView(orderCard(i,working.get(i)));

        MaterialButton next=UiKit.primary(this,"Versand vorbereiten");
        next.setOnClickListener(v->{
            if(working.isEmpty()){DebugUtil.error(this,next,"Keine PDF für diesen Versand ausgewählt.");return;}
            prepareMerged(true);
        });
        LinearLayout.LayoutParams nlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));nlp.setMargins(0,UiKit.dp(this,10),0,0);root.addView(next,nlp);
    }

    private View orderCard(int index,OutboxItem item){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView number=UiKit.heading(this,String.valueOf(index+1),18);number.setGravity(Gravity.CENTER);
        titleRow.addView(number,new LinearLayout.LayoutParams(UiKit.dp(this,36),UiKit.dp(this,44)));
        TextView title=UiKit.heading(this,item.name,15);title.setMaxLines(2);
        titleRow.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        box.addView(titleRow);

        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER);
        MaterialButton up=UiKit.tonal(this,"Nach oben");up.setEnabled(index>0);
        up.setOnClickListener(v->{Collections.swap(working,index,index-1);prepareMerged(false);renderStep();});
        actions.addView(up,new LinearLayout.LayoutParams(0,UiKit.dp(this,46),1f));
        actions.addView(new View(this),new LinearLayout.LayoutParams(UiKit.dp(this,8),1));
        MaterialButton down=UiKit.tonal(this,"Nach unten");down.setEnabled(index<working.size()-1);
        down.setOnClickListener(v->{Collections.swap(working,index,index+1);prepareMerged(false);renderStep();});
        actions.addView(down,new LinearLayout.LayoutParams(0,UiKit.dp(this,46),1f));
        box.addView(actions);

        MaterialButton remove=UiKit.tonal(this,"Aus diesem Versand entfernen");
        remove.setOnClickListener(v->{working.remove(index);prepareMerged(false);renderStep();});
        LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,46));rlp.setMargins(0,UiKit.dp(this,8),0,0);box.addView(remove,rlp);
        return UiKit.surfaceCard(this,box);
    }

    private void prepareMerged(boolean advance){
        List<OutboxItem> snapshot=new ArrayList<>(working);
        new Thread(()->{
            try{
                File next=PdfMergeUtil.merge(this,snapshot);
                Bitmap bitmap=renderFirstPage(next,900);
                runOnUiThread(()->{
                    if(merged!=null&&merged.exists())merged.delete();
                    if(previewBitmap!=null&&!previewBitmap.isRecycled())previewBitmap.recycle();
                    merged=next;previewBitmap=bitmap;
                    if(advance){step=3;renderStep();}
                    else if(step==2)renderStep();
                });
            }catch(Exception e){
                runOnUiThread(()->DebugUtil.error(this,root,"PDFs verbinden",e));
            }
        },"outbox-merge").start();
    }

    private Bitmap renderFirstPage(File file,int width) throws Exception{
        try(ParcelFileDescriptor pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);
            PdfRenderer renderer=new PdfRenderer(pfd);
            PdfRenderer.Page page=renderer.openPage(0)){
            int height=Math.max(1,Math.round(width*(page.getHeight()/(float)page.getWidth())));
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        }
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
        c4=new MaterialSwitch(this);c4.setText("C4-Umschlag, ungefalzt");print.addView(c4);
        root.addView(UiKit.surfaceCard(this,print));

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
        addressBox.addView(addressPreview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,560)));
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
        localCorrection.setOnCheckedChangeListener((b,checked)->{
            Profile p=SecureStore.find(this,selectedProfileId);
            applyLayoutForProfile(p,checked);
            if(checked&&!addressEdited&&p!=null){
                editorRequestedBySwitch=true;
                openAddressEditor(b);
            }
        });

        refreshProfiles();

        MaterialButton send=UiKit.primary(this,"Brief jetzt versenden");
        send.setOnClickListener(v->send(send));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,58));slp.setMargins(0,UiKit.dp(this,12),0,0);root.addView(send,slp);
    }

    private TextView section(String text){
        TextView t=UiKit.heading(this,text,19);t.setPadding(0,UiKit.dp(this,14),0,UiKit.dp(this,5));return t;
    }

    private JobOptions currentOptions(){
        JobOptions o=new JobOptions();
        o.color=color!=null&&color.isChecked();
        o.duplex=duplex!=null&&duplex.isChecked();
        o.registered=registered==null?"Nein":String.valueOf(registered.getSelectedItem());
        o.c4=c4!=null&&c4.isChecked();
        return o;
    }

    private boolean compatible(Profile p,JobOptions o){
        if(!p.active)return false;
        if(Profile.PROVIDER_POST.equals(p.provider)){
            if(o.c4)return false;
            if(p.color!=o.color||p.duplex!=o.duplex)return false;
            String pr=p.registeredMail==null?"Nein":p.registeredMail;
            if(!pr.equals(o.registered))return false;
            return true;
        }
        if("Einschreiben Rückschein".equals(o.registered))return false;
        return true;
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
        int pages=merged==null?0:PdfMergeUtil.countPages(merged);
        boolean found=false;
        for(Profile p:profiles){
            if(!compatible(p,o))continue;
            found=true;
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView logo=new ImageView(this);logo.setImageResource(Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?R.drawable.ic_provider_lxp:R.drawable.ic_provider_post);
            row.addView(logo,new LinearLayout.LayoutParams(UiKit.dp(this,40),UiKit.dp(this,40)));
            RadioButton rb=new RadioButton(this);rb.setId(View.generateViewId());rb.setTag(p.id);
            rb.setText(p.name);rb.setTextSize(16);
            rb.setChecked(p.id.equals(selectedProfileId));
            profileButtons.add(rb);
            row.addView(rb,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
            card.addView(row);

            TextView route=UiKit.body(this,Profile.PROVIDER_LETTERXPRESS.equals(p.provider)?"LetterXpress · "+(Profile.TYPE_LXP_API.equals(p.type)?"API":"SFTP"):"Deutsche Post · "+(Profile.TYPE_IPP.equals(p.type)?"IPP":"WebDAV"));
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
                targetSender=AddressLayoutRules.targetSender(p,o);
                targetRecipient=AddressLayoutRules.targetRecipient(p,o);
            }
            addressPreview.setBoxes(targetSender,targetRecipient);
            addressPreview.setInteractive(false);
        }else{
            addressPreview.setBoxes(sourceSender,sourceRecipient);
            addressPreview.setInteractive(false);
        }

        RectF reserved=AddressLayoutRules.reserved(p,o);
        addressPreview.setReservedArea(reserved,reserved.isEmpty()?null:"Reserviert für Frankierung");

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
            targetSender=AddressLayoutRules.targetSender(p,currentOptions());
            targetRecipient=AddressLayoutRules.targetRecipient(p,currentOptions());
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
        if(addressPreview.hasCollision()){
            layoutHint.setText("Adressbereich kollidiert mit einer reservierten Zone. Öffne „Groß bearbeiten“ und verschiebe die Zielposition.");
        }else if(localCorrection.isChecked()){
            layoutHint.setText(addressEdited
                    ?"Adresskorrektur aktiv. Original- und Zielbereiche wurden für diesen Brief bestätigt."
                    :"Adresskorrektur aktiv. Öffne „Groß bearbeiten“, um die Originalbereiche dieses Briefes zu prüfen.");
        }else{
            layoutHint.setText("Adresskorrektur ist aus. Über „Groß bearbeiten“ kannst du Original- und Zielbereiche in einer großen Vorschau prüfen.");
        }
    }

    private void send(MaterialButton button){
        Profile p=SecureStore.find(this,selectedProfileId);
        if(p==null){DebugUtil.error(this,button,"Bitte ein kompatibles Profil wählen.");return;}
        if(merged==null||!merged.exists()){DebugUtil.error(this,button,"Die zusammengeführte PDF fehlt.");return;}
        JobOptions o=currentOptions();

        boolean doCorrection=localCorrection.isChecked();
        if(doCorrection&&!addressEdited){
            DebugUtil.error(this,button,"Öffne zuerst die große Adressvorschau und bestätige Original- und Zielbereiche.");
            return;
        }
        if(addressPreview.hasCollision()&&!doCorrection){
            DebugUtil.error(this,button,"Adresslayout kollidiert mit einem reservierten Bereich. Korrigiere die Position vor dem Versand.");
            return;
        }

        button.setEnabled(false);button.setText("Wird versendet…");
        File source=merged;
        List<OutboxItem> sentItems=new ArrayList<>(working);
        new Thread(()->{
            File corrected=null;
            try{
                File outgoing=source;
                if(doCorrection){
                    corrected=AddressCorrectionProcessor.apply(this,source,sourceSender,sourceRecipient,targetSender,targetRecipient);
                    outgoing=corrected;
                }
                ProviderSender.send(this,Uri.fromFile(outgoing),p,o);
                int deleteFailures=OutboxStore.removeSent(this,sentItems);
                File finalCorrected=corrected;
                runOnUiThread(()->{
                    if(finalCorrected!=null)finalCorrected.delete();
                    Snackbar.make(button,deleteFailures==0?"Versand erfolgreich übergeben.":"Versand erfolgreich. Einige Auto-Import-Dateien konnten nicht gelöscht werden und werden nicht erneut importiert.",Snackbar.LENGTH_LONG).show();
                    selectedIds.clear();working.clear();step=1;
                    if(merged!=null){merged.delete();merged=null;}
                    refreshItems();
                });
            }catch(Exception e){
                if(corrected!=null)corrected.delete();
                runOnUiThread(()->{button.setEnabled(true);button.setText("Erneut versenden");DebugUtil.error(this,button,"Versand",e);});
            }
        },"outbox-send").start();
    }

    @Override protected void onDestroy(){
        if(merged!=null)merged.delete();
        if(previewBitmap!=null&&!previewBitmap.isRecycled())previewBitmap.recycle();
        if(addressPreview!=null)addressPreview.clearBitmap();
        super.onDestroy();
    }
}
