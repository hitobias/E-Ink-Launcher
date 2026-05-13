package cn.modificator.launcher.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests for {@link AppFilter#shouldShow(ResolveInfo, boolean)}.
 * <p>
 * Robolectric is used because {@code ResolveInfo}/{@code ActivityInfo}/
 * {@code ApplicationInfo} need real Android framework classes at runtime
 * (they exist in plain {@code android.jar} stub but throw on no-arg constructor
 * without a real framework). Robolectric provides those.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppFilterTest {

  private static ResolveInfo makeInfo(String pkg, int flags) {
    ResolveInfo info = new ResolveInfo();
    ActivityInfo ai = new ActivityInfo();
    ai.packageName = pkg;
    ai.name = pkg + ".MainActivity";
    ApplicationInfo appInfo = new ApplicationInfo();
    appInfo.packageName = pkg;
    appInfo.flags = flags;
    ai.applicationInfo = appInfo;
    info.activityInfo = ai;
    return info;
  }

  @Test
  public void shouldShow_themeIconPack_isFiltered() {
    ResolveInfo info = makeInfo("com.android.theme.icon_pack.foo", 0);
    assertFalse(AppFilter.shouldShow(info, false));
  }

  @Test
  public void shouldShow_ownLauncher_isFiltered() {
    ResolveInfo info = makeInfo("cn.modificator.launcher", 0);
    assertFalse(AppFilter.shouldShow(info, false));
  }

  @Test
  public void shouldShow_ownLauncherDebugFlavor_isFiltered() {
    // BLOCKED_PREFIXES uses startsWith, so .debug suffix is also filtered.
    ResolveInfo info = makeInfo("cn.modificator.launcher.debug", 0);
    assertFalse(AppFilter.shouldShow(info, false));
  }

  @Test
  public void shouldShow_normalApp_keepSystemApps_isVisible() {
    ResolveInfo info = makeInfo("com.example.app", 0);
    assertTrue(AppFilter.shouldShow(info, false));
  }

  @Test
  public void shouldShow_normalApp_hideSystemApps_isVisible() {
    ResolveInfo info = makeInfo("com.example.app", 0);
    assertTrue(AppFilter.shouldShow(info, true));
  }

  @Test
  public void shouldShow_pureSystemApp_hideSystemApps_isFiltered() {
    ResolveInfo info = makeInfo("com.android.systemapp", ApplicationInfo.FLAG_SYSTEM);
    assertFalse(AppFilter.shouldShow(info, true));
  }

  @Test
  public void shouldShow_pureSystemApp_keepSystemApps_isVisible() {
    ResolveInfo info = makeInfo("com.android.systemapp", ApplicationInfo.FLAG_SYSTEM);
    assertTrue(AppFilter.shouldShow(info, false));
  }

  @Test
  public void shouldShow_updatedSystemApp_hideSystemApps_isVisible() {
    int flags = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
    ResolveInfo info = makeInfo("com.android.updatedsystem", flags);
    assertTrue(AppFilter.shouldShow(info, true));
  }

  @Test
  public void shouldShow_nullResolveInfo_isFiltered() {
    assertFalse(AppFilter.shouldShow(null, false));
  }

  @Test
  public void shouldShow_nullActivityInfo_isFiltered() {
    ResolveInfo info = new ResolveInfo();
    assertFalse(AppFilter.shouldShow(info, false));
  }

  @Test
  public void shouldShow_nullPackageName_isFiltered() {
    ResolveInfo info = new ResolveInfo();
    info.activityInfo = new ActivityInfo();
    // packageName left null
    assertFalse(AppFilter.shouldShow(info, false));
  }
}
