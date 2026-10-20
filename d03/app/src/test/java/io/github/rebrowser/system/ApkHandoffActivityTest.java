package io.github.rebrowser.system;

import static org.junit.Assert.*;
import org.junit.Test;

/** Verified descriptor validation and state labels; no APK/installer action runs here. */
public final class ApkHandoffActivityTest {
    private static final String SHA="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    @Test public void verifiedDescriptorRequiresDigestAndBounds(){
        ApkHandoffActivity.Verified verified=new ApkHandoffActivity.Verified("ReBrowser.apk","2.0.0",1024,SHA);
        assertEquals(1024,verified.byteCount());
        assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.Verified("a","1",0,SHA));
        assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.Verified("a","1",1,"ABC"));
        assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.Verified("","1",1,SHA));
        assertThrows(IllegalArgumentException.class,()->new ApkHandoffActivity.Verified("a",new String(new char[65]).replace('\0','v'),1,SHA));
    }
    @Test public void stateLabelsNeverClaimInstallationComplete(){
        for(ApkHandoffActivity.HandoffState state:ApkHandoffActivity.HandoffState.values()){
            String label=ApkHandoffActivity.stateLabel(state);
            assertFalse(label.contains("安装完成"));
            assertFalse(label.contains("已安装"));
        }
        assertTrue(ApkHandoffActivity.stateLabel(ApkHandoffActivity.HandoffState.HANDED_OFF_TO_SYSTEM).contains("未确认"));
        assertThrows(IllegalArgumentException.class,()->ApkHandoffActivity.stateLabel(null));
    }
}
