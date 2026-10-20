package io.github.rebrowser.engine;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.MediaSession;

/**
 * Read-only adapter for the pinned SDK {@link MediaSession.Delegate}. It NEVER calls play/pause/
 * seek/setActive, never reads artwork bytes, never identifies an account and never grants
 * playback. Only the explicitly bound session is observed; a stale/retired binding drops the
 * event. Times arrive in SECONDS from Gecko and are kept as seconds here, never mistaken for the
 * Android media-session milliseconds.
 */
public final class GeckoMediaBridge implements MediaSession.Delegate {
    public static final int MAX_TEXT_CHARS = 512;
    public static final double MAX_SECONDS = 24d * 60 * 60;

    public enum Transport { UNOBSERVED, ACTIVE, INACTIVE, PLAY_REPORTED, PAUSE_REPORTED, STOP_REPORTED }
    public interface Guard { boolean current(GeckoSession boundSession); }
    public interface Listener { void observed(Observation observation); }

    public static final class Observation {
        private final Transport transport;private final String title,artist,album;
        private final double positionSeconds,durationSeconds,playbackRate;private final long sequence;
        Observation(Transport transport,String title,String artist,String album,double position,double duration,double rate,long sequence){
            this.transport=transport;this.title=title;this.artist=artist;this.album=album;
            this.positionSeconds=position;this.durationSeconds=duration;this.playbackRate=rate;this.sequence=sequence;
        }
        public Transport transport(){return transport;}
        public String title(){return title;}
        public String artist(){return artist;}
        public String album(){return album;}
        public double positionSeconds(){return positionSeconds;}
        /** -1 = the page did not report a duration; never coerced to zero. */
        public double durationSeconds(){return durationSeconds;}
        public double playbackRate(){return playbackRate;}
        public long sequence(){return sequence;}
        public long positionMillis(){return positionSeconds<0?-1:(long)Math.round(positionSeconds*1000d);}
    }

    private final GeckoSession bound;private final Guard guard;private final Listener listener;
    private long sequence;private boolean invalidated;
    private Transport transport=Transport.UNOBSERVED;
    private String title="",artist="",album="";
    private double position=-1,duration=-1,rate=0;

    public GeckoMediaBridge(GeckoSession exactSession,Guard guard,Listener listener){
        if(exactSession==null || guard==null || listener==null)throw new IllegalArgumentException("Exact session/guard/listener required");
        bound=exactSession;this.guard=guard;this.listener=listener;
    }

    @Override public void onActivated(GeckoSession session,MediaSession mediaSession){if(eligible(session)){transport=Transport.ACTIVE;emit();}}
    @Override public void onDeactivated(GeckoSession session,MediaSession mediaSession){if(eligible(session)){transport=Transport.INACTIVE;emit();}}
    @Override public void onPlay(GeckoSession session,MediaSession mediaSession){if(eligible(session)){transport=Transport.PLAY_REPORTED;emit();}}
    @Override public void onPause(GeckoSession session,MediaSession mediaSession){if(eligible(session)){transport=Transport.PAUSE_REPORTED;emit();}}
    @Override public void onStop(GeckoSession session,MediaSession mediaSession){if(eligible(session)){transport=Transport.STOP_REPORTED;emit();}}
    @Override public void onMetadata(GeckoSession session,MediaSession mediaSession,MediaSession.Metadata metadata){
        if(!eligible(session) || metadata==null)return;
        title=text(metadata.title);artist=text(metadata.artist);album=text(metadata.album);
        emit();
    }
    @Override public void onPositionState(GeckoSession session,MediaSession mediaSession,MediaSession.PositionState state){
        if(!eligible(session) || state==null)return;
        double nextPosition=state.position,nextDuration=state.duration,nextRate=state.playbackRate;
        if(!finite(nextPosition,0) || !finite(nextDuration,-1) || !Double.isFinite(nextRate) || Math.abs(nextRate)>16d)return;
        if(nextDuration>=0 && nextPosition>nextDuration+0.001d)return;
        position=nextPosition;duration=nextDuration;rate=nextRate;
        emit();
    }
    public void invalidate(){invalidated=true;}
    public Observation snapshot(){return new Observation(transport,title,artist,album,position,duration,rate,sequence);}

    private boolean eligible(GeckoSession session){
        if(invalidated || session!=bound)return false;
        try{return guard.current(bound);}catch(RuntimeException unavailable){return false;}
    }
    private void emit(){
        if(sequence==Long.MAX_VALUE){invalidated=true;return;}
        sequence++;
        try{listener.observed(snapshot());}catch(RuntimeException ignored){/* observation is not task settlement */}
    }
    private static boolean finite(double value,double minimum){return Double.isFinite(value) && value>=minimum && value<=MAX_SECONDS;}
    private static String text(String value){
        if(value==null)return "";
        StringBuilder out=new StringBuilder();int count=0;
        for(int offset=0;offset<value.length() && count<MAX_TEXT_CHARS;){int cp=value.codePointAt(offset);offset+=Character.charCount(cp);
            if(Character.isISOControl(cp))continue;out.appendCodePoint(cp);count++;}
        return out.toString();
    }
}
