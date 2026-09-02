package de.eposthelper.app;

import android.os.Bundle;
import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class ProfileEditActivity extends AppCompatActivity {
    private Profile profile;
    private EditText name,url,user,pass,pin;
    private Spinner type,registered;
    private MaterialSwitch active,duplex,color;

    @Override protected void onCreate(Bundle b){
        SettingsStore.applySavedAppearance(this);
        super.onCreate(b);
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?new Profile():SecureStore.find(this,id);
        if(profile==null) profile=new Profile();
        render();
    }

    private EditText field(String label,String value,boolean secret){
        EditText e=new EditText(this);
        e.setHint(label); e.setText(value); e.setTextSize(15); e.setSingleLine(true);
        e.setPadding(UiKit.dp(this,16),UiKit.dp(this,12),UiKit.dp(this,16),UiKit.dp(this,12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(UiKit.resolveSurface(this));
        bg.setCornerRadius(UiKit.dp(this,18));
        bg.setStroke(UiKit.dp(this,1),androidx.core.graphics.ColorUtils.setAlphaComponent(UiKit.resolveSecondaryText(this),48));
        e.setBackground(bg);
        if(secret) e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void addField(LinearLayout root,EditText field){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,58));
        lp.setMargins(0,UiKit.dp(this,6),0,UiKit.dp(this,6));
        root.addView(field,lp);
    }

    private TextView label(String text){
        TextView t=UiKit.body(this,text);
        t.setTextSize(12);
        t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        t.setPadding(0,UiKit.dp(this,9),0,UiKit.dp(this,4));
        return t;
    }

    private MaterialCardView section(String title,String subtitle,LinearLayout body){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.heading(this,title,18));
        if(subtitle!=null&&!subtitle.isBlank()){
            TextView s=UiKit.body(this,subtitle); s.setTextSize(13); s.setPadding(0,UiKit.dp(this,4),0,UiKit.dp(this,8)); box.addView(s);
        }
        box.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return UiKit.surfaceCard(this,box);
    }

    private void render(){
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,8),UiKit.dp(this,8),UiKit.dp(this,16),UiKit.dp(this,8));
        TextView back=new TextView(this); back.setText("‹"); back.setTextSize(34); back.setGravity(Gravity.CENTER);
        back.setContentDescription("Zurück"); back.setOnClickListener(v->finish());
        top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        top.addView(UiKit.heading(this,getIntent().hasExtra("profileId")?"Profil bearbeiten":"Neues Profil",22),
                new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,30));
        scroll.addView(root);

        LinearLayout connection=new LinearLayout(this); connection.setOrientation(LinearLayout.VERTICAL);
        name=field("Profilname, z. B. Farbe beidseitig",profile.name,false); addField(connection,name);
        connection.addView(label("Einlieferungsweg"));
        type=new Spinner(this);
        type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Sammelkorb / WebDAV","Netzwerkdrucker / IPP"}));
        type.setSelection(Profile.TYPE_IPP.equals(profile.type)?1:0);
        connection.addView(type,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        url=field("Vom Administrator bereitgestellte URL",profile.url,false); addField(connection,url);
        root.addView(section("Verbindung","Die Ziel-URL wird im aktuellen E-POST-Portal bereitgestellt. Die App folgt der Authentifizierungs-Challenge des Servers.",connection));

        LinearLayout credentials=new LinearLayout(this); credentials.setOrientation(LinearLayout.VERTICAL);
        user=field("Benutzername",profile.username,false); addField(credentials,user);
        pass=field("WebDAV-Passwort",profile.password,true); addField(credentials,pass);
        pin=field("Optionaler SPKI-Pin, sha256/…",profile.certificatePin,false); addField(credentials,pin);
        Runnable updateAuthFields=()->{
            boolean ipp=type.getSelectedItemPosition()==1;
            pass.setVisibility(ipp?android.view.View.GONE:android.view.View.VISIBLE);
            user.setHint(ipp?"Optionaler IPP-Benutzername":"WebDAV-Benutzername");
        };
        type.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,android.view.View view,int position,long id){ updateAuthFields.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        updateAuthFields.run();
        TextView pinHelp=UiKit.body(this,"WebDAV nutzt Basic-Authentifizierung mit Benutzername und Passwort. Der Netzwerkdrucker wird über seine bereitgestellte URL angesprochen; ein optionaler Benutzername wird nur im IPP-Druckjob mitgesendet. TLS und Zertifikatsprüfung bleiben immer aktiv.");
        pinHelp.setTextSize(12); credentials.addView(pinHelp);
        root.addView(section("Zugang & Sicherheit","Zugangsdaten werden verschlüsselt im Android Keystore gespeichert.",credentials));

        LinearLayout options=new LinearLayout(this); options.setOrientation(LinearLayout.VERTICAL);
        active=new MaterialSwitch(this); active.setText("Profil aktiv"); active.setChecked(profile.active); options.addView(active);
        duplex=new MaterialSwitch(this); duplex.setText("Profil ist beidseitig"); duplex.setChecked(profile.duplex); options.addView(duplex);
        color=new MaterialSwitch(this); color.setText("Profil ist Farbdruck"); color.setChecked(profile.color); options.addView(color);
        options.addView(label("Zusatzleistung"));
        registered=new Spinner(this);
        String[] regs={"Nein","Einschreiben","Einschreiben Einwurf","Einschreiben Rückschein"};
        registered.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,regs));
        int idx=0; for(int i=0;i<regs.length;i++) if(regs[i].equals(profile.registeredMail))idx=i;
        registered.setSelection(idx);
        options.addView(registered,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        TextView optionHelp=UiKit.body(this,"Diese Merkmale dienen nur der Auswahl in Android. Die tatsächlichen Versandoptionen werden im E-POST-Ziel konfiguriert.");
        optionHelp.setTextSize(12); optionHelp.setPadding(0,UiKit.dp(this,8),0,0); options.addView(optionHelp);
        root.addView(section("Versandprofil","Farbe, Duplex und Einschreiben werden vom jeweiligen E-POST-Ziel bestimmt.",options));

        LinearLayout address=new LinearLayout(this); address.setOrientation(LinearLayout.VERTICAL);
        TextView addressText=UiKit.body(this,"E-POST MAILER korrigiert Empfänger- und Absenderbereiche über seine Adresswerkzeuge und gespeicherte Vorlagen. Die App verändert deshalb keine PDF-Koordinaten und erzeugt keine eigene, inkompatible Adressverschiebung.");
        addressText.setTextSize(13); address.addView(addressText);
        MaterialButton addressHelper=UiKit.tonal(this,"Versandfeld-Assistent öffnen");
        addressHelper.setOnClickListener(v->{
            collect();
            Intent i=new Intent(this,AddressConfigActivity.class);
            if(getIntent().hasExtra("profileId")) i.putExtra("profileId",profile.id);
            startActivity(i);
        });
        address.addView(addressHelper,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        root.addView(section("Adressfenster","Visuelle Korrekturvorlage nach dem E-POST-Ablauf.",address));

        MaterialButton test=UiKit.tonal(this,"Verbindung prüfen");
        test.setOnClickListener(v->testConnection(test));
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,54));
        tlp.setMargins(0,UiKit.dp(this,14),0,UiKit.dp(this,8)); root.addView(test,tlp);

        MaterialButton save=UiKit.primary(this,"Profil speichern");
        save.setOnClickListener(v->save(save));
        root.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)));

        if(getIntent().hasExtra("profileId")){
            MaterialButton delete=UiKit.tonal(this,"Profil löschen");
            delete.setTextColor(0xFFB3261E);
            delete.setOnClickListener(v->delete(delete));
            LinearLayout.LayoutParams dlp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52));
            dlp.setMargins(0,UiKit.dp(this,8),0,0); root.addView(delete,dlp);
        }

        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(page);
        SystemUi.apply(this,page);
    }

    private String text(EditText e){return e.getText()==null?"":e.getText().toString().trim();}

    private void collect(){
        profile.name=text(name);
        profile.type=type.getSelectedItemPosition()==1?Profile.TYPE_IPP:Profile.TYPE_WEBDAV;
        profile.url=text(url); profile.username=text(user);
        profile.password=Profile.TYPE_IPP.equals(profile.type)?"":text(pass);
        profile.certificatePin=text(pin);
        profile.active=active.isChecked(); profile.duplex=duplex.isChecked(); profile.color=color.isChecked();
        profile.registeredMail=String.valueOf(registered.getSelectedItem());
        
    }

    private boolean validate(){
        collect();
        if(profile.name.isBlank()){name.setError("Bitte einen Profilnamen eingeben.");return false;}
        try{Sender.normalizeSecureUrl(profile.url);return true;}
        catch(Exception e){url.setError(e.getMessage());return false;}
    }

    private void testConnection(MaterialButton anchor){
        if(!validate())return;
        anchor.setEnabled(false); anchor.setText("Prüfung läuft…");
        new Thread(()->{
            try{
                String result=ConnectionTester.test(profile);
                profile.connectionVerified=true;
                profile.connectionVerifiedAt=System.currentTimeMillis();
                profile.lastConnectionMessage=result;
                persistProfile();
                runOnUiThread(()->{
                    anchor.setEnabled(true); anchor.setText("Verbindung prüfen");
                    TextView tv=UiKit.mono(this,result); tv.setPadding(UiKit.dp(this,6),UiKit.dp(this,6),UiKit.dp(this,6),UiKit.dp(this,6));
                    new MaterialAlertDialogBuilder(this).setTitle("Verbindung erfolgreich").setView(tv).setPositiveButton("OK",null).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    anchor.setEnabled(true); anchor.setText("Verbindung prüfen");
                    profile.connectionVerified=false;
                    profile.connectionVerifiedAt=System.currentTimeMillis();
                    profile.lastConnectionMessage=e.getMessage()==null?"Unbekannter Fehler":e.getMessage();
                    try{persistProfile();}catch(Exception ignored){}
                    DebugUtil.error(this,anchor,"Verbindung prüfen",e);
                });
            }
        },"epost-connection-test").start();
    }

    private void persistProfile() throws Exception{
        List<Profile> list=new ArrayList<>(SecureStore.load(this));
        boolean replaced=false;
        for(int i=0;i<list.size();i++){
            if(list.get(i).id.equals(profile.id)){list.set(i,profile);replaced=true;break;}
        }
        if(!replaced) list.add(profile);
        SecureStore.save(this,list);
    }

    private void save(MaterialButton anchor){
        if(!validate())return;
        try{
            persistProfile(); finish();
        }catch(Exception e){
            DebugUtil.error(this,anchor,e.getMessage()==null?"Profil konnte nicht gespeichert werden.":e.getMessage());
        }
    }

    private void delete(MaterialButton anchor){
        try{
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            for(int i=list.size()-1;i>=0;i--) if(list.get(i).id.equals(profile.id)) list.remove(i);
            SecureStore.save(this,list); finish();
        }catch(Exception e){
            DebugUtil.error(this,anchor,"Profil konnte nicht gelöscht werden.");
        }
    }
}
