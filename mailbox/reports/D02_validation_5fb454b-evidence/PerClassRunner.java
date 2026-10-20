import java.util.*;
import org.junit.runner.*;
import org.junit.runner.notification.*;

/** Emits real per-class JUnit results as JSON; no test result is synthesised. */
public final class PerClassRunner {
    public static void main(String[] names) throws Exception {
        Map<String,int[]> perClass = new TreeMap<>();
        List<String> failures = new ArrayList<>();
        JUnitCore core = new JUnitCore();
        core.addListener(new RunListener() {
            private int[] row(Description d) { return perClass.computeIfAbsent(d.getClassName(), k -> new int[2]); }
            public void testFinished(Description d) { row(d)[0]++; }
            public void testFailure(Failure f) { row(f.getDescription())[1]++; failures.add(f.getDescription().getClassName()+"."+f.getDescription().getMethodName()+": "+f.getMessage()); }
        });
        Class<?>[] classes = new Class<?>[names.length];
        for (int i = 0; i < names.length; i++) classes[i] = Class.forName(names[i]);
        Result result = core.run(classes);
        StringBuilder json = new StringBuilder("{\"classes\":[");
        boolean first = true;
        for (Map.Entry<String,int[]> e : perClass.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"class\":\"").append(e.getKey()).append("\",\"tests\":").append(e.getValue()[0])
                .append(",\"failures\":").append(e.getValue()[1]).append('}');
        }
        json.append("],\"testsRun\":").append(result.getRunCount())
            .append(",\"failures\":").append(result.getFailureCount())
            .append(",\"ignored\":").append(result.getIgnoreCount())
            .append(",\"failureDetails\":[");
        for (int i = 0; i < failures.size(); i++) { if (i > 0) json.append(','); json.append('"').append(failures.get(i).replace("\\","\\\\").replace("\"","\\\"")).append('"'); }
        json.append("]}");
        System.out.println(json);
        System.exit(result.wasSuccessful() ? 0 : 1);
    }
}
