package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Read-only projection; no MediaSession is created and no transport action is executed. */
public final class MusicMediaSessionTest {
    private static MusicState state(MusicState.Playback playback,long duration){return new MusicState(MusicState.Account.REPORTED_SIGNED_IN,playback,MusicState.Focus.NONE,"歌","人",null,10,duration,1,0);}
    @Test public void playingProjectionOffersPauseAndBoundedSeek(){
        MusicMediaSession.Projection projection=MusicMediaSession.project(state(MusicState.Playback.PLAYING,100),true,false);
        assertEquals(MusicMediaSession.Transport.PLAYING,projection.transport());
        assertTrue(projection.actions().contains(MusicMediaSession.Action.PAUSE));
        assertTrue(projection.actions().contains(MusicMediaSession.Action.SKIP_NEXT));
        assertFalse(projection.actions().contains(MusicMediaSession.Action.SKIP_PREVIOUS));
        assertTrue(projection.actions().contains(MusicMediaSession.Action.SEEK));
        assertEquals(1f,projection.reportedSpeed(),0f);
    }
    @Test public void unknownPlaybackYieldsNoTransportAction(){
        MusicMediaSession.Projection projection=MusicMediaSession.project(state(MusicState.Playback.UNKNOWN,-1),true,true);
        assertEquals(MusicMediaSession.Transport.NONE,projection.transport());
        assertFalse(projection.actions().contains(MusicMediaSession.Action.PLAY));
        assertFalse(projection.actions().contains(MusicMediaSession.Action.PAUSE));
        assertTrue(projection.actions().contains(MusicMediaSession.Action.SKIP_NEXT));
        assertFalse(projection.seekable());
        assertEquals(-1,projection.reportedDurationMs());
    }
    @Test public void explicitOffersDriveActionsAndUnknownDurationRemovesSeek(){
        java.util.Set<MusicMediaSession.Action> offered=java.util.EnumSet.of(MusicMediaSession.Action.PLAY,MusicMediaSession.Action.SEEK,MusicMediaSession.Action.SKIP_NEXT);
        MusicMediaSession.Projection mixed=MusicMediaSession.project(state(MusicState.Playback.UNKNOWN,-1),offered,null);
        assertEquals(MusicMediaSession.Transport.NONE,mixed.transport());
        assertFalse(mixed.actions().contains(MusicMediaSession.Action.SEEK));
        assertTrue(mixed.actions().contains(MusicMediaSession.Action.SKIP_NEXT));
        assertFalse(mixed.seekable());
        assertTrue(MusicMediaSession.Projection.empty().actions().isEmpty());
        assertEquals(-1,MusicMediaSession.Projection.empty().reportedDurationMs());
        assertThrows(IllegalArgumentException.class,()->MusicMediaSession.project(state(MusicState.Playback.PLAYING,10),null,null));
    }
    @Test public void estimateStaysSeparateFromReportedPosition(){
        MusicState observed=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,1000,5000,1,100);
        MusicState.Estimate estimate=observed.estimatePosition(1,2600);
        MusicMediaSession.Projection projection=MusicMediaSession.project(observed,java.util.EnumSet.of(MusicMediaSession.Action.PAUSE),estimate);
        assertEquals(1000,projection.reportedPositionMs());
        assertEquals(Long.valueOf(3500),projection.estimatedPositionMs());
    }
    @Test public void pausedAndUnknownDurationDoNotFabricateSeek(){
        MusicMediaSession.Projection projection=MusicMediaSession.project(state(MusicState.Playback.PAUSED,-1),false,false);
        assertTrue(projection.actions().contains(MusicMediaSession.Action.PLAY));
        assertFalse(projection.actions().contains(MusicMediaSession.Action.SEEK));
        assertEquals(0f,projection.reportedSpeed(),0f);
        assertThrows(IllegalArgumentException.class,()->MusicMediaSession.project(null,false,false));
    }
}
