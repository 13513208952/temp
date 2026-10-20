package io.github.rebrowser.music;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded decode of ALREADY-PROVIDED encoded artwork bytes. No URL, path or content resolver is
 * touched and nothing is cached indefinitely. At most ONE JNI decode/undisposed result exists per
 * instance: the returned {@link OwnedBitmap} holds that slot until its bitmap is actually
 * recycled; a failed/unknown recycle keeps the slot busy instead of pretending to release it.
 */
public final class MusicArtwork {
    public static final int MAX_ENCODED_BYTES = 8 * 1024 * 1024;
    public static final long MAX_DECODED_PIXELS = 1024L * 1024L;
    public static final int MAX_DIMENSION = 4096;

    /** Pure, testable decode plan: signature sniffing and power-of-two sampling. */
    public static final class Plan {
        private final Format format;private final int width,height,sampleSize;
        private Plan(Format format,int width,int height,int sampleSize){this.format=format;this.width=width;this.height=height;this.sampleSize=sampleSize;}
        public Format format(){return format;}
        public int width(){return width;}
        public int height(){return height;}
        public int sampleSize(){return sampleSize;}
        public long decodedPixels(){return (long)(width/sampleSize)*(height/sampleSize);}
    }
    public enum Format { JPEG, PNG, WEBP, UNKNOWN }

    /** Signature check only; it makes no claim that the payload is valid or trusted. */
    public static Format sniff(byte[] encoded){
        if(encoded==null || encoded.length<12)return Format.UNKNOWN;
        if((encoded[0]&0xff)==0xff && (encoded[1]&0xff)==0xd8 && (encoded[2]&0xff)==0xff)return Format.JPEG;
        if((encoded[0]&0xff)==0x89 && encoded[1]=='P' && encoded[2]=='N' && encoded[3]=='G')return Format.PNG;
        if(encoded[0]=='R' && encoded[1]=='I' && encoded[2]=='F' && encoded[3]=='F' && encoded[8]=='W' && encoded[9]=='E' && encoded[10]=='B' && encoded[11]=='P')return Format.WEBP;
        return Format.UNKNOWN;
    }

    /** Sampling such that sampled pixels stay within the budget, capped by MAX_DIMENSION. */
    public static Plan plan(int width,int height){
        if(width<1 || height<1 || width>MAX_DIMENSION || height>MAX_DIMENSION)throw new IllegalArgumentException("Invalid artwork dimensions");
        int sample=1;
        while((long)Math.ceil((double)width/sample)*Math.ceil((double)height/sample)>MAX_DECODED_PIXELS){
            if(sample>=MAX_DIMENSION)throw new IllegalArgumentException("Artwork too large");
            sample*=2;
        }
        return new Plan(Format.UNKNOWN,width,height,sample);
    }

    /** One owned decode result; the slot stays busy until an ACTUAL recycle is observed. */
    public final class OwnedBitmap implements AutoCloseable {
        private final Bitmap bitmap;private boolean closed;private boolean released;
        private OwnedBitmap(Bitmap bitmap){this.bitmap=bitmap;}
        public Bitmap bitmap(){return bitmap;}
        public boolean isClosed(){return closed;}
        /** AutoCloseable form; unknown recycle keeps the slot busy (see {@link #release()}). */
        @Override public void close(){release();}
        /** Idempotent release; true only when the slot has actually been released. */
        public boolean release(){
            if(closed)return released;
            boolean recycled;
            try{bitmap.recycle();recycled=bitmap.isRecycled();}
            catch(RuntimeException unknown){return false;} // stays busy: release is NOT proven
            if(!recycled)return false;
            closed=true;
            released=released||releaseSlot();
            return released;
        }
    }

    private final AtomicLong latestGeneration=new AtomicLong();
    private final AtomicBoolean slotBusy=new AtomicBoolean();
    /** Claim a request; a later claim supersedes earlier ones. */
    public long begin(long generation){
        if(generation<1)throw new IllegalArgumentException("Invalid artwork generation");
        return latestGeneration.accumulateAndGet(generation,Math::max);
    }
    public long latestGeneration(){return latestGeneration.get();}
    public boolean busy(){return slotBusy.get();}

    /**
     * Decode at most one owned bitmap. Returns null when superseded, unreadable, oversized or when
     * the single slot is already busy; the slot is freed only after real cleanup for a null result.
     */
    public OwnedBitmap decodeOwned(byte[] encoded,long generation){
        if(encoded==null || encoded.length>MAX_ENCODED_BYTES)throw new IllegalArgumentException("Artwork bytes rejected");
        if(generation!=latestGeneration.get())return null;
        if(!slotBusy.compareAndSet(false,true))return null;
        Bitmap decoded=null;
        boolean keepSlot=false;
        try{
            Format format=sniff(encoded);
            if(format==Format.UNKNOWN)return null;
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(encoded,0,encoded.length,bounds);
            if(bounds.outWidth<1 || bounds.outHeight<1)return null;
            Plan plan=plan(bounds.outWidth,bounds.outHeight);
            if(generation!=latestGeneration.get())return null;
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=plan.sampleSize();
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
            if(decoded==null)return null;
            if(generation!=latestGeneration.get())return null;
            keepSlot=true;
            return new OwnedBitmap(decoded);
        }finally{
            if(!keepSlot){
                if(decoded!=null){
                    try{decoded.recycle();}catch(RuntimeException unknown){/* slot stays busy below */}
                    if(decoded.isRecycled())slotBusy.set(false);
                }else slotBusy.set(false);
            }
        }
    }

    private boolean releaseSlot(){
        if(!slotBusy.compareAndSet(true,false))return false;
        return true;
    }
}
