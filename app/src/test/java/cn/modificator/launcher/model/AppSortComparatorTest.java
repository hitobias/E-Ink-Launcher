package cn.modificator.launcher.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests for {@link AppSortComparator}.
 * <p>
 * {@link ResolveInfo#loadLabel(PackageManager)} returns
 * {@link ResolveInfo#nonLocalizedLabel} when it is set, bypassing the
 * PackageManager. We use that to make name-based ordering deterministic
 * without setting up a ShadowPackageManager.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class AppSortComparatorTest {

  private Context context;
  private PackageManager pm;

  @Before
  public void setUp() {
    context = ApplicationProvider.getApplicationContext();
    pm = context.getPackageManager();
  }

  private static ResolveInfo makeInfo(String pkg, String label) {
    ResolveInfo r = new ResolveInfo();
    r.activityInfo = new ActivityInfo();
    r.activityInfo.packageName = pkg;
    r.activityInfo.name = pkg + ".Main";
    r.activityInfo.applicationInfo = new ApplicationInfo();
    r.activityInfo.applicationInfo.packageName = pkg;
    r.nonLocalizedLabel = label;
    return r;
  }

  @Test
  public void sortNameAsc_alphabeticalOrder() {
    ResolveInfo cherry = makeInfo("com.example.c", "Cherry");
    ResolveInfo apple = makeInfo("com.example.a", "Apple");
    ResolveInfo banana = makeInfo("com.example.b", "Banana");

    List<ResolveInfo> list = new ArrayList<>(Arrays.asList(cherry, apple, banana));
    Collections.sort(list, new AppSortComparator(context, pm, AppSortComparator.SORT_NAME_ASC));

    assertEquals("Apple", list.get(0).nonLocalizedLabel);
    assertEquals("Banana", list.get(1).nonLocalizedLabel);
    assertEquals("Cherry", list.get(2).nonLocalizedLabel);
  }

  @Test
  public void sortNameDesc_reverseAlphabeticalOrder() {
    ResolveInfo cherry = makeInfo("com.example.c", "Cherry");
    ResolveInfo apple = makeInfo("com.example.a", "Apple");
    ResolveInfo banana = makeInfo("com.example.b", "Banana");

    List<ResolveInfo> list = new ArrayList<>(Arrays.asList(apple, banana, cherry));
    Collections.sort(list, new AppSortComparator(context, pm, AppSortComparator.SORT_NAME_DESC));

    assertEquals("Cherry", list.get(0).nonLocalizedLabel);
    assertEquals("Banana", list.get(1).nonLocalizedLabel);
    assertEquals("Apple", list.get(2).nonLocalizedLabel);
  }

  @Test
  public void virtualIcons_alwaysSortToBottom_inAscMode() {
    ResolveInfo lock = makeInfo(AppDataCenter.LOCK_PACKAGE_NAME, "Lock");
    ResolveInfo wifi = makeInfo(AppDataCenter.WIFI_PACKAGE_NAME, "WiFi");
    ResolveInfo bt = makeInfo(AppDataCenter.BLUETOOTH_PACKAGE_NAME, "Bluetooth");
    ResolveInfo apple = makeInfo("com.example.a", "Apple");
    ResolveInfo zebra = makeInfo("com.example.z", "Zebra");

    List<ResolveInfo> list = new ArrayList<>(Arrays.asList(lock, apple, wifi, zebra, bt));
    Collections.sort(list, new AppSortComparator(context, pm, AppSortComparator.SORT_NAME_ASC));

    // First two slots: the real apps (alphabetical).
    assertEquals("Apple", list.get(0).nonLocalizedLabel);
    assertEquals("Zebra", list.get(1).nonLocalizedLabel);
    // Remaining three: the virtual icons, in any order.
    for (int i = 2; i < 5; i++) {
      String pkg = list.get(i).activityInfo.packageName;
      assertTrue("expected virtual pkg at index " + i + " but got " + pkg,
          AppDataCenter.LOCK_PACKAGE_NAME.equals(pkg)
              || AppDataCenter.WIFI_PACKAGE_NAME.equals(pkg)
              || AppDataCenter.BLUETOOTH_PACKAGE_NAME.equals(pkg));
    }
  }

  @Test
  public void virtualIcons_alwaysSortToBottom_inDescMode() {
    ResolveInfo lock = makeInfo(AppDataCenter.LOCK_PACKAGE_NAME, "Lock");
    ResolveInfo apple = makeInfo("com.example.a", "Apple");
    ResolveInfo zebra = makeInfo("com.example.z", "Zebra");

    List<ResolveInfo> list = new ArrayList<>(Arrays.asList(lock, apple, zebra));
    Collections.sort(list, new AppSortComparator(context, pm, AppSortComparator.SORT_NAME_DESC));

    // DESC: Zebra, Apple, then virtual.
    assertEquals("Zebra", list.get(0).nonLocalizedLabel);
    assertEquals("Apple", list.get(1).nonLocalizedLabel);
    assertEquals(AppDataCenter.LOCK_PACKAGE_NAME, list.get(2).activityInfo.packageName);
  }

  @Test
  public void modeNeedsUsageStats_correctlyClassifies() {
    assertTrue(AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_USAGE_ASC));
    assertTrue(AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_USAGE_DESC));
    assertTrue(AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_RECENT_ASC));
    assertTrue(AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_RECENT_DESC));
    // Name and install modes don't need stats.
    org.junit.Assert.assertFalse(
        AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_NAME_ASC));
    org.junit.Assert.assertFalse(
        AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_NAME_DESC));
    org.junit.Assert.assertFalse(
        AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_INSTALL_ASC));
    org.junit.Assert.assertFalse(
        AppSortComparator.modeNeedsUsageStats(AppSortComparator.SORT_INSTALL_DESC));
  }
}
