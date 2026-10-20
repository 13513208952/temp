import io.github.rebrowser.bridge.BridgeMessages;
import io.github.rebrowser.music.MusicLyrics;
import io.github.rebrowser.outlook.MailSearch;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Follow-up deformation probes for the three reviewed fixes. Not part of the submission. */
public final class D02FollowupProbes {
    private static int pass, fail;
    private static final List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        mailSearchChunks();
        lyricsFractions();
        wordOrdering();
        numericDigits();
        System.out.println("FOLLOWUP pass=" + pass + " fail=" + fail);
        for (String f : failures) System.out.println("FAILED: " + f);
        if (fail != 0) System.exit(1);
    }

    private static void check(String name, boolean ok) {
        if (ok) pass++; else { fail++; failures.add(name); }
    }
    private static void rejects(String name, Runnable action) {
        try { action.run(); fail++; failures.add(name + " (no rejection)"); }
        catch (IllegalArgumentException expected) { pass++; }
        catch (RuntimeException other) { fail++; failures.add(name + " (unexpected " + other.getClass().getSimpleName() + ")"); }
    }

    // ---- MailSearch: identical body over different Reader chunking -------------
    private static Reader chunked(String input, int chunkSize) {
        return new Reader() {
            private int at;
            @Override public int read(char[] dest, int offset, int length) {
                if (at >= input.length()) return -1;
                int count = Math.min(Math.min(chunkSize, length), input.length() - at);
                input.getChars(at, at + count, dest, offset);
                at += count;
                return count;
            }
            @Override public void close() {}
        };
    }
    private static Reader randomChunks(String input, Random random) {
        return new Reader() {
            private int at;
            @Override public int read(char[] dest, int offset, int length) {
                if (at >= input.length()) return -1;
                int want = 1 + random.nextInt(7);
                int count = Math.min(Math.min(want, length), input.length() - at);
                input.getChars(at, at + count, dest, offset);
                at += count;
                return count;
            }
            @Override public void close() {}
        };
    }
    private static List<Long> expectedOffsets(String text, String needle, int limit) {
        List<Long> offsets = new ArrayList<>();
        int from = 0;
        while (offsets.size() < limit) {
            int index = text.indexOf(needle, from);
            if (index < 0) break;
            offsets.add((long) index);
            from = index + needle.length();
        }
        return offsets;
    }
    private static void compareSearch(String label, String text, String needle, int max, Reader reader) throws Exception {
        MailSearch.Result result = MailSearch.search(reader, needle, max, () -> false);
        List<Long> expected = expectedOffsets(text, needle, max);
        List<Long> actual = new ArrayList<>();
        for (MailSearch.Match match : result.matches()) actual.add(match.offset());
        check(label + " offsets", expected.equals(actual));
        int total = expectedOffsets(text, needle, Integer.MAX_VALUE).size();
        // "truncated" means the scan stopped at the cap/budget, not necessarily that a further match exists.
        boolean limitReached = total >= max;
        check(label + " truncation flag", result.truncated() == limitReached);
        for (MailSearch.Match match : result.matches()) {
            check(label + " snippet literal", match.snippet().contains(needle));
            check(label + " snippet bounded", match.snippet().length() <= MailSearch.MAX_SNIPPET_CHARS + needle.length());
        }
    }
    private static void mailSearchChunks() throws Exception {
        String[][] cases = {
                {"aaa", "aa"}, {"aaaa", "aa"}, {"aaaaa", "aa"}, {"aaaaaa", "aaa"},
                {"abababa", "aba"}, {"xxxxx", "xx"}, {"中文中文中", "中文"},
                {"😀😀😀", "😀"}, {"a😀a😀a", "😀a"}, {"abcabcabc", "abcabc"},
                {"a", "aa"}, {"", "a"},
        };
        for (String[] testCase : cases) {
            String text = testCase[0], needle = testCase[1];
            compareSearch("whole " + text + "/" + needle, text, needle, 10, chunked(text, Integer.MAX_VALUE));
            for (int size : new int[]{1, 2, 3, 7}) compareSearch(size + "-chunk " + text + "/" + needle, text, needle, 10, chunked(text, size));
            compareSearch("random " + text + "/" + needle, text, needle, 10, randomChunks(text, new Random(text.hashCode())));
        }
        // Result limit must agree with a non-overlapping scan even across chunks.
        compareSearch("limit-2 aaaaa/aa", "aaaaa", "aa", 2, chunked("aaaaa", 1));
        compareSearch("limit-1 aaaaa/aa", "aaaaa", "aa", 1, chunked("aaaaa", 2));
        // Longer pseudo-random text with a repeated self-overlapping needle.
        Random random = new Random(23);
        StringBuilder text = new StringBuilder();
        String alphabet = "aab中😀";
        for (int i = 0; i < 3000; i++) text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        String body = text.toString();
        for (String needle : new String[]{"aa", "aba", "中a", "😀a", "aab"}) {
            compareSearch("long random " + needle, body, needle, 200, randomChunks(body, new Random(needle.hashCode())));
            compareSearch("long 1-byte " + needle, body, needle, 200, chunked(body, 1));
        }
    }

    // ---- MusicLyrics: fraction precision and ordering --------------------------
    private static long firstLineMs(String lrc) {
        MusicLyrics.Lyrics lyrics = MusicLyrics.parseLrc(lrc);
        check("one line parsed for " + lrc, lyrics.lines().size() == 1);
        return lyrics.lines().get(0).timeMs();
    }
    private static void lyricsFractions() {
        check("no dot = 1000ms", firstLineMs("[00:01]x") == 1000);
        check("1 digit .5 = 1500ms", firstLineMs("[00:01.5]x") == 1500);
        check("2 digits .05 = 1050ms", firstLineMs("[00:01.05]x") == 1050);
        check("3 digits .005 = 1005ms", firstLineMs("[00:01.005]x") == 1005);
        check("3 digits .999 = 1999ms", firstLineMs("[00:01.999]x") == 1999);
        check("zero stamp", firstLineMs("[00:00.000]x") == 0);
        check("59.999", firstLineMs("[00:59.999]x") == 59_999);
        check("3-digit minutes", firstLineMs("[999:59.999]x") == 999L * 60_000 + 59_999);
        // 4 fraction digits and invalid seconds are ignored rather than guessed.
        check("4 fraction digits ignored", MusicLyrics.parseLrc("[00:01.1234]x").lines().isEmpty());
        check("seconds 60 ignored", MusicLyrics.parseLrc("[00:60.0]x").lines().isEmpty());
        check("dot without fraction should be ignored", MusicLyrics.parseLrc("[00:01.]x").lines().isEmpty());
        check("dot without fraction keeps no fake line", MusicLyrics.parseLrc("[00:02.]x\n[00:03.0]ok").lines().size() == 1);
    }

    private static void wordOrdering() {
        List<MusicLyrics.Word> words = MusicLyrics.parseWords(
                "3000\t3500\tc\n1000\t2000\ta\n1000\t1500\tb\n4000\t4500\td");
        MusicLyrics.Lyrics lyrics = MusicLyrics.parse("[00:00.000]x", words);
        check("sorted by start", lyrics.words().get(0).text().equals("a") && lyrics.words().get(1).text().equals("b")
                && lyrics.words().get(2).text().equals("c") && lyrics.words().get(3).text().equals("d"));
        check("tie keeps input order", lyrics.words().get(0).text().equals("a") && lyrics.words().get(1).text().equals("b"));
        check("overlap detected after sorting", lyrics.overlappingWords());
        MusicLyrics.Lyrics adjacent = MusicLyrics.parse("", MusicLyrics.parseWords("0\t1000\ta\n1000\t2000\tb"));
        check("adjacent ranges are not overlapping", !adjacent.overlappingWords());
        check("wordIndexAt uses sorted order", lyrics.wordIndexAt(1200) == 1);
        check("wordIndexAt before first is -1", adjacent.wordIndexAt(0) == 0 && lyrics.wordIndexAt(0) == -1);
        MusicLyrics.Lyrics lines = MusicLyrics.parseLrc("[00:02.00]second\n[00:01.00]first\n[00:02.00]third");
        check("LRC lines sorted stably", lines.lines().get(0).text().equals("first")
                && lines.lines().get(1).text().equals("second") && lines.lines().get(2).text().equals("third"));
        rejects("negative word start", () -> MusicLyrics.parseWords("-1\t10\tx"));
        rejects("word end before start", () -> MusicLyrics.parseWords("10\t5\tx"));
        rejects("word beyond max time", () -> MusicLyrics.parseWords("0\t" + (MusicLyrics.MAX_TIME_MS + 1) + "\tx"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < MusicLyrics.MAX_WORDS; i++) many.append(i * 10).append('\t').append(i * 10 + 5).append("\tw\n");
        check("max words accepted", MusicLyrics.parseWords(many.toString()).size() == MusicLyrics.MAX_WORDS);
        many.append("0\t1\tover\n");
        rejects("word limit enforced", () -> MusicLyrics.parseWords(many.toString()));
    }

    // ---- BridgeMessages: Unicode digits at every numeric position --------------
    private static final String HEX32 = "0123456789abcdef0123456789abcdef";
    private static final String HEX64 = HEX32 + HEX32;
    private static String binding() {
        return "{\"dataEpoch\":\"" + HEX32 + "\",\"partitionRef\":\"p1\",\"partitionGeneration\":0,"
                + "\"sessionRef\":\"s1\",\"sessionGeneration\":1,\"documentLease\":\"" + HEX64 + "\",\"portLeaseToken\":\"" + HEX64 + "\"}";
    }
    private static String event(String sequenceLiteral) {
        return "{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"EVENT\",\"binding\":" + binding()
                + ",\"eventSequence\":" + sequenceLiteral + ",\"eventType\":\"t\",\"resourceRef\":\"r\",\"payload\":{}}";
    }
    private static void numericDigits() {
        String[] unicodeDigits = {"\u0661", "\u06f1", "\u0967", "\uff11"}; // Arabic-Indic, Extended Arabic-Indic, Devanagari, Fullwidth
        for (String digit : unicodeDigits) {
            rejects("unicode digit integer " + (int) digit.charAt(0), () -> BridgeMessages.parse(event(digit)));
            rejects("unicode digit negative " + (int) digit.charAt(0), () -> BridgeMessages.parse(event("-" + digit)));
            rejects("unicode digit fraction " + (int) digit.charAt(0), () -> BridgeMessages.parse(event("1." + digit)));
            rejects("unicode digit exponent " + (int) digit.charAt(0), () -> BridgeMessages.parse(event("1e" + digit)));
            rejects("unicode digit exponent sign " + (int) digit.charAt(0), () -> BridgeMessages.parse(event("1e+" + digit)));
            rejects("unicode digit after zero " + (int) digit.charAt(0), () -> BridgeMessages.parse(event("0" + digit)));
            rejects("unicode digit in args " + (int) digit.charAt(0), () -> BridgeMessages.parse(
                    "{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"COMMAND\",\"binding\":" + binding()
                            + ",\"requestId\":\"r_" + HEX32 + "\",\"action\":\"a\",\"args\":{\"n\":" + digit + "}}"));
        }
        check("ascii zero accepted", BridgeMessages.parse(event("0")).event().eventSequence() == 0);
        check("ascii negative zero equals zero", BridgeMessages.parse(event("-0")).event().eventSequence() == 0);
        check("ascii fraction rejected as sequence", rejectsValue(() -> BridgeMessages.parse(event("1.0"))));
        check("ascii exponent rejected as sequence", rejectsValue(() -> BridgeMessages.parse(event("1E2"))));
        // A fraction/exponent is legal JSON but not an exact integer sequence.
        BridgeMessages.parse("{\"protocol\":\"rebrowser.connector/1\",\"kind\":\"COMMAND\",\"binding\":" + binding()
                + ",\"requestId\":\"r_" + HEX32 + "\",\"action\":\"a\",\"args\":{\"ratio\":0.5,\"exp\":1e-2}}");
        pass++;
    }
    private static boolean rejectsValue(Runnable action) {
        try { action.run(); return false; } catch (IllegalArgumentException expected) { return true; }
    }
}
