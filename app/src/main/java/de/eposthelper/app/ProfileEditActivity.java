package de.eposthelper.app;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

public class ProfileEditActivity extends AppCompatActivity {
    private Profile profile;
    private TextInputEditText name,url,user,pass,pin,recipientWindow,senderWindow;
    private Spinner type,registered;
    private MaterialSwitch active,duplex,color;

    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        String id=getIntent().getStringExtra("profileId");
        profile=id==null?new Profile():SecureStore.find(this,id);
        if(profile==null) profile=new Profile();
        render();
    }

    private TextInputEditText field(LinearLayout root,String label,String value){
        TextInputLayout til=new TextInputLayout(this);
        til.setHint(label); til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setBoxCornerRadii(dp(18),dp(18),dp(18),dp(18));
        TextInputEditText e=new TextInputEditText(this); e.setText(value);
        til.addView(e,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(8),0,dp(8)); root.addView(til,lp); return e;
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(18),dp(18),dp(24)); scroll.addView(root);
        name=field(root,"Profilname",profile.name);
        type=new Spinner(this); String[] types={"Sammelkorb / WebDAV","Netzwerkdrucker / IPP"};
        type.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));
        type.setSelection(Profile.TYPE_IPP.equals(profile.type)?1:0); root.addView(type);
        url=field(root,"HTTPS-/IPPS-URL",profile.url);
        user=field(root,"Benutzername",profile.username);
        pass=field(root,"Passwort",profile.password); pass.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pin=field(root,"Optionaler Zertifikat-Pin, sha256/…",profile.certificatePin);
        active=new MaterialSwitch(this); active.setText("Profil aktiv"); active.setChecked(profile.active); root.addView(active);
        duplex=new MaterialSwitch(this); duplex.setText("Serverprofil: beidseitig"); duplex.setChecked(profile.duplex); root.addView(duplex);
        color=new MaterialSwitch(this); color.setText("Serverprofil: Farbe"); color.setChecked(profile.color); root.addView(color);
        registered=new Spinner(this); String[] regs={"Nein","Einschreiben","Einwurf","Rückschein"};
        registered.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,regs));
        int idx=0; for(int i=0;i<regs.length;i++) if(regs[i].equals(profile.registeredMail)) idx=i;
        registered.setSelection(idx); root.addView(registered);
        recipientWindow=field(root,"Empfängerfenster-Korrektur, z. B. X+2/Y-1 mm",profile.recipientWindow);
        senderWindow=field(root,"Absenderfenster-Korrektur, z. B. X0/Y+1 mm",profile.senderWindow);
        MaterialButton save=new MaterialButton(this); save.setText("Profil speichern"); save.setOnClickListener(v->save()); root.addView(save);
        if(getIntent().getStringExtra("profileId")!=null){
            MaterialButton delete=new MaterialButton(this); delete.setText("Profil löschen"); delete.setOnClickListener(v->delete()); root.addView(delete);
        }
        setContentView(scroll);
    }

    private String text(TextInputEditText e){return e.getText()==null?"":e.getText().toString().trim();}

    private void collect(){
        profile.name=text(name); profile.type=type.getSelectedItemPosition()==1?Profile.TYPE_IPP:Profile.TYPE_WEBDAV;
        profile.url=text(url); profile.username=text(user); profile.password=text(pass); profile.certificatePin=text(pin);
        profile.active=active.isChecked(); profile.duplex=duplex.isChecked(); profile.color=color.isChecked();
        profile.registeredMail=String.valueOf(registered.getSelectedItem());
        profile.recipientWindow=text(recipientWindow); profile.senderWindow=text(senderWindow);
    }

    private void save(){
        collect();
        try{
            Sender.normalizeSecureUrl(profile.url);
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            boolean replaced=false;
            for(int i=0;i<list.size();i++) if(list.get(i).id.equals(profile.id)){list.set(i,profile);replaced=true;break;}
            if(!replaced) list.add(profile);
            SecureStore.save(this,list); finish();
        }catch(Exception e){url.setError(e.getMessage());}
    }

    private void delete(){
        try{
            List<Profile> list=new ArrayList<>(SecureStore.load(this));
            list.removeIf(p->p.id.equals(profile.id)); SecureStore.save(this,list); finish();
        }catch(Exception ignored){}
    }
}
