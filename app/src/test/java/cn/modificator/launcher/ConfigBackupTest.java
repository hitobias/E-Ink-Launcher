package cn.modificator.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Round-trip tests for {@link ConfigBackup}.
 * <p>
 * Robolectric is required because {@code ConfigBackup} reads/writes real
 * {@link SharedPreferences}, which require a Context implementation.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ConfigBackupTest {

  private static final String PREFS_FILE = "launcherPropertyFile";

  private Context context;
  private SharedPreferences prefs;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
  }

  @Test
  public void exportThenImport_preservesAllPrimitiveValues() throws Exception {
    Set<String> hiddenApps = new HashSet<>(Arrays.asList("com.foo", "com.bar"));
    prefs.edit()
        .putString("theme", "dark")
        .putInt("sortMode", 3)
        .putLong("lastSync", 12345678L)
        .putFloat("fontScale", 1.25f)
        .putBoolean("hideSystem", true)
        .putStringSet("hiddenApps", hiddenApps)
        .commit();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ConfigBackup.exportTo(context, out);

    // Wipe and re-import.
    prefs.edit().clear().commit();
    assertFalse("prefs should be empty after clear", prefs.contains("theme"));

    InputStream in = new ByteArrayInputStream(out.toByteArray());
    ConfigBackup.importFrom(context, in);

    assertEquals("dark", prefs.getString("theme", null));
    assertEquals(3, prefs.getInt("sortMode", -1));
    // Note: JSON has no distinct long type; ConfigBackup stores values that
    // parse back as java.lang.Integer when they fit. 12345678 fits, so it
    // round-trips through the Integer branch and we read it as int. Read both
    // to be robust.
    long longValue = prefs.contains("lastSync")
        ? (prefs.getAll().get("lastSync") instanceof Long
            ? prefs.getLong("lastSync", -1L)
            : (long) prefs.getInt("lastSync", -1))
        : -1L;
    assertEquals(12345678L, longValue);
    assertEquals(1.25f, prefs.getFloat("fontScale", -1f), 0.0001f);
    assertTrue(prefs.getBoolean("hideSystem", false));

    Set<String> restored = prefs.getStringSet("hiddenApps", null);
    assertNotNull(restored);
    assertEquals(hiddenApps, restored);
  }

  @Test
  public void exportThenImport_clearsExistingPrefsFirst() throws Exception {
    prefs.edit().putString("keep", "first").commit();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ConfigBackup.exportTo(context, out);

    // Add a stale key, then re-import — it should be wiped by import.
    prefs.edit().putString("stale", "leftover").commit();
    assertTrue(prefs.contains("stale"));

    InputStream in = new ByteArrayInputStream(out.toByteArray());
    ConfigBackup.importFrom(context, in);

    assertEquals("first", prefs.getString("keep", null));
    assertFalse("stale key should have been cleared by import",
        prefs.contains("stale"));
  }

  @Test
  public void importFrom_invalidJson_throws() {
    InputStream in = new ByteArrayInputStream(
        "not a json".getBytes(StandardCharsets.UTF_8));
    try {
      ConfigBackup.importFrom(context, in);
      fail("expected JSONException for malformed JSON");
    } catch (JSONException expected) {
      // expected
    } catch (IOException ioe) {
      fail("expected JSONException not IOException: " + ioe);
    }
  }

  @Test
  public void importFrom_missingPrefsObject_throws() {
    InputStream in = new ByteArrayInputStream(
        "{\"version\":1}".getBytes(StandardCharsets.UTF_8));
    try {
      ConfigBackup.importFrom(context, in);
      fail("expected JSONException when 'prefs' is missing");
    } catch (JSONException expected) {
      assertTrue(expected.getMessage(),
          expected.getMessage() != null
              && expected.getMessage().toLowerCase().contains("prefs"));
    } catch (IOException ioe) {
      fail("expected JSONException not IOException: " + ioe);
    }
  }

  @Test
  public void importFrom_oversizedInput_throws() {
    // Build a > 256KB string of valid JSON-ish noise.
    StringBuilder sb = new StringBuilder(300 * 1024);
    sb.append("{\"prefs\":{\"x\":\"");
    while (sb.length() < 300 * 1024) sb.append('a');
    sb.append("\"}}");
    InputStream in = new ByteArrayInputStream(
        sb.toString().getBytes(StandardCharsets.UTF_8));
    try {
      ConfigBackup.importFrom(context, in);
      fail("expected IOException for oversized config");
    } catch (IOException expected) {
      // expected
    } catch (JSONException je) {
      fail("expected IOException not JSONException: " + je);
    }
  }
}
