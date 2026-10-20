package de.eposthelper.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class DiagnosticAndPreviewTest {
    @Test public void clipboardContainsOnlyAllowlistedTechnicalFields() {
        String raw="HTTP 401 https://user:password@example.org/?apiKey=secret\nAuthorization: Bearer abc\nMax Mustermann, Hauptstraße 1\nperson@example.org\nprivate.pdf";
        String copied=DiagnosticRedactor.forCopy(raw,"DiagnosticException");
        assertTrue(copied.contains("401"));
        for(String secret:new String[]{"password","secret","Bearer","Mustermann","Hauptstraße","person@","private.pdf","example.org"})
            assertFalse(copied.contains(secret));
    }
    @Test public void replacingDocumentAtSamePositionRejectsOldPreview() {
        PreviewTicket old=new PreviewTicket(1,"content://old.pdf");
        assertFalse(old.matches(1,"content://replacement.pdf"));
        assertFalse(old.matches(2,"content://old.pdf"));
        assertTrue(old.matches(1,"content://old.pdf"));
    }
}
