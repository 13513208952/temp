package io.github.rebrowser.lyrics;

import static org.junit.Assert.*;
import org.junit.Test;

/** Observation-only permission flow; nothing is requested and no consent is minted. */
public final class OverlayPermissionFlowTest {
    @Test public void unknownObservationIsNotAllowed(){
        OverlayPermissionFlow flow=new OverlayPermissionFlow();
        OverlayPermissionFlow.State state=flow.observe(null);
        assertEquals(OverlayPermissionFlow.Phase.UNKNOWN,state.phase());
        assertFalse(state.mayDisplayOverlay());
        assertEquals("系统浮层权限未知",state.label());
    }
    @Test public void allowedObservationEnablesDisplayButReturningRequiresRecheck(){
        OverlayPermissionFlow flow=new OverlayPermissionFlow();
        assertTrue(flow.observe(Boolean.TRUE).mayDisplayOverlay());
        OverlayPermissionFlow.State returned=flow.returnedFromSettings();
        assertEquals(OverlayPermissionFlow.Phase.RECHECK_REQUIRED,returned.phase());
        assertFalse(returned.mayDisplayOverlay());
        assertTrue(flow.observe(Boolean.TRUE).mayDisplayOverlay());
    }
    @Test public void systemFlowNeedsNativeTicketAndIsNotAnExecutionProof(){
        OverlayPermissionFlow flow=new OverlayPermissionFlow();
        flow.observe(Boolean.FALSE);
        assertFalse(flow.mayOfferSystemFlow(false));
        assertTrue(flow.mayOfferSystemFlow(true));
        flow.observe(Boolean.TRUE);
        assertFalse(flow.mayOfferSystemFlow(true));
    }
    @Test public void foreignThreadAndRetirementReject(){
        OverlayPermissionFlow flow=new OverlayPermissionFlow();
        flow.retire();
        assertThrows(IllegalStateException.class,()->flow.observe(Boolean.TRUE));
    }
}
