package de.eposthelper.app;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private LinearLayout root;
    private Uri selectedPdf;
    private String selectedProfileId;
    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (SecurityException ignored) {}
                    selectedPdf = uri; renderSend();
                }
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getData() != null) selectedPdf = getIntent().getData();
        buildShell(); renderSend();
    }

    @Override protected void onResume() {
        super.onResume();
        if (root != null) renderSend();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void buildShell() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);

        TextView bar = new TextView(this);
        bar.setText("E-POST Helper\nPDF sicher als Brief versenden");
        bar.setTextSize(22); bar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.setPadding(dp(18), dp(18), dp(18), dp(12));
        page.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78)));

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(28));
        scroll.addView(root);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(12), dp(8), dp(12), dp(8));
        MaterialButton send = new MaterialButton(this); send.setText("Senden"); send.setOnClickListener(v -> renderSend());
        MaterialButton profiles = new MaterialButton(this); profiles.setText("Profile"); profiles.setOnClickListener(v -> renderProfiles());
        MaterialButton security = new MaterialButton(this); security.setText("Sicherheit"); security.setOnClickListener(v -> renderSecurity());
        nav.addView(send, new LinearLayout.LayoutParams(0, dp(48), 1f));
        nav.addView(profiles, new LinearLayout.LayoutParams(0, dp(48), 1f));
        nav.addView(security, new LinearLayout.LayoutParams(0, dp(48), 1f));
        page.addView(nav);
        setContentView(page);
    }

    private TextView title(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(22); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(8), 0, dp(8)); return t;
    }

    private TextView body(String text) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(15); t.setLineSpacing(0,1.15f); return t;
    }

    private MaterialCardView card(View content) {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(dp(24)); c.setCardElevation(0); c.setStrokeWidth(0);
        c.setContentPadding(dp(18), dp(18), dp(18), dp(18)); c.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,dp(8),0,dp(8)); c.setLayoutParams(lp); return c;
    }

    private void renderSend() {
        root.removeAllViews();
        LinearLayout hero = new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL);
        hero.addView(title("Bereit zum Senden"));
        hero.addView(body("HTTPS/IPPS erzwungen. Zugangsdaten liegen AES-256-GCM-verschlüsselt im Android Keystore."));
        root.addView(card(hero));

        root.addView(title("Dokument"));
        MaterialButton choose = new MaterialButton(this);
        choose.setText(selectedPdf == null ? "PDF auswählen" : "PDF ausgewählt: " + selectedPdf.getLastPathSegment());
        choose.setOnClickListener(v -> picker.launch(new String[]{"application/pdf"}));
        root.addView(choose);

        List<Profile> profiles = SecureStore.load(this);
        if (profiles.isEmpty()) {
            root.addView(card(body("Noch kein Versandprofil vorhanden. Lege zuerst einen Sammelkorb oder IPP-Netzwerkdrucker an.")));
            MaterialButton add = new MaterialButton(this); add.setText("Erstes Profil anlegen");
            add.setOnClickListener(v -> startActivity(new Intent(this, ProfileEditActivity.class))); root.addView(add); return;
        }

        root.addView(title("Versandprofil"));
        RadioGroup group = new RadioGroup(this);
        for (Profile p : profiles) {
            if (!p.active) continue;
            RadioButton rb = new RadioButton(this);
            rb.setText(p.name + "\n" + (Profile.TYPE_IPP.equals(p.type) ? "IPP-Netzwerkdrucker" : "Sammelkorb/WebDAV") +
                    " · " + (p.duplex ? "beidseitig" : "einseitig") + " · " + (p.color ? "Farbe" : "S/W") + " · Einschreiben: " + p.registeredMail);
            rb.setTag(p.id); rb.setPadding(dp(8),dp(8),dp(8),dp(8)); group.addView(rb);
            if (selectedProfileId == null) selectedProfileId = p.id;
            rb.setChecked(p.id.equals(selectedProfileId));
        }
        group.setOnCheckedChangeListener((g,id) -> { View v=g.findViewById(id); if (v != null) selectedProfileId=String.valueOf(v.getTag()); });
        root.addView(card(group));

        MaterialButton send = new MaterialButton(this); send.setText("Jetzt senden"); send.setOnClickListener(v -> sendSelected(send)); root.addView(send);
        MaterialButton enablePrinter = new MaterialButton(this); enablePrinter.setText("Android-Druckdienst aktivieren");
        enablePrinter.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS))); root.addView(enablePrinter);
    }

    private void sendSelected(View anchor) {
        if (selectedPdf == null) { Snackbar.make(anchor,"Bitte zuerst ein PDF auswählen.",Snackbar.LENGTH_LONG).show(); return; }
        Profile p = SecureStore.find(this, selectedProfileId);
        if (p == null) { Snackbar.make(anchor,"Versandprofil fehlt.",Snackbar.LENGTH_LONG).show(); return; }
        Snackbar.make(anchor,"Übertragung läuft…",Snackbar.LENGTH_LONG).show();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                Sender.send(this, selectedPdf, p);
                runOnUiThread(() -> Snackbar.make(anchor,"An E-POST übergeben.",Snackbar.LENGTH_LONG).show());
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(anchor,"Versand fehlgeschlagen: "+e.getMessage(),Snackbar.LENGTH_LONG).show());
            }
        });
    }

    private void renderProfiles() {
        root.removeAllViews(); root.addView(title("Versandprofile"));
        root.addView(body("Lege für jede serverseitig konfigurierte Versandart ein eigenes Ziel an, z. B. einseitig, Duplex, Farbe oder Einschreiben."));
        for (Profile p : SecureStore.load(this)) {
            LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
            TextView n = title(p.name); n.setTextSize(18); box.addView(n);
            box.addView(body((Profile.TYPE_IPP.equals(p.type) ? "IPP" : "WebDAV") + " · " + redactUrl(p.url) + "\n" + (p.active ? "Aktiv" : "Inaktiv")));
            MaterialButton edit = new MaterialButton(this); edit.setText("Bearbeiten");
            edit.setOnClickListener(v -> { Intent i = new Intent(this,ProfileEditActivity.class); i.putExtra("profileId",p.id); startActivity(i); });
            box.addView(edit); root.addView(card(box));
        }
        MaterialButton add = new MaterialButton(this); add.setText("Profil hinzufügen");
        add.setOnClickListener(v -> startActivity(new Intent(this,ProfileEditActivity.class))); root.addView(add);
    }

    private String redactUrl(String u) {
        try {
            Uri x = Uri.parse(Sender.normalizeSecureUrl(u));
            return x.getScheme()+"://"+x.getHost()+"/…";
        } catch (Exception e) { return "nicht konfiguriert"; }
    }

    private void renderSecurity() {
        root.removeAllViews(); root.addView(title("Sicherheit"));
        root.addView(card(body("Transport: ausschließlich HTTPS oder IPPS, TLS 1.2/1.3, Android-System-Truststore und Hostname-Prüfung. Optional kann pro Profil ein SHA-256-SPKI-Pin gesetzt werden.")));
        root.addView(card(body("Lokale Geheimnisse: Benutzername, Passwort, Ziel-URL und Pins werden zusammen mit den Profilen per AES-256-GCM verschlüsselt. Der Schlüssel bleibt im Android Keystore und wird nicht exportiert oder gesichert.")));
        root.addView(card(body("Die App akzeptiert keine Klartext-Verbindungen und bietet keinen Schalter zum Ignorieren von Zertifikatsfehlern.")));
        root.addView(card(body("Adressfenster-Korrektur wird am jeweiligen E-POST-Sammelkorb bzw. Netzwerkdrucker konfiguriert. Die App speichert die Werte zur Profilzuordnung und verändert das PDF nicht.")));
    }
}
