import io.github.rebrowser.bridge.BridgeBudget;
import io.github.rebrowser.bridge.BridgeMessages;
import io.github.rebrowser.lyrics.LyricOverlayWindow;
import io.github.rebrowser.outlook.MailObjectFormat;
import io.github.rebrowser.outlook.MailRetentionPolicy;
import io.github.rebrowser.outlook.MailSearch;
import io.github.rebrowser.system.ForegroundPolicy;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Independent P0/P2 probes over the reviewed D02 candidate. Not part of the submission. */
public final class D02Probes {
    private static int pass, fail;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        retention();
        bridge();
        foreground();
        drag();
        mailFormat();
        mailSearch();
        System.out.println("PROBES pass=" + pass + " fail=" + fail);
        for (String failure : failures) System.out.println("FAILED: " + failure);
        if (fail != 0) System.exit(1);
    }

    private static void check(String name, boolean condition) {
        if (condition) { pass++; } else { fail++; failures.add(name); }
    }
    private static void rejects(String name, Runnable action) {
        try { action.run(); fail++; failures.add(name + " (no rejection)"); }
        catch (IllegalArgumentException expected) { pass++; }
        catch (RuntimeException other) { fail++; failures.add(name + " (unexpected " + other.getClass().getSimpleName() + ")"); }
    }
    private static void rejectsIllegalState(String name, Runnable action) {
        try { action.run(); fail++; failures.add(name + " (no rejection)"); }
        catch (IllegalStateException expected) { pass++; }
    }

    // P0.3 ------------------------------------------------------------------
    private static void retention() {
        MailRetentionPolicy.Observation unknownBoth = new MailRetentionPolicy.Observation(true, null, null);
        check("derived+unknown refs stays UNKNOWN_KEEP",
                MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE, unknownBoth) == MailRetentionPolicy.Decision.UNKNOWN_KEEP);
        MailRetentionPolicy.Observation unknownDependents = new MailRetentionPolicy.Observation(true, null, false);
        check("derived+null dependents stays UNKNOWN_KEEP",
                MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE, unknownDependents) == MailRetentionPolicy.Decision.UNKNOWN_KEEP);
        MailRetentionPolicy.Observation unknownPending = new MailRetentionPolicy.Observation(true, false, null);
        check("derived+null pending stays UNKNOWN_KEEP",
                MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE, unknownPending) == MailRetentionPolicy.Decision.UNKNOWN_KEEP);
        MailRetentionPolicy.Observation complete = new MailRetentionPolicy.Observation(true, false, false);
        check("derived+all known true/false/false is candidate",
                MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.DERIVED_REBUILDABLE, complete) == MailRetentionPolicy.Decision.RECLAIM_CANDIDATE);
        for (Boolean dependents : new Boolean[]{null, Boolean.FALSE, Boolean.TRUE}) {
            for (Boolean pending : new Boolean[]{null, Boolean.FALSE, Boolean.TRUE}) {
                for (Boolean original : new Boolean[]{null, Boolean.FALSE, Boolean.TRUE}) {
                    MailRetentionPolicy.Decision unique = MailRetentionPolicy.classify(MailRetentionPolicy.ContentClass.UNIQUE_ORIGINAL,
                            new MailRetentionPolicy.Observation(original, dependents, pending));
                    check("unique original never reclaimed " + original + dependents + pending, unique == MailRetentionPolicy.Decision.KEEP);
                }
            }
        }
    }

    // P0.4 ------------------------------------------------------------------
    private static final String HEX32 = "0123456789abcdef0123456789abcdef";
    private static final String HEX64 = HEX32 + HEX32;
    private static String binding() {
        return "{\"dataEpoch\":\"" + HEX32 + "\",\"partitionRef\":\"p1\",\"partitionGeneration\":0,"
                + "\"sessionRef\":\"s1\",\"sessionGeneration\":1,\"documentLease\":\"" + HEX64 + "\",\"portLeaseToken\":\"" + HEX64 + "\"}";
    }
    private static String hello(String hint) {
        return "{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"HELLO\",\"connectorVersion\":\"1\",\"documentInstanceHint\":\"" + hint + "\"}";
    }
    private static void bridge() {
        rejects("parse(String) lone high surrogate", () -> BridgeMessages.parse(hello("\ud800")));
        rejects("parse(String) lone low surrogate", () -> BridgeMessages.parse(hello("\udc00")));
        rejects("parse(String) high surrogate at end", () -> BridgeMessages.parse(hello("x\ud83d")));
        String validPair = BridgeMessages.parse(hello("\ud83d\ude00")).hello().documentInstanceHint();
        check("valid surrogate pair preserved", validPair.equals("\ud83d\ude00"));
        rejects("parse(byte[]) invalid UTF-8", () -> BridgeMessages.parse(new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xc3, '"', '}'}));
        rejects("parse(byte[]) CESU-8 surrogate", () -> BridgeMessages.parse(new byte[]{'{', '"', 'a', '"', ':', '"', (byte) 0xed, (byte) 0xa0, (byte) 0x80, '"', '}'}));
        rejects("non-ASCII hex digit in \\u", () -> BridgeMessages.parse(hello("\\u" + "\u0663\u0663\u0663\u0663")));
        rejects("duplicate key", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"HELLO\",\"connectorVersion\":\"1\",\"connectorVersion\":\"2\",\"documentInstanceHint\":\"h\"}"));
        rejects("unknown field", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"HELLO\",\"connectorVersion\":\"1\",\"documentInstanceHint\":\"h\",\"x\":1}"));
        rejects("numbers with leading zero", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":" + binding() + ",\"eventSequence\":01,\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}"));
        rejects("non-finite exponent", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":" + binding() + ",\"eventSequence\":1e999,\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}"));
        rejects("fractional sequence", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":" + binding() + ",\"eventSequence\":1.0,\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}"));
        rejects("negative sequence", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":" + binding() + ",\"eventSequence\":-1,\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}"));
        rejects("over safe integer", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":" + binding() + ",\"eventSequence\":9007199254740992,\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}"));
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 16; i++) deep.append('[');
        deep.append('0');
        for (int i = 0; i < 16; i++) deep.append(']');
        rejects("deep array", () -> BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"COMMAND\",\"binding\":" + binding() + ",\"requestId\":\"r_" + HEX32 + "\",\"action\":\"a\",\"args\":" + deep + "}"));
        rejects("raw control character", () -> BridgeMessages.parse(hello("a\u0001b")));
        rejects("oversized envelope", () -> BridgeMessages.parse(hello(new String(new char[70_000]).replace('\0', 'h'))));
        BridgeMessages.Message ready = BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"READY\",\"binding\":" + binding() + ",\"limits\":{},\"supportedActions\":[]}");
        check("READY binding stays unverified", !ready.ready().binding().isVerified());
        check("HELLO stays unverified", !BridgeMessages.parse(hello("h")).hello().isVerified());
        BridgeBudget.Window window = new BridgeBudget.Window(65_536, 2);
        BridgeBudget.Credit credit = window.tryReserve(32_768);
        rejects("credit above window", () -> window.tryReserve(65_537));
        check("credit released once", credit.releaseAfterConsumerFreedBytes() && !credit.releaseAfterConsumerFreedBytes());
    }

    // P0.5 ------------------------------------------------------------------
    private static void foreground() {
        ForegroundPolicy.Decision deniedMedia = ForegroundPolicy.mediaPlaybackAdvice(33,
                ForegroundPolicy.Lifecycle.RESUMED, ForegroundPolicy.NotificationPermission.DENIED);
        check("media FGS not vetoed by POST_NOTIFICATIONS denial", deniedMedia.advice() == ForegroundPolicy.Advice.ALLOWED);
        ForegroundPolicy.Decision unknownMedia = ForegroundPolicy.mediaPlaybackAdvice(33,
                ForegroundPolicy.Lifecycle.STARTED, ForegroundPolicy.NotificationPermission.UNKNOWN);
        check("media FGS not blocked by unknown notification permission", unknownMedia.advice() == ForegroundPolicy.Advice.ALLOWED);
        check("paused lifecycle gives no background-start proof",
                ForegroundPolicy.mediaPlaybackAdvice(33, ForegroundPolicy.Lifecycle.PAUSED, ForegroundPolicy.NotificationPermission.GRANTED).reason()
                        == ForegroundPolicy.Reason.BACKGROUND_START_NOT_PROVEN);
        check("not-started lifecycle blocked",
                ForegroundPolicy.mediaPlaybackAdvice(33, ForegroundPolicy.Lifecycle.NOT_STARTED, ForegroundPolicy.NotificationPermission.GRANTED).reason()
                        == ForegroundPolicy.Reason.SERVICE_NOT_STARTED);
        check("short op outside foreground blocked",
                ForegroundPolicy.shortOperationAdvice(33, ForegroundPolicy.Lifecycle.RESUMED, ForegroundPolicy.NotificationPermission.GRANTED, false).reason()
                        == ForegroundPolicy.Reason.SHORT_OPERATION_NEEDS_APP_FOREGROUND);
        check("sdk 29 blocked",
                ForegroundPolicy.dataSyncAdvice(29, ForegroundPolicy.Lifecycle.RESUMED, ForegroundPolicy.NotificationPermission.GRANTED).advice()
                        == ForegroundPolicy.Advice.BLOCKED);
    }

    // P0.6 ------------------------------------------------------------------
    private static void drag() {
        LyricOverlayWindow.DragModel model = new LyricOverlayWindow.DragModel(1080, 2400, 360, 200);
        model.start(1, 0, 0, 10, 10);
        model.move(1, 50, 60);
        model.move(2, 80, 90);
        check("multi-touch cancels", model.cancelled() && !model.active());
        model.cancel();
        check("cancel is idempotent", model.cancelled() && !model.active());
        rejectsIllegalState("end after cancel does not double-end", model::end);
        check("no movement applied after cancel", model.x() == 40 && model.y() == 50);
        LyricOverlayWindow.DragModel second = new LyricOverlayWindow.DragModel(1080, 2400, 360, 200);
        second.start(1, 0, 0, 10, 10);
        second.end();
        rejectsIllegalState("second end rejected", second::end);
    }

    // P2.3 ------------------------------------------------------------------
    private static void mailFormat() {
        Random random = new Random(7);
        for (int i = 0; i < 500; i++) {
            byte[] raw = new byte[124];
            random.nextBytes(raw);
            boolean accepted;
            try { MailObjectFormat.decode(raw); accepted = true; } catch (IllegalArgumentException expected) { accepted = false; }
            check("random 124-byte header rejected", !accepted);
        }
        byte[] epoch = new byte[16], account = new byte[16], object = new byte[16], digest = new byte[32];
        random.nextBytes(epoch); random.nextBytes(account); random.nextBytes(object); random.nextBytes(digest);
        for (long packed : new long[]{0, 1, 32_767, 32_768, 32_769, 65_536, 1_000_000}) {
            MailObjectFormat.Header header = MailObjectFormat.header(MailObjectFormat.Codec.ZLIB, MailObjectFormat.Purpose.BODY, 7,
                    epoch, account, object, packed == 0 ? 0 : packed + 10, packed, digest);
            byte[] encoded = MailObjectFormat.encode(header);
            MailObjectFormat.Header decoded = MailObjectFormat.decode(encoded);
            check("roundtrip packed=" + packed, MailObjectFormat.matchesCatalog(decoded, header));
            check("stored bytes packed=" + packed, decoded.storedBytes() == 124 + packed + decoded.frameCount() * 20);
        }
        rejects("frames overflow beyond u32*32768", () -> MailObjectFormat.frames(MailObjectFormat.MAX_PACKED_BYTES + 1));
        long max = MailObjectFormat.MAX_PACKED_BYTES;
        check("max packed still one u32 frame count", MailObjectFormat.frames(max) == 0xffff_ffffL);
        check("empty object is one frame", MailObjectFormat.frames(0) == 1);
    }

    private static void mailSearch() {
        Random random = new Random(11);
        for (int round = 0; round < 60; round++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(40_000);
            for (int i = 0; i < length; i++) text.append((char) ('a' + random.nextInt(3)));
            String needle = random.nextBoolean() ? "ab" : "中文";
            int at = length == 0 ? -1 : random.nextInt(length);
            if (at >= 0 && needle.equals("中文")) text.insert(at, needle);
            else if (at >= 0) text.insert(at, needle);
            MailSearch.Result result;
            try { result = MailSearch.search(new StringReader(text.toString()), needle, 200, () -> false); }
            catch (Exception failure) { fail++; failures.add("search threw " + failure); continue; }
            String haystack = text.toString();
            int verified = 0, from = 0;
            for (MailSearch.Match match : result.matches()) {
                int index = haystack.indexOf(needle, from);
                check("reported offset exists at/after previous", index >= 0 && index == match.offset());
                if (index < 0 || index != match.offset()) break;
                check("snippet contains literal", match.snippet().contains(needle));
                from = index + needle.length();
                verified++;
            }
            check("match count consistent", verified == result.matches().size());
            check("matches non-overlapping ordered", ordered(result.matches()));
        }
        try {
            MailSearch.Result cancelled = MailSearch.search(new StringReader("abcdef"), "a", 10, () -> true);
            fail++; failures.add("cancellation ignored");
        } catch (java.io.IOException expected) { pass++; }
    }
    private static boolean ordered(List<MailSearch.Match> matches) {
        long previous = -1;
        for (MailSearch.Match match : matches) { if (match.offset() <= previous) return false; previous = match.offset(); }
        return true;
    }
}
