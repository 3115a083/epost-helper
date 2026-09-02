package de.eposthelper.app;

import android.content.Intent;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentInfo;
import android.print.PrintJobInfo;
import android.print.PrinterInfo;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class AdvancedPrintOptionsActivity extends AppCompatActivity {
    public static final String OPT_REGISTERED="epost.registered";
    public static final String OPT_C4="epost.c4";

    private Profile profile;
    private PrintJobInfo jobInfo;
    private PrintDocumentInfo documentInfo;
    private MaterialSwitch c4;
    private Spinner registered;
    private TextView price;

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        jobInfo=getIntent().getParcelableExtra(android.printservice.PrintService.EXTRA_PRINT_JOB_INFO);
        PrinterInfo printer=getIntent().getParcelableExtra(android.printservice.PrintService.EXTRA_PRINTER_INFO);
        documentInfo=getIntent().getParcelableExtra(android.printservice.PrintService.EXTRA_PRINT_DOCUMENT_INFO);
        if(jobInfo==null||printer==null){finish();return;}
        profile=SecureStore.find(this,printer.getId().getLocalId());
        if(profile==null){finish();return;}
        render();
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,18),UiKit.dp(this,22));
        page.setBackgroundColor(UiKit.resolveSurface(this));

        page.addView(UiKit.heading(this,"Zusätzliche Versandoptionen",23));
        TextView provider=UiKit.body(this,(Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)?"LetterXpress":"Deutsche Post")+" · "+profile.name);
        provider.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,10));page.addView(provider);

        PrintAttributes attrs=jobInfo.getAttributes();
        String standard="";
        if(attrs!=null){
            standard=(attrs.getColorMode()==PrintAttributes.COLOR_MODE_COLOR?"Farbe":"Schwarzweiß")+" · "+
                    (attrs.getDuplexMode()==PrintAttributes.DUPLEX_MODE_NONE?"Einseitig":"Doppelseitig");
        }
        TextView inherited=UiKit.body(this,"Farbe und Duplex werden direkt aus dem Android-Druckdialog übernommen"+(standard.isBlank()?".":": "+standard+"."));
        inherited.setTextSize(13);inherited.setPadding(0,0,0,UiKit.dp(this,10));page.addView(inherited);

        LinearLayout opts=new LinearLayout(this);opts.setOrientation(LinearLayout.VERTICAL);
        TextView regLabel=UiKit.body(this,"Einschreiben");regLabel.setPadding(0,UiKit.dp(this,8),0,UiKit.dp(this,4));opts.addView(regLabel);
        registered=new Spinner(this);
        String[] regs=Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)
                ?new String[]{"Nein","Einschreiben Einwurf","Einschreiben"}
                :new String[]{"Nein","Einschreiben","Einschreiben Einwurf","Einschreiben Rückschein"};
        registered.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,regs));
        int ri=0;for(int i=0;i<regs.length;i++)if(regs[i].equals(profile.registeredMail))ri=i;registered.setSelection(ri);
        opts.addView(registered,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));

        c4=new MaterialSwitch(this);c4.setText("C4-Umschlag, ungefalzt");
        c4.setVisibility(Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)?View.VISIBLE:View.GONE);opts.addView(c4);
        page.addView(UiKit.surfaceCard(this,opts));

        price=UiKit.heading(this,"",18);price.setPadding(0,UiKit.dp(this,10),0,UiKit.dp(this,10));page.addView(price);
        c4.setOnClickListener(v->refreshPrice());
        registered.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){refreshPrice();}
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });

        MaterialButton apply=UiKit.primary(this,"Übernehmen");apply.setOnClickListener(v->apply());
        page.addView(apply,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)));

        setContentView(page);SystemUi.apply(this,page);refreshPrice();
    }

    private JobOptions options(){
        JobOptions o=JobOptions.fromProfile(profile);
        PrintAttributes attrs=jobInfo.getAttributes();
        if(attrs!=null){
            o.color=attrs.getColorMode()==PrintAttributes.COLOR_MODE_COLOR;
            o.duplex=attrs.getDuplexMode()!=0&&attrs.getDuplexMode()!=PrintAttributes.DUPLEX_MODE_NONE;
        }
        o.registered=String.valueOf(registered.getSelectedItem());o.c4=c4.isChecked();
        return o;
    }

    private void refreshPrice(){
        if(!Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)){price.setText("");return;}
        int pages=documentInfo==null?0:documentInfo.getPageCount();
        if(pages<=0){price.setText("Preis nach Seitenanalyse verfügbar");return;}
        JobOptions o=options();
        if(Profile.TYPE_LXP_SFTP.equals(profile.type)){
            double estimate=LetterXpressPriceEstimator.gross(o,pages);
            price.setText(estimate>=0?String.format(java.util.Locale.GERMANY,"Ca. %.2f € brutto · Listenpreis",estimate):"Preis nicht verfügbar");
            return;
        }
        price.setText("Preis wird berechnet…");
        new Thread(()->{
            try{
                double p=LetterXpressApiClient.price(profile,o,pages);
                runOnUiThread(()->price.setText(p>=0?String.format(java.util.Locale.GERMANY,"Voraussichtlich %.2f €",p):"Preis nicht verfügbar"));
            }catch(Exception e){
                runOnUiThread(()->price.setText("Preis nicht verfügbar"));
            }
        },"lxp-price").start();
    }

    private void apply(){
        PrintJobInfo.Builder b=new PrintJobInfo.Builder(jobInfo);
        b.putAdvancedOption(OPT_REGISTERED,String.valueOf(registered.getSelectedItem()));
        b.putAdvancedOption(OPT_C4,c4.isChecked()?1:0);
        Intent result=new Intent();result.putExtra(android.printservice.PrintService.EXTRA_PRINT_JOB_INFO,b.build());
        setResult(RESULT_OK,result);finish();
    }
}
