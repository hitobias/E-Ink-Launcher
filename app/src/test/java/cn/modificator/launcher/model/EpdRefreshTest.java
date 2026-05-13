package cn.modificator.launcher.model;

import static org.junit.Assert.assertFalse;

import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Regression test for v0.2.1 Supernote crash:
 * EpdRefresh.fast() called ConcurrentHashMap.put(key, null) when the first
 * vendor class lookup failed (Onyx classes don't exist on Supernote).
 * ConcurrentHashMap rejects null values → NPE → app crash on launch.
 *
 * Robolectric has no vendor SDK on the classpath, so every vendor lookup
 * misses — exactly the path that crashed on Supernote. fast()/full() must
 * still return false without throwing.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class EpdRefreshTest {

  @Test
  public void fast_returnsFalseAndDoesNotThrow_whenNoVendorSdk() {
    View v = new View(RuntimeEnvironment.getApplication());
    assertFalse(EpdRefresh.fast(v));
  }

  @Test
  public void full_returnsFalseAndDoesNotThrow_whenNoVendorSdk() {
    View v = new View(RuntimeEnvironment.getApplication());
    assertFalse(EpdRefresh.full(v));
  }

  @Test
  public void fast_negativeLookupIsCachedAndRepeatable() {
    // First call populates the misses set; subsequent calls hit the cache.
    // Either path must not NPE.
    View v = new View(RuntimeEnvironment.getApplication());
    assertFalse(EpdRefresh.fast(v));
    assertFalse(EpdRefresh.fast(v));
    assertFalse(EpdRefresh.fast(v));
  }

  @Test
  public void nullTargetIsRejectedEarly() {
    assertFalse(EpdRefresh.fast(null));
    assertFalse(EpdRefresh.full(null));
  }
}
