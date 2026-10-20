package de.eposthelper.app;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class BackupPayloadTest {
    private JSONObject valid() throws Exception {
        return new JSONObject().put("format",1).put("settings",new JSONObject()
            .put("appearance","system").put("palette","material_you").put("debug",false).put("outboxFolder",""))
            .put("profiles",new JSONArray()).put("stats",new JSONArray());
    }
    @Test public void acceptsCompleteEmptyBackup() throws Exception {
        assertEquals(0,BackupPayload.parse(valid().toString()).profiles.size());
    }
    @Test public void rejectsMissingAndWrongTypedSections() throws Exception {
        JSONObject root=valid(); root.remove("profiles");
        assertThrows(Exception.class,()->BackupPayload.parse(root.toString()));
        JSONObject wrong=valid().put("stats","[]");
        assertThrows(Exception.class,()->BackupPayload.parse(wrong.toString()));
    }
    @Test public void rejectsUnknownVersionAndInvalidSettings() throws Exception {
        JSONObject root=valid().put("format",2);
        assertThrows(Exception.class,()->BackupPayload.parse(root.toString()));
        JSONObject other=valid();
        other.getJSONObject("settings").put("debug","true");
        assertThrows(Exception.class,()->BackupPayload.parse(other.toString()));
    }
    @Test public void rejectsDuplicateProfilesBeforeAnyWrite() throws Exception {
        JSONObject root=valid(); Profile p=new Profile();p.id="test";
        root.getJSONArray("profiles").put(p.toJson()).put(p.toJson());
        assertThrows(Exception.class,()->BackupPayload.parse(root.toString()));
    }
    @Test public void boundedReaderRejectsNextByteAndAcceptsBoundary() throws Exception {
        byte[] data=new byte[BackupPayload.MAX_BYTES];
        assertEquals(data.length,BackupPayload.readBounded(new ByteArrayInputStream(data)).length);
        InputStream tooLarge=new SequenceInputStream(new ByteArrayInputStream(data),new ByteArrayInputStream(new byte[]{1}));
        assertThrows(Exception.class,()->BackupPayload.readBounded(tooLarge));
    }
}
