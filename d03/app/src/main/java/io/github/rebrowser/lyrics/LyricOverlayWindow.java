package io.github.rebrowser.lyrics;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import io.github.rebrowser.ui.BrowserToolbar;

/**
 * Native lyric panel VIEW with pointer-id-correct dragging. It is a normal in-app view: it never
 * adds itself to the system window, changes global flags or obtains overlay permission. The
 * gesture is bound to the FIRST pointer id, a second finger cancels/ends it, POINTER_UP/CANCEL,
 * unbind, mode change and new bounds invalidate the old gesture, and a new window model can never
 * be written by a gesture that started on the old one.
 */
public final class LyricOverlayWindow extends FrameLayout {
    public static final int MAX_LINE_CHARS = 512;
    public static final int MAX_COORD = 32_768;

    /** Pure drag arithmetic in dp: clamped origin, single-pointer semantics per captured id. */
    public static final class DragModel {
        private final int displayWidthDp,displayHeightDp,panelWidthDp,panelHeightDp;
        private boolean active,cancelled;private int pointerId=-1,startX,startY,originX,originY;
        public DragModel(int displayWidthDp,int displayHeightDp,int panelWidthDp,int panelHeightDp){
            if(displayWidthDp<1 || displayHeightDp<1 || displayWidthDp>MAX_COORD || displayHeightDp>MAX_COORD)throw new IllegalArgumentException("Invalid display size");
            if(panelWidthDp<1 || panelHeightDp<1 || panelWidthDp>displayWidthDp || panelHeightDp>displayHeightDp)throw new IllegalArgumentException("Invalid panel size");
            this.displayWidthDp=displayWidthDp;this.displayHeightDp=displayHeightDp;this.panelWidthDp=panelWidthDp;this.panelHeightDp=panelHeightDp;
        }
        public void start(int pointerCount,int panelOriginX,int panelOriginY,int touchX,int touchY){
            start(0,pointerCount,panelOriginX,panelOriginY,touchX,touchY);
        }
        /** Capture the first pointer id; a different id later cannot drive this gesture. */
        public void start(int pointerId,int pointerCount,int panelOriginX,int panelOriginY,int touchX,int touchY){
            if(pointerCount!=1)throw new IllegalArgumentException("Single-pointer drag only");
            active=true;cancelled=false;this.pointerId=pointerId;
            originX=clampX(coord(panelOriginX));originY=clampY(coord(panelOriginY));
            startX=coord(touchX);startY=coord(touchY);
        }
        public void move(int pointerCount,int x,int y){
            if(!active)throw new IllegalStateException("Drag not started");
            if(cancelled)return;
            if(pointerCount!=1){cancel();return;}
            originX=clampX(originX+(coord(x)-startX));originY=clampY(originY+(coord(y)-startY));
            startX=coord(x);startY=coord(y);
        }
        /** Move only when the supplied id is the captured first pointer. */
        public void movePointer(int pointerId,int pointerCount,int x,int y){
            if(!active)throw new IllegalStateException("Drag not started");
            if(cancelled)return;
            if(pointerCount!=1 || (this.pointerId!=-1 && pointerId!=this.pointerId)){cancel();return;}
            move(pointerCount,x,y);
        }
        public void end(){if(!active)throw new IllegalStateException("Drag not started");active=false;pointerId=-1;}
        public void endPointer(int pointerId){
            if(!active)return;
            if(this.pointerId!=-1 && pointerId!=this.pointerId){cancel();return;}
            end();
        }
        /** Cancelling is idempotent even after a second finger already aborted the drag. */
        public void cancel(){cancelled=true;active=false;pointerId=-1;}
        public boolean cancelled(){return cancelled;}
        public boolean active(){return active;}
        public int pointerId(){return pointerId;}
        public int x(){return originX;}
        public int y(){return originY;}
        private int clampX(int value){return Math.min(Math.max(0,value),displayWidthDp-panelWidthDp);}
        private int clampY(int value){return Math.min(Math.max(0,value),displayHeightDp-panelHeightDp);}
        private static int coord(int value){if(value< -MAX_COORD || value>MAX_COORD)throw new IllegalArgumentException("Coordinate out of range");return value;}
    }

    /** Plain display projection supplied by the official lyric owner; never HTML. */
    public static final class Display {
        private final String currentLine,nextLine;private final LyricOverlayController.Mode mode;
        public Display(String currentLine,String nextLine,LyricOverlayController.Mode mode){
            this.currentLine=text(currentLine,MAX_LINE_CHARS);this.nextLine=text(nextLine,MAX_LINE_CHARS);
            if(mode==null)throw new IllegalArgumentException("Missing overlay mode");
            this.mode=mode;
        }
        public String currentLine(){return currentLine;}
        public String nextLine(){return nextLine;}
        public LyricOverlayController.Mode mode(){return mode;}
        public String modeLabel(){switch(mode){case VISIBLE_LOCKED:return "已锁定（仅模式）";case VISIBLE_UNLOCKED:return "已解锁";default:return "已关闭";}}
    }

    private final TextView current,next,modeLabel;
    private Button unbindButton;
    private DragModel drag;
    private BrowserToolbar.Binding binding;private Object frame;
    private int x,y;
    private int activePointerId=-1;

    public LyricOverlayWindow(Context context){
        super(context);
        ownerThread();
        setMinimumHeight(dp(96));
        current=new TextView(context);current.setTextSize(18);
        next=new TextView(context);next.setTextSize(14);
        modeLabel=new TextView(context);modeLabel.setTextSize(11);
        unbindButton=new Button(context);
        unbindButton.setText("隐藏歌词");
        unbindButton.setMinimumHeight(dp(48));
        addView(current);addView(next);addView(modeLabel);addView(unbindButton);
        setContentDescription("歌词浮层（应用内）");
    }

    public void bind(BrowserToolbar.Binding binding,Display display,DragModel model){
        ownerThread();
        if(binding==null || display==null || model==null)throw new IllegalArgumentException("Missing lyric overlay binding");
        binding.check();
        clearViewState();
        this.binding=binding;this.drag=model;this.frame=new Object();
        activePointerId=-1;
        current.setText(display.currentLine());
        next.setText(display.nextLine());
        modeLabel.setText(display.modeLabel()+" · 拖动调整位置（单指）");
        current.setContentDescription("当前歌词：" + display.currentLine());
        next.setContentDescription("下一句：" + display.nextLine());
        setOnTouchListener((view,event)->handleTouch(event));
    }

    /** Bounds/rotation change: the old gesture and old coordinates cannot survive it. */
    public void rebindBounds(DragModel model){
        ownerThread();
        if(model==null)throw new IllegalArgumentException("Missing drag model");
        drag=model;activePointerId=-1;
        setTranslationX(0);setTranslationY(0);
    }

    /** Called ONLY by the native window owner after it actually applied a drag origin. */
    public void showAppliedOrigin(int appliedX,int appliedY){
        ownerThread();
        if(frame==null || binding==null)return;
        try{binding.check();}catch(SecurityException | IllegalStateException stale){return;}
        x=appliedX;y=appliedY;
        setTranslationX(x*getResources().getDisplayMetrics().density);
        setTranslationY(y*getResources().getDisplayMetrics().density);
    }

    public void unbind(){
        ownerThread();
        clearViewState();
    }
    private void clearViewState(){
        binding=null;drag=null;frame=null;x=0;y=0;activePointerId=-1;
        setOnTouchListener(null);
        current.setText("");next.setText("");modeLabel.setText("");
        current.setContentDescription(null);next.setContentDescription(null);modeLabel.setContentDescription(null);
        if(unbindButton!=null){unbindButton.setOnClickListener(null);unbindButton.setContentDescription(null);}
        setTranslationX(0);setTranslationY(0);
    }
    @Override protected void onDetachedFromWindow(){unbind();super.onDetachedFromWindow();}

    /** Local drag intent only; global/system window placement remains with the native owner. */
    boolean handleTouch(MotionEvent event){
        DragModel model=drag;BrowserToolbar.Binding captured=binding;Object exactFrame=frame;
        if(event==null || model==null || captured==null || exactFrame==null)return false;
        try{
            captured.check();
            if(frame!=exactFrame || binding!=captured)return false;
            float density=getResources().getDisplayMetrics().density;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    activePointerId=event.getPointerId(0);
                    model.start(activePointerId,event.getPointerCount(),x,y,(int)(event.getX()/density),(int)(event.getY()/density));
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    model.cancel();activePointerId=-1;return true;
                case MotionEvent.ACTION_MOVE:{
                    int index=event.findPointerIndex(activePointerId);
                    if(index<0 || event.getPointerCount()!=1){model.cancel();activePointerId=-1;return true;}
                    if(!model.active())return true;
                    model.movePointer(event.getPointerId(index),event.getPointerCount(),(int)(event.getX(index)/density),(int)(event.getY(index)/density));
                    return true;
                }
                case MotionEvent.ACTION_POINTER_UP:{
                    int index=event.getActionIndex();
                    if(event.getPointerId(index)==activePointerId){model.endPointer(activePointerId);activePointerId=-1;}
                    else{model.cancel();activePointerId=-1;}
                    return true;
                }
                case MotionEvent.ACTION_UP:{
                    if(!model.active())return true;
                    model.end();
                    setContentDescription("拖动待原生应用（" + model.x() + "," + model.y() + "）");
                    activePointerId=-1;
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    model.cancel();activePointerId=-1;return true;
                default:return false;
            }
        }catch(RuntimeException invalidGesture){
            model.cancel();activePointerId=-1;
            return false;
        }
    }
    int appliedX(){return x;}
    int appliedY(){return y;}
    private static String text(String value,int max){
        if(value==null)return "";
        if(value.length()>max)throw new IllegalArgumentException("Unbounded lyric text");
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isISOControl(c) && c!='\n')throw new IllegalArgumentException("Control in lyric text");if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16");}
        return value;
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static void ownerThread(){if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Native UI owner thread required");}
}
