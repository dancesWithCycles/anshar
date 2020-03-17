package no.rutebanken.anshar.data;

import org.junit.Test;

import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;

public class SiriObjectStorageKeyTest {

    @Test
    public void testEquals() {
        String uuid = UUID.randomUUID().toString();
        SiriObjectStorageKey keyA = new SiriObjectStorageKey("TST", "Line:1234", uuid);
        SiriObjectStorageKey keyB = new SiriObjectStorageKey("TST", "Line:1234", uuid);

        assertEquals(keyA, keyB);
    }

    @Test
    public void testNotEqualLineRef() {
        String uuid = UUID.randomUUID().toString();
        SiriObjectStorageKey keyA = new SiriObjectStorageKey("TST", "Line:1234", uuid);
        SiriObjectStorageKey keyB = new SiriObjectStorageKey("TST", "Line:12345", uuid);

        assertFalse(keyA.equals(keyB));
    }

    @Test
    public void testNullLineRef() {
        String uuid = UUID.randomUUID().toString();
        SiriObjectStorageKey keyA = new SiriObjectStorageKey("TST", null, uuid);
        SiriObjectStorageKey keyB = new SiriObjectStorageKey("TST", null, uuid);

        assertEquals(keyA, keyB);
    }

    @Test
    public void testNotNullLineRef() {
        String uuid = UUID.randomUUID().toString();
        SiriObjectStorageKey keyA = new SiriObjectStorageKey("TST", "Line:1234", uuid);
        SiriObjectStorageKey keyB = new SiriObjectStorageKey("TST", null, uuid);

        assertFalse(keyA.equals(keyB));
    }

    @Test
    public void testNotEqualCodespace() {
        String uuid = UUID.randomUUID().toString();
        SiriObjectStorageKey keyA = new SiriObjectStorageKey("ABC", "Line:1234", uuid);
        SiriObjectStorageKey keyB = new SiriObjectStorageKey("DEF", "Line:1234", uuid);

        assertFalse(keyA.equals(keyB));
    }

    @Test
    public void testNotEqualKey() {
        SiriObjectStorageKey keyA = new SiriObjectStorageKey("TST", "Line:1234", UUID.randomUUID().toString());
        SiriObjectStorageKey keyB = new SiriObjectStorageKey("TST", "Line:12345", UUID.randomUUID().toString());

        assertFalse(keyA.equals(keyB));
    }
}
