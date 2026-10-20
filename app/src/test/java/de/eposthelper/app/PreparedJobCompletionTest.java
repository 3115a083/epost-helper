package de.eposthelper.app;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PreparedJobCompletionTest {
    @Test public void localTestDoesNotDeleteSourcesOrQueuedJob() throws Exception {
        Profile p=new Profile();p.provider=Profile.PROVIDER_DEBUG;p.type=Profile.TYPE_DEBUG;
        AtomicInteger deletions=new AtomicInteger();
        assertFalse(PreparedJobCompletion.finish(p,deletions::incrementAndGet,deletions::incrementAndGet));
        assertEquals(0,deletions.get());
    }
    @Test public void realSuccessfulDeliveryCompletesBothCleanupSteps() throws Exception {
        Profile p=new Profile();
        AtomicInteger deletions=new AtomicInteger();
        assertTrue(PreparedJobCompletion.finish(p,deletions::incrementAndGet,deletions::incrementAndGet));
        assertEquals(2,deletions.get());
    }
}
