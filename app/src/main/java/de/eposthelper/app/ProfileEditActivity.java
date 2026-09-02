package de.eposthelper.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class ProfileEditActivity extends AppCompatActivity {
    private Profile profile;
    private EditText name,url,user,secret,pin,sshKey;
    private Spinner provider,type,registered;
    private MaterialSwitch active,duplex,color;
    private LinearLayout credentials;
    private TextView routeHelp;

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        SettingsStore.applyDynamicColors(this);
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?new Profile():SecureStore.find(this,id);
        if(profile==null)profile=new Profile();
        render();
    }

    private EditText field(String hint,String value,boolean password){
        EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setTextSize(15);e.setSingleLine(true);
        e.setPadding(UiKit.dp(this,16),UiKit.dp(this,12),UiKit.dp(this,16),UiKit.dp(this,12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(UiKit.resolveSurface(this));bg.setCornerRadius(UiKit.dp(this,18));
        bg.setStroke(UiKit.dp(this,1),androidx.core.graphics.ColorUtils.setAlphaComponent(UiKit.resolveSecondaryText(this),48));e.setBackground(bg);
        if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void addField(LinearLayout root,EditText e){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,58));
        lp.setMargins(0,UiKit.dp(this,5),0,UiKit.dp(this,5));root.addView(e,lp);
    }

    private LinearLayout cardBody(String title,String help){
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);
        b.addView(UiKit.heading(this,title,18));
        if(help!=null&&!help.isBlank()){
            TextView h=UiKit.body(this,help);h.setTextSize(13);h.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8));b.addView(h);
        }
        return b;
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setBackgroundColor(UiKit.resolveSurface(this));
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=UiKit.heading(this,"‹",34);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->getOnBackPressedDispatcher().onBackPressed());
        bar.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        bar.addView(UiKit.heading(this,getIntent().hasExtra("profileId")?"Profil bearbeiten":"Neues Profil",22),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(bar);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28));scroll.addView(root);

        LinearLayout basic=cardBody("Anbieter & Profil","Ein Profil ist ein Zugang zu einem bestehenden Briefdienst. Versandoptionen können später direkt im Android-Druckdialog angepasst werden.");
        LinearLayout providerRow=new LinearLayout(this);providerRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);logo.setContentDescription("Anbieterlogo");providerRow.addView(logo,new LinearLayout.LayoutParams(UiKit.dp(this,46),UiKit.dp(this,46)));
        provider=new Spinner(this);provider.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Deutsche Post","LetterXpress"}));
        provider.setSelection(Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)?1:0);
        providerRow.addView(provider,new LinearLayout.LayoutParams(0,UiKit.dp(this,54),1f));basic.addView(providerRow);
        name=field("Profilname",profile.name,false);addField(basic,name);
        active=new MaterialSwitch(this);active.setText("Profil aktiv");active.setChecked(profile.active);basic.addView(active);
        root.addView(UiKit.surfaceCard(this,basic));

        LinearLayout connection=cardBody("Verbindung","");
        type=new Spinner(this);connection.addView(type,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)));
        routeHelp=UiKit.body(this,"");routeHelp.setTextSize(13);connection.addView(routeHelp);
        url=field("Ziel-URL",profile.url,false);addField(connection,url);
        root.addView(UiKit.surfaceCard(this,connection));

        credentials=cardBody("Zugangsdaten","");
        user=field("Benutzername",profile.username,false);addField(credentials,user);
        secret=field("Passwort / API-Key",Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)&&Profile.TYPE_LXP_API.equals(profile.type)?profile.apiKey:profile.password,true);addField(credentials,secret);
        pin=field("Optionaler HTTPS-SPKI-Pin, sha256/…",profile.certificatePin,false);addField(credentials,pin);
        sshKey=field("SFTP Host-Key-Fingerprint",profile.sshHostKey,false);addField(credentials,sshKey);
        root.addView(UiKit.surfaceCard(this,credentials));

        LinearLayout defaults=cardBody("Standard-Versand","Diese Werte sind Startwerte. Im Android-Druckdialog kannst du sie pro Brief überschreiben.");
        duplex=new MaterialSwitch(this);duplex.setText("Doppelseitig");duplex.setChecked(profile.duplex);defaults.addView(duplex);
        color=new MaterialSwitch(this);color.setText("Farbe");color.setChecked(profile.color);defaults.addView(color);
        registered=new Spinner(this);defaults.addView(UiKit.body(this,"Einschreiben"));
        registered.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Nein","Einschreiben Einwurf","Einschreiben","Einschreiben Rückschein"}));
        int ri=0;for(int i=0;i<registered.getCount();i++)if(registered.getItemAtPosition(i).toString().equals(profile.registeredMail))ri=i;registered.setSelection(ri);
        defaults.addView(registered,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        TextView addressHelp=UiKit.body(this,"Optional: Quellbereiche für Absender und Empfänger am eigenen Brieflayout speichern. Die automatische PDF-Transformation folgt in einem separaten Schritt.");
        addressHelp.setTextSize(13);addressHelp.setPadding(0,UiKit.dp(this,8),0,UiKit.dp(this,8));defaults.addView(addressHelp);
        MaterialButton helper=UiKit.tonal(this,"Adressbereiche konfigurieren");
        helper.setOnClickListener(v->{collect();saveQuiet();Intent i=new Intent(this,AddressConfigActivity.class);i.putExtra("profileId",profile.id);startActivity(i);});
        defaults.addView(helper,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        root.addView(UiKit.surfaceCard(this,defaults));

        MaterialButton test=UiKit.tonal(this,"Verbindung prüfen");test.setOnClickListener(v->test(test));
        root.addView(test,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54)));
        MaterialButton save=UiKit.primary(this,"Profil speichern");save.setOnClickListener(v->save(save));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));slp.setMargins(0,UiKit.dp(this,8),0,0);root.addView(save,slp);
        if(getIntent().hasExtra("profileId")){
            MaterialButton del=UiKit.tonal(this,"Profil löschen");del.setTextColor(0xFFB3261E);del.setOnClickListener(v->delete(del));root.addView(del);
        }

        AdapterView.OnItemSelectedListener update=new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){updateProviderUi(logo);}
            public void onNothingSelected(AdapterView<?> p){}
        };
        provider.setOnItemSelectedListener(update);
        type.setOnItemSelectedListener(update);
        updateProviderUi(logo);

        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(page);SystemUi.apply(this,page);
    }

    private void updateProviderUi(ImageView logo){
        boolean lxp=provider.getSelectedItemPosition()==1;
        logo.setImageResource(lxp?R.drawable.ic_provider_lxp:R.drawable.ic_provider_post);
        String current=type.getSelectedItem()==null?profile.type:String.valueOf(type.getSelectedItem());
        String[] types=lxp?new String[]{"LetterXpress API","LetterXpress SFTP"}:new String[]{"Sammelkorb / WebDAV","Netzwerkdrucker / IPP"};
        if(type.getAdapter()==null||type.getCount()!=2||((String)type.getItemAtPosition(0)).startsWith("LetterXpress")!=lxp){
            type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));
            int idx=0;
            if(lxp&&Profile.TYPE_LXP_SFTP.equals(profile.type))idx=1;
            if(!lxp&&Profile.TYPE_IPP.equals(profile.type))idx=1;
            type.setSelection(idx);
        }
        int route=type.getSelectedItemPosition();
        boolean api=lxp&&route==0,sftp=lxp&&route==1,ipp=!lxp&&route==1;
        url.setVisibility(lxp?View.GONE:View.VISIBLE);
        if(!lxp)url.setHint(ipp?"IPPS-/HTTPS-Drucker-URL":"WebDAV-HTTPS-URL");
        user.setHint(api?"LetterXpress Benutzername":sftp?"SFTP Benutzername":ipp?"Optionaler IPP-Benutzername":"WebDAV Benutzername");
        secret.setHint(api?"LetterXpress API-Key":sftp?"SFTP Passwort":"WebDAV Passwort");
        secret.setVisibility(ipp?View.GONE:View.VISIBLE);
        pin.setVisibility(sftp?View.GONE:View.VISIBLE);
        sshKey.setVisibility(sftp?View.VISIBLE:View.GONE);
        routeHelp.setText(api?"REST API v3. Unterstützt Preisabfrage und Testmodus."
                :sftp?"SFTP/SSH auf sftp.letterxpress.de:279. Versandoptionen werden per FILECODE übertragen."
                :ipp?"Automatischer E-POST-Netzwerkdrucker."
                :"E-POST Sammelkorb über WebDAV.");
    }

    private String text(EditText e){return e.getText()==null?"":e.getText().toString().trim();}

    private void collect(){
        boolean lxp=provider.getSelectedItemPosition()==1;
        profile.provider=lxp?Profile.PROVIDER_LETTERXPRESS:Profile.PROVIDER_POST;
        int route=type.getSelectedItemPosition();
        profile.type=lxp?(route==0?Profile.TYPE_LXP_API:Profile.TYPE_LXP_SFTP):(route==0?Profile.TYPE_WEBDAV:Profile.TYPE_IPP);
        profile.name=text(name);profile.active=active.isChecked();
        profile.url=lxp?(Profile.TYPE_LXP_API.equals(profile.type)?"https://api.letterxpress.de/v3":"sftp://sftp.letterxpress.de:279"):text(url);
        profile.username=text(user);
        if(Profile.TYPE_LXP_API.equals(profile.type)){profile.apiKey=text(secret);profile.password="";}
        else{profile.password=text(secret);}
        profile.certificatePin=text(pin);profile.sshHostKey=text(sshKey);
        profile.duplex=duplex.isChecked();profile.color=color.isChecked();profile.registeredMail=String.valueOf(registered.getSelectedItem());

    }

    private void persist() throws Exception{
        List<Profile> list=new ArrayList<>(SecureStore.load(this));boolean found=false;
        for(int i=0;i<list.size();i++)if(list.get(i).id.equals(profile.id)){list.set(i,profile);found=true;break;}
        if(!found)list.add(profile);SecureStore.save(this,list);
    }
    private void saveQuiet(){try{persist();}catch(Exception ignored){}}

    private void test(MaterialButton anchor){
        collect();anchor.setEnabled(false);anchor.setText("Prüfung läuft…");
        new Thread(()->{
            try{
                String result;
                if(Profile.PROVIDER_LETTERXPRESS.equals(profile.provider)){
                    if(Profile.TYPE_LXP_API.equals(profile.type))result=LetterXpressApiClient.test(profile);
                    else{
                        if(profile.sshHostKey.isBlank()){
                            String fp=LetterXpressSftpClient.discoverFingerprint(profile);
                            runOnUiThread(()->{
                                sshKey.setText(fp);
                                anchor.setEnabled(true);
                                anchor.setText("Verbindung prüfen");
                                android.widget.Toast.makeText(this,"SSH-Fingerprint ermittelt. Bitte prüfen und Profil speichern, danach erneut testen.",android.widget.Toast.LENGTH_LONG).show();
                            });
                            return;
                        }
                        result=LetterXpressSftpClient.test(profile);
                    }
                }else result=ConnectionTester.test(profile);
                profile.connectionVerified=true;profile.lastConnectionMessage=result;profile.connectionVerifiedAt=System.currentTimeMillis();persist();
                runOnUiThread(()->{anchor.setEnabled(true);anchor.setText("Verbindung prüfen");android.widget.Toast.makeText(this,result,android.widget.Toast.LENGTH_LONG).show();});
            }catch(Exception e){
                profile.connectionVerified=false;profile.lastConnectionMessage=e.getMessage();profile.connectionVerifiedAt=System.currentTimeMillis();
                try{persist();}catch(Exception ignored){}
                runOnUiThread(()->{anchor.setEnabled(true);anchor.setText("Verbindung prüfen");DebugUtil.error(this,anchor,"Verbindung prüfen",e);});
            }
        },"provider-test").start();
    }

    private void save(MaterialButton anchor){
        collect();
        if(profile.name.isBlank()){name.setError("Profilname fehlt");return;}
        try{persist();finish();}catch(Exception e){DebugUtil.error(this,anchor,"Profil speichern",e);}
    }

    private void delete(MaterialButton anchor){
        try{
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            for(int i=list.size()-1;i>=0;i--)if(list.get(i).id.equals(profile.id))list.remove(i);
            SecureStore.save(this,list);finish();
        }catch(Exception e){DebugUtil.error(this,anchor,"Profil löschen",e);}
    }
}
