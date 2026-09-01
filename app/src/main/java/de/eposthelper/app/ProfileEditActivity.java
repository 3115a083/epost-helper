package de.eposthelper.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

public class ProfileEditActivity extends AppCompatActivity {
    private Profile profile;
    private EditText name,url,user,pass,pin,recipientWindow,senderWindow;
    private Spinner type,registered;
    private MaterialSwitch active,duplex,color;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?new Profile():SecureStore.find(this,id);
        if(profile==null) profile=new Profile();
        render();
    }

    private EditText field(String label,String value,boolean secret){
        EditText e=new EditText(this);
        e.setHint(label); e.setText(value); e.setTextSize(15);
        e.setSingleLine(true); e.setPadding(UiKit.dp(this,14),UiKit.dp(this,12),UiKit.dp(this,14),UiKit.dp(this,12));
        android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
        bg.setColor(UiKit.resolveSurface(this)); bg.setCornerRadius(UiKit.dp(this,18)); bg.setStroke(UiKit.dp(this,1),0x22000000);
        e.setBackground(bg);
        if(secret) e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void addField(LinearLayout root,EditText field){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56));
        lp.setMargins(0,UiKit.dp(this,6),0,UiKit.dp(this,6)); root.addView(field,lp);
    }

    private TextView label(String text){
        TextView t=UiKit.body(this,text); t.setTextSize(12); t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        t.setPadding(0,UiKit.dp(this,8),0,UiKit.dp(this,4)); return t;
    }

    private MaterialCardView section(String title,LinearLayout body){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        box.addView(UiKit.heading(this,title,17));
        box.addView(body,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return UiKit.surfaceCard(this,box);
    }

    private void render(){
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(UiKit.resolveSurface(this));

        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(UiKit.dp(this,10),UiKit.dp(this,10),UiKit.dp(this,16),UiKit.dp(this,10));
        TextView back=new TextView(this); back.setText("‹"); back.setTextSize(34); back.setGravity(Gravity.CENTER); back.setContentDescription("Zurück");
        back.setOnClickListener(v->finish()); top.addView(back,new LinearLayout.LayoutParams(UiKit.dp(this,54),UiKit.dp(this,54)));
        TextView title=UiKit.heading(this,getIntent().hasExtra("profileId")?"Profil bearbeiten":"Neues Profil",22);
        top.addView(title,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        page.addView(top);

        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this,18),0,UiKit.dp(this,18),UiKit.dp(this,28)); scroll.addView(root);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heroHead=new LinearLayout(this); heroHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon=UiKit.heroTitle(this,"▣",34); heroHead.addView(icon,new LinearLayout.LayoutParams(UiKit.dp(this,50),UiKit.dp(this,50)));
        LinearLayout heroText=new LinearLayout(this); heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.addView(UiKit.heroTitle(this,profile.name==null||profile.name.isBlank()?"Versandprofil":profile.name,20));
        heroText.addView(UiKit.heroBody(this,Profile.TYPE_IPP.equals(profile.type)?"E-POST Netzwerkdrucker":"E-POST Sammelkorb"));
        heroHead.addView(heroText,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
        TextView state=UiKit.pill(this,profile.active?"Aktiv":"Inaktiv",profile.active); heroHead.addView(state);
        hero.addView(heroHead);
        root.addView(UiKit.hero(this,hero,0xFF3657D7,0xFF1CC66B));

        LinearLayout connection=new LinearLayout(this); connection.setOrientation(LinearLayout.VERTICAL);
        name=field("Profilname",profile.name,false); addField(connection,name);
        connection.addView(label("Verbindungstyp"));
        type=new Spinner(this); String[] types={"Sammelkorb / WebDAV","Netzwerkdrucker / IPP"};
        type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));
        type.setSelection(Profile.TYPE_IPP.equals(profile.type)?1:0); connection.addView(type,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        url=field("HTTPS- oder IPPS-URL",profile.url,false); addField(connection,url);
        root.addView(section("Verbindung",connection));

        LinearLayout credentials=new LinearLayout(this); credentials.setOrientation(LinearLayout.VERTICAL);
        user=field("Benutzername",profile.username,false); addField(credentials,user);
        pass=field("Passwort",profile.password,true); addField(credentials,pass);
        pin=field("Optionaler Zertifikat-Pin, sha256/…",profile.certificatePin,false); addField(credentials,pin);
        TextView pinHelp=UiKit.body(this,"Pinning ist optional. Ohne Pin gelten weiterhin System-CA und Hostname-Prüfung."); pinHelp.setTextSize(12); credentials.addView(pinHelp);
        root.addView(section("Zugangsdaten & Sicherheit",credentials));

        LinearLayout options=new LinearLayout(this); options.setOrientation(LinearLayout.VERTICAL);
        active=new MaterialSwitch(this); active.setText("Profil aktiv"); active.setChecked(profile.active); options.addView(active);
        duplex=new MaterialSwitch(this); duplex.setText("Beidseitig"); duplex.setChecked(profile.duplex); options.addView(duplex);
        color=new MaterialSwitch(this); color.setText("Farbe"); color.setChecked(profile.color); options.addView(color);
        options.addView(label("Einschreiben"));
        registered=new Spinner(this); String[] regs={"Nein","Einschreiben","Einwurf","Rückschein"};
        registered.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,regs));
        int idx=0; for(int i=0;i<regs.length;i++) if(regs[i].equals(profile.registeredMail))idx=i;
        registered.setSelection(idx); options.addView(registered,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,52)));
        root.addView(section("Versandart",options));

        LinearLayout address=new LinearLayout(this); address.setOrientation(LinearLayout.VERTICAL);
        recipientWindow=field("Empfängerfenster, z. B. X+2 / Y-1 mm",profile.recipientWindow,false); addField(address,recipientWindow);
        senderWindow=field("Absenderfenster, z. B. X0 / Y+1 mm",profile.senderWindow,false); addField(address,senderWindow);
        TextView addressHelp=UiKit.body(this,"Diese Werte dokumentieren die serverseitige E-POST-Konfiguration. Das PDF selbst wird nicht verändert."); addressHelp.setTextSize(12); address.addView(addressHelp);
        root.addView(section("Adressfenster",address));

        MaterialButton save=UiKit.primary(this,"Profil speichern");
        save.setOnClickListener(v->save(save));
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(this,56)); slp.setMargins(0,UiKit.dp(this,14),0,UiKit.dp(this,8)); root.addView(save,slp);

        if(getIntent().hasExtra("profileId")){
            MaterialButton delete=UiKit.tonal(this,"Profil löschen");
            delete.setTextColor(0xFFB3261E); delete.setOnClickListener(v->delete(delete)); root.addView(delete);
        }

        page.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        setContentView(page);
    }

    private String text(EditText e){return e.getText()==null?"":e.getText().toString().trim();}

    private void collect(){
        profile.name=text(name);
        profile.type=type.getSelectedItemPosition()==1?Profile.TYPE_IPP:Profile.TYPE_WEBDAV;
        profile.url=text(url); profile.username=text(user); profile.password=text(pass); profile.certificatePin=text(pin);
        profile.active=active.isChecked(); profile.duplex=duplex.isChecked(); profile.color=color.isChecked();
        profile.registeredMail=String.valueOf(registered.getSelectedItem());
        profile.recipientWindow=text(recipientWindow); profile.senderWindow=text(senderWindow);
    }

    private void save(MaterialButton anchor){
        collect();
        if(profile.name.isBlank()){name.setError("Bitte einen Profilnamen eingeben.");return;}
        try{
            Sender.normalizeSecureUrl(profile.url);
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            boolean replaced=false;
            for(int i=0;i<list.size();i++){
                if(list.get(i).id.equals(profile.id)){list.set(i,profile);replaced=true;break;}
            }
            if(!replaced) list.add(profile);
            SecureStore.save(this,list); finish();
        }catch(Exception e){
            Snackbar.make(anchor,e.getMessage()==null?"Profil konnte nicht gespeichert werden.":e.getMessage(),Snackbar.LENGTH_LONG).show();
        }
    }

    private void delete(MaterialButton anchor){
        try{
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            for(int i=list.size()-1;i>=0;i--) if(list.get(i).id.equals(profile.id)) list.remove(i);
            SecureStore.save(this,list); finish();
        }catch(Exception e){
            Snackbar.make(anchor,"Profil konnte nicht gelöscht werden.",Snackbar.LENGTH_LONG).show();
        }
    }
}
