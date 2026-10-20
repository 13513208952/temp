package io.github.rebrowser.music;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builds a media notification VIEW only. It creates no channel, posts nothing, starts no
 * foreground service and fabricates no PendingIntent: actions must be exact intentionally-supplied
 * intents. A {@link Snapshot} binds one native frame to immutable title/subtitle/action order,
 * is consumable ONCE by its owner, and {@link Snapshot#empty()} clears stale private text/actions.
 * Seconds from the official player are converted to milliseconds with bounded long arithmetic.
 */
public final class MusicNotification {
    public static final int MAX_TEXT_CHARS = 128;
    public static final long MAX_POSITION_SECONDS = 24L * 60 * 60;
    public static final int MAX_ACTIONS = 4;
    public static final String DEFAULT_CHANNEL_ID = "rebrowser.music.playback";
    public enum ActionKind { PLAY, PAUSE, NEXT, PREVIOUS }

    /** Exact supplied action; this class never constructs its intent or PendingIntent. */
    public static final class Action {
        private final ActionKind kind;private final PendingIntent intent;
        public Action(ActionKind kind,PendingIntent exactNativeIntent){
            if(kind==null || exactNativeIntent==null)throw new IllegalArgumentException("Action requires an exact native intent");
            this.kind=kind;this.intent=exactNativeIntent;
        }
        public ActionKind kind(){return kind;}
        public PendingIntent intent(){return intent;}
    }
    public static final class Projection {
        private final String title,artist;private final long positionSeconds,durationSeconds;
        private final boolean playing;private final boolean unknownDuration;
        public Projection(String title,String artist,boolean playing,long positionSeconds,long durationSeconds){
            this.title=text(title,MAX_TEXT_CHARS);this.artist=text(artist,MAX_TEXT_CHARS);this.playing=playing;
            if(positionSeconds<0 || positionSeconds>MAX_POSITION_SECONDS)throw new IllegalArgumentException("Invalid position");
            if(durationSeconds< -1 || durationSeconds>MAX_POSITION_SECONDS)throw new IllegalArgumentException("Invalid duration");
            if(durationSeconds>=0 && positionSeconds>durationSeconds)throw new IllegalArgumentException("Position exceeds duration");
            this.positionSeconds=positionSeconds;this.durationSeconds=durationSeconds;this.unknownDuration=durationSeconds<0;
        }
        public String title(){return title;}
        public String artist(){return artist;}
        public boolean playing(){return playing;}
        public long positionSeconds(){return positionSeconds;}
        public long durationSeconds(){return durationSeconds;}
        public boolean unknownDuration(){return unknownDuration;}
        /** Seconds to milliseconds; -1 remains "unknown" instead of a fake zero. */
        public long positionMillis(){return positionSeconds*1000L;}
        public long durationMillis(){return unknownDuration?-1:durationSeconds*1000L;}
        public long shownProgressMillis(){return positionMillis();}
    }

    /** One consumable display frame; a consumed/empty snapshot renders no stale private content. */
    public static final class Snapshot {
        private final Object frame;private final Projection projection;private final List<Action> actions;
        private boolean consumed;
        private Snapshot(Object frame,Projection projection,List<Action> actions){
            this.frame=frame;this.projection=projection;this.actions=Collections.unmodifiableList(new ArrayList<>(actions));
        }
        public static Snapshot of(Projection projection,List<Action> offeredActions){
            if(projection==null || offeredActions==null)throw new IllegalArgumentException("Missing notification projection/actions");
            if(offeredActions.size()>MAX_ACTIONS)throw new IllegalArgumentException("Too many notification actions");
            Set<ActionKind> kinds=EnumSet.noneOf(ActionKind.class);
            List<Action> copy=new ArrayList<>();
            for(Action action:offeredActions){
                if(action==null)throw new IllegalArgumentException("Null notification action");
                if(!kinds.add(action.kind()))throw new IllegalArgumentException("Duplicate notification action kind");
                copy.add(action);
            }
            return new Snapshot(new Object(),projection,copy);
        }
        /** Explicit empty frame: clears title/subtitle/actions instead of reusing old ones. */
        public static Snapshot empty(){return new Snapshot(new Object(),new Projection("","",false,0,-1),Collections.<Action>emptyList());}
        public Object frame(){return frame;}
        public Projection projection(){return projection;}
        public List<Action> actions(){return actions;}
        public boolean isEmpty(){return projection.title().isEmpty() && projection.artist().isEmpty() && actions.isEmpty();}
        public boolean consumed(){return consumed;}
        /** Owner takes this frame exactly once; later use must request a fresh snapshot. */
        public boolean take(){if(consumed)return false;consumed=true;return true;}
    }

    private MusicNotification(){}

    /** Construct (not post) a notification from ONE frame; a consumed snapshot is rejected. */
    public static Notification build(Context context,String channelId,int smallIconResourceId,Snapshot snapshot){
        if(context==null || snapshot==null)throw new IllegalArgumentException("Missing notification context/snapshot");
        if(smallIconResourceId==0)throw new IllegalArgumentException("A supplied small icon is required");
        if(snapshot.consumed())throw new IllegalStateException("Notification frame already consumed");
        Projection projection=snapshot.projection();
        String channel=channelId==null || channelId.isEmpty()?DEFAULT_CHANNEL_ID:channelId;
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,channel):new Notification.Builder(context);
        builder.setSmallIcon(smallIconResourceId)
                .setContentTitle(projection.title().isEmpty()?"音乐":projection.title())
                .setContentText(projection.artist())
                .setOnlyAlertOnce(true)
                .setOngoing(projection.playing())
                .setShowWhen(false);
        for(Action action:snapshot.actions())builder.addAction(new Notification.Action.Builder(0,label(action.kind()),action.intent()).build());
        // Progress is deliberately omitted: confirmed playback end is not established here.
        return builder.build();
    }

    /** Legacy convenience: one fresh frame from a projection, no actions. */
    public static Notification build(Context context,String channelId,int smallIconResourceId,Projection projection,Action[] suppliedActions){
        List<Action> actions=suppliedActions==null?Collections.<Action>emptyList():java.util.Arrays.asList(suppliedActions);
        return build(context,channelId,smallIconResourceId,Snapshot.of(projection,actions));
    }

    public static String label(ActionKind kind){
        if(kind==null)throw new IllegalArgumentException("Missing action kind");
        switch(kind){case PLAY:return "播放";case PAUSE:return "暂停";case NEXT:return "下一首";case PREVIOUS:return "上一首";default:throw new IllegalArgumentException("Unsupported action");}
    }

    private static String text(String value,int max){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException("Unbounded notification text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c))throw new IllegalArgumentException("Control in notification text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
}
