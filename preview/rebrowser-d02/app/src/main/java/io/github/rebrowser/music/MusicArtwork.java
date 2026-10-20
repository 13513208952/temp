package io.github.rebrowser.music;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounded decode of ALREADY-PROVIDED encoded artwork bytes. No URL, path or content resolver is
 * touched; nothing is cached indefinitely. The latest requested generation wins, a superseded
 * decode returns null, and recycling a bitmap is not treated as the whole decode chain ending.
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

    private final AtomicLong latestGeneration=new AtomicLong();
    /** Claim a request; a later claim supersedes earlier ones. Returns 0 for an invalid generation. */
    public long begin(long generation){
        if(generation<1)throw new IllegalArgumentException("Invalid artwork generation");
        return latestGeneration.accumulateAndGet(generation,Math::max);
    }

    /** Decode only after bounds inspection; superseded results are discarded, never recycled as success. */
    public Bitmap decode(byte[] encoded,long generation){
        if(encoded==null || encoded.length>MAX_ENCODED_BYTES)throw new IllegalArgumentException("Artwork bytes rejected");
        Format format=sniff(encoded);
        if(format==Format.UNKNOWN)return null;
        if(generation!=latestGeneration.get())return null;
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(encoded,0,encoded.length,bounds);
        if(bounds.outWidth<1 || bounds.outHeight<1)return null;
        Plan plan=plan(bounds.outWidth,bounds.outHeight);
        if(generation!=latestGeneration.get())return null;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=plan.sampleSize();
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap decoded=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
        if(decoded==null)return null;
        if(generation!=latestGeneration.get()){decoded.recycle();return null;}
        return decoded;
    }
    public long latestGeneration(){return latestGeneration.get();}
}
