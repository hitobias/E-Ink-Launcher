package cn.modificator.launcher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;

/**
 * Pure-JVM unit tests for {@link Utils}.
 * <p>
 * Note: {@code getReadableFileSize} uses a threshold of 0.8 — the unit changes
 * just before the next power of 1024, not exactly at it. So 1024 still formats
 * in KB (not crossing into MB until 0.8 * 1024 * 1024 = 838,860.8 bytes ≈ 819.2 KB).
 * The DecimalFormat pattern {@code "####.00"} renders one or two integer digits
 * with two fraction digits, so 1.00 is "1.00", 2.00 is "2.00", etc.
 */
public class UtilsTest {

  @Test
  public void getReadableFileSize_zero_returnsBytes() {
    assertEquals("0bytes", Utils.getReadableFileSize(0));
  }

  @Test
  public void getReadableFileSize_smallBytes_returnsBytes() {
    // 819 = floor(1024 * 0.8) - 1 — still under threshold, stays in bytes.
    assertEquals("818bytes", Utils.getReadableFileSize(818));
  }

  @Test
  public void getReadableFileSize_oneKilobyte_returnsKB() {
    // 1024 bytes / 1024 = 1.00 KB.
    assertEquals("1.00KB", Utils.getReadableFileSize(1024));
  }

  @Test
  public void getReadableFileSize_twoMegabytes_returnsMB() {
    long twoMb = 2L * 1024 * 1024;
    assertEquals("2.00MB", Utils.getReadableFileSize(twoMb));
  }

  @Test
  public void getReadableFileSize_oneGigabyte_returnsGB() {
    long oneGb = 1024L * 1024 * 1024;
    assertEquals("1.00GB", Utils.getReadableFileSize(oneGb));
  }

  @Test
  public void getAMPMCNString_amBeforeFive_isLingChen() {
    // 0..4 AM → "凌晨"
    assertEquals("凌晨", Utils.getAMPMCNString(0, Calendar.AM));
    assertEquals("凌晨", Utils.getAMPMCNString(4, Calendar.AM));
  }

  @Test
  public void getAMPMCNString_amAroundDawn_isLiMing() {
    // 5..6 AM → "黎明"
    assertEquals("黎明", Utils.getAMPMCNString(5, Calendar.AM));
    assertEquals("黎明", Utils.getAMPMCNString(6, Calendar.AM));
  }

  @Test
  public void getAMPMCNString_amEarlyMorning_isZaoChen() {
    // 7..8 AM → "早晨"
    assertEquals("早晨", Utils.getAMPMCNString(7, Calendar.AM));
    assertEquals("早晨", Utils.getAMPMCNString(8, Calendar.AM));
  }

  @Test
  public void getAMPMCNString_amBeforeNoon_isShangWu() {
    // 9..11 AM → "上午"
    assertEquals("上午", Utils.getAMPMCNString(9, Calendar.AM));
    assertEquals("上午", Utils.getAMPMCNString(11, Calendar.AM));
  }

  @Test
  public void getAMPMCNString_pmAtNoonOrMidnight_isZhongWu() {
    // Calendar.HOUR with PM uses 0-based hour: hour 0 PM == 12:xx pm.
    assertEquals("中午", Utils.getAMPMCNString(0, Calendar.PM));
    assertEquals("中午", Utils.getAMPMCNString(12, Calendar.PM));
  }

  @Test
  public void getAMPMCNString_pmEarlyAfternoon_isXiaWu() {
    // 1..5 PM → "下午"
    assertEquals("下午", Utils.getAMPMCNString(1, Calendar.PM));
    assertEquals("下午", Utils.getAMPMCNString(5, Calendar.PM));
    // hours == 13, PM falls through all PM branches → "深夜" (out-of-range bucket)
    assertEquals("深夜", Utils.getAMPMCNString(13, Calendar.PM));
  }

  @Test
  public void getAMPMCNString_pmEvening_isWanShang() {
    // 6..9 PM → "晚上"
    assertEquals("晚上", Utils.getAMPMCNString(6, Calendar.PM));
    assertEquals("晚上", Utils.getAMPMCNString(9, Calendar.PM));
  }

  @Test
  public void getAMPMCNString_pmLateNight_isShenYe() {
    // 10..11 PM → "深夜"; also 23 as given in the spec falls in this bucket.
    assertEquals("深夜", Utils.getAMPMCNString(10, Calendar.PM));
    assertEquals("深夜", Utils.getAMPMCNString(11, Calendar.PM));
    assertEquals("深夜", Utils.getAMPMCNString(23, Calendar.PM));
  }
}
