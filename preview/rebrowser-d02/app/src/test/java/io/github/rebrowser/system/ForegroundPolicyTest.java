package io.github.rebrowser.system;

import static org.junit.Assert.*;
import org.junit.Test;

/** Advice matrix only; no service/notification is started and unknown is never allowed. */
public final class ForegroundPolicyTest {
    @Test public void grantedPermissionWithLiveLifecycleIsOnlyAnAdvice(){
        ForegroundPolicy.Decision decision=ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.GRANTED);
        assertEquals(ForegroundPolicy.Advice.ALLOWED,decision.advice());
        assertFalse(decision.isExecution());
    }
    @Test public void unknownOrDeniedPermissionBlocksOrStaysUnknown(){
        assertEquals(ForegroundPolicy.Advice.UNKNOWN,ForegroundPolicy.dataSyncAdvice(33,ForegroundPolicy.Lifecycle.STARTED,ForegroundPolicy.NotificationPermission.UNKNOWN).advice());
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.dataSyncAdvice(33,ForegroundPolicy.Lifecycle.STARTED,ForegroundPolicy.NotificationPermission.DENIED).advice());
        assertEquals(ForegroundPolicy.Reason.NOTIFICATION_PERMISSION_DENIED,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.DENIED).reason());
    }
    @Test public void destroyedOrUnsupportedSdkBlocksRegardlessOfPermission(){
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.mediaPlaybackAdvice(29,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.GRANTED).advice());
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.mediaPlaybackAdvice(33,ForegroundPolicy.Lifecycle.DESTROYED,ForegroundPolicy.NotificationPermission.GRANTED).advice());
    }
    @Test public void shortOperationRequiresForegroundAndKnownPermission(){
        assertEquals(ForegroundPolicy.Advice.BLOCKED,ForegroundPolicy.shortOperationAdvice(33,ForegroundPolicy.Lifecycle.STARTED,ForegroundPolicy.NotificationPermission.GRANTED,false).advice());
        assertEquals(ForegroundPolicy.Advice.UNKNOWN,ForegroundPolicy.shortOperationAdvice(33,ForegroundPolicy.Lifecycle.RESUMED,ForegroundPolicy.NotificationPermission.UNKNOWN,true).advice());
        assertThrows(IllegalArgumentException.class,()->ForegroundPolicy.mediaPlaybackAdvice(33,null,ForegroundPolicy.NotificationPermission.GRANTED));
    }
}
