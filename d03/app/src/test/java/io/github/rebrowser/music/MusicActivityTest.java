package io.github.rebrowser.music;

import static org.junit.Assert.*;
import org.junit.Test;

/** Display arithmetic for the window; no playback is started by these methods. */
public final class MusicActivityTest {
    @Test public void progressAndSeekAreClampedAndBounded(){
        assertEquals(0,MusicActivity.Model.progressPermille(-1,1000));
        assertEquals(500,MusicActivity.Model.progressPermille(500,1000));
        assertEquals(1000,MusicActivity.Model.progressPermille(5000,1000));
        assertEquals(0,MusicActivity.Model.progressPermille(100,0));
        assertEquals(500,MusicActivity.Model.seekTarget(1000,500));
        assertThrows(IllegalArgumentException.class,()->MusicActivity.Model.seekTarget(1000,1001));
        assertThrows(IllegalArgumentException.class,()->MusicActivity.Model.seekTarget(0,1));
    }
    @Test public void seekIsOnlyEnabledForReportedPlayingWithDuration(){
        MusicState playing=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,10,100,1,0);
        MusicState paused=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PAUSED,MusicState.Focus.NONE,"t","a",null,10,100,1,0);
        MusicState unknownDuration=new MusicState(MusicState.Account.UNKNOWN,MusicState.Playback.PLAYING,MusicState.Focus.NONE,"t","a",null,10,-1,1,0);
        assertTrue(MusicActivity.Model.seekEnabled(playing));
        assertFalse(MusicActivity.Model.seekEnabled(paused));
        assertFalse(MusicActivity.Model.seekEnabled(unknownDuration));
        assertFalse(MusicActivity.Model.seekEnabled(null));
    }

    @Test public void lyricTextHighlightsOnlyInsideAWordAndNeverKeepsGapWord(){
        MusicLyrics.Lyrics lyrics=MusicLyrics.parse("[00:00.00]line",MusicLyrics.parseWords("100\t200\t甲\n400\t500\t乙"));
        assertTrue(MusicActivity.Model.lyricText(lyrics,150).contains("[甲]"));
        assertFalse(MusicActivity.Model.lyricText(lyrics,300).contains("[甲]"));
        assertTrue(MusicActivity.Model.lyricText(lyrics,450).contains("[乙]"));
        assertFalse(MusicActivity.Model.lyricText(lyrics,600).contains("["));
        assertEquals("歌词未读取",MusicActivity.Model.lyricText(null,0));
    }
}
