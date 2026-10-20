package io.github.rebrowser.outlook;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Bounded LITERAL (not regex) search over a Reader the native owner already authorized. It does
 * not open a database/file, decrypt, export or modify mail; returned offsets/snippets are display
 * results for the owner, never account scope or content authority.
 */
public final class MailSearch {
    public static final int CHUNK_CHARS = 8192;
    public static final int MAX_NEEDLE_CHARS = 64;
    public static final int MAX_RESULTS = 200;
    public static final long MAX_SCANNED_CHARS = 8L * 1024 * 1024;
    public static final int MAX_SNIPPET_CHARS = 120;

    public static final class Match {
        private final long offset;private final String snippet;
        Match(long offset,String snippet){this.offset=offset;this.snippet=snippet;}
        public long offset(){return offset;}
        public String snippet(){return snippet;}
    }
    public static final class Result {
        private final List<Match> matches;private final boolean truncated;private final long scannedChars;
        private Result(List<Match> matches,boolean truncated,long scanned){this.matches=Collections.unmodifiableList(matches);this.truncated=truncated;scannedChars=scanned;}
        public List<Match> matches(){return matches;}
        /** Scan stopped at the result limit or byte budget; absence beyond it is not proof. */
        public boolean truncated(){return truncated;}
        public long scannedChars(){return scannedChars;}
    }
    public static final class Page {
        private final List<Match> matches;private final int nextIndex;private final boolean more;
        Page(List<Match> matches,int nextIndex,boolean more){this.matches=Collections.unmodifiableList(matches);this.nextIndex=nextIndex;this.more=more;}
        public List<Match> matches(){return matches;}
        public int nextIndex(){return nextIndex;}
        public boolean more(){return more;}
    }

    public static Result search(Reader authorizedReader,String literal,int maxResults,BooleanSupplier cancelled) throws IOException {
        if(authorizedReader==null || literal==null || cancelled==null)throw new IllegalArgumentException("Authorized reader, literal and cancellation source required");
        String needle=literal;
        if(needle.isEmpty() || needle.length()>MAX_NEEDLE_CHARS)throw new IllegalArgumentException("Invalid literal length");
        text(needle);
        if(maxResults<1 || maxResults>MAX_RESULTS)throw new IllegalArgumentException("Invalid result limit");
        List<Match> matches=new ArrayList<>();
        char[] buffer=new char[CHUNK_CHARS];
        StringBuilder window=new StringBuilder();
        long consumed=0,windowStart=0,nextEligibleOffset=0;
        boolean truncated=false;
        int read;
        while(true){
            if(cancelled.getAsBoolean())throw new IOException("Search cancelled");
            read=authorizedReader.read(buffer,0,buffer.length);
            if(read<0)break;
            if(read==0)throw new IOException("Reader made no progress");
            if(consumed+read>MAX_SCANNED_CHARS){truncated=true;break;}
            window.append(buffer,0,read);
            consumed+=read;
            int searchFrom=0;
            while(true){
                int index=window.indexOf(needle,searchFrom);
                if(index<0)break;
                long absolute=windowStart+index;
                // A retained prefix may overlap the previous match; do not re-emit it.
                if(absolute<nextEligibleOffset){searchFrom=index+1;continue;}
                matches.add(new Match(absolute,snippet(window,index,needle.length())));
                nextEligibleOffset=absolute+needle.length();
                if(matches.size()>=maxResults){truncated=true;break;}
                searchFrom=index+needle.length();
            }
            if(truncated)break;
            int keep=Math.min(window.length(),Math.max(0,needle.length()-1));
            window.delete(0,window.length()-keep);
            windowStart=consumed-window.length(); // absolute offset of the retained tail
        }
        return new Result(matches,truncated,consumed);
    }

    /** Deterministic paging over an already computed bounded result; no re-read. */
    public static Page page(Result result,int startIndex,int pageSize){
        if(result==null || startIndex<0 || pageSize<1 || pageSize>MAX_RESULTS || startIndex>result.matches.size())throw new IllegalArgumentException("Invalid result page");
        int end=Math.min(result.matches.size(),startIndex+pageSize);
        return new Page(new ArrayList<>(result.matches.subList(startIndex,end)),end,end<result.matches.size());
    }

    private static String snippet(CharSequence text,int index,int needleLength){
        int start=Math.max(0,index-MAX_SNIPPET_CHARS/2);
        int end=Math.min(text.length(),index+needleLength+MAX_SNIPPET_CHARS/2);
        if(start>0 && Character.isLowSurrogate(text.charAt(start)))start++;
        if(end<text.length() && Character.isLowSurrogate(text.charAt(end)))end--;
        String value=text.subSequence(start,end).toString();
        for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)) && value.charAt(i)!='\n' && value.charAt(i)!='\t'){value=value.replace(value.charAt(i),' ');}
        return value;
    }
    private static void text(String value){for(int i=0;i<value.length();i++){char c=value.charAt(i);if(Character.isHighSurrogate(c)){if(++i>=value.length() || !Character.isLowSurrogate(value.charAt(i)))throw new IllegalArgumentException("Broken UTF16 needle");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Broken UTF16 needle");}}
    private MailSearch(){}
}
