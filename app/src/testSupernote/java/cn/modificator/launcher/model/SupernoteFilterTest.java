package cn.modificator.launcher.model;

import static org.junit.Assert.assertFalse;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Supernote flavor-specific filter checks: Chauvet OS internal packages are
 * silently filtered out of the grid.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SupernoteFilterTest {

  private static ResolveInfo makeInfo(String pkg) {
    ResolveInfo info = new ResolveInfo();
    ActivityInfo ai = new ActivityInfo();
    ai.packageName = pkg;
    ai.name = pkg + ".MainActivity";
    ApplicationInfo appInfo = new ApplicationInfo();
    appInfo.packageName = pkg;
    ai.applicationInfo = appInfo;
    info.activityInfo = ai;
    return info;
  }

  @Test
  public void supernoteFactoryTest_isFiltered() {
    assertFalse(AppFilter.shouldShow(makeInfo("com.ratta.supernote.supernotefactorytest"), false));
  }

  @Test
  public void supernoteLauncher_isFiltered() {
    assertFalse(AppFilter.shouldShow(makeInfo("com.ratta.supernote.launcher"), false));
  }

  @Test
  public void supernoteBackground_isFiltered() {
    assertFalse(AppFilter.shouldShow(makeInfo("com.ratta.supernote.background"), false));
  }
}
