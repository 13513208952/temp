package floatingpanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A platform-independent, immutable normalized position for a floating panel.
 * Geometry and decoding never access persistence. Only persist() writes.
 */
public final class FloatingPanelPosition {
    public static final double DEFAULT_X_FRACTION = 0.5d;
    public static final double DEFAULT_Y_FRACTION = 0.25d;
    public static final double MIN_FRACTION = 0d;
    public static final double MAX_FRACTION = 1d;
    private static final int MAX_STORED_CHARS = 512;
    private static final int MAX_STORED_FIELDS = 16;

    private final double x;
    private final double y;

    private FloatingPanelPosition(double x, double y) { this.x = x; this.y = y; }

    public static FloatingPanelPosition defaults() {
        return new FloatingPanelPosition(DEFAULT_X_FRACTION, DEFAULT_Y_FRACTION);
    }
    public static FloatingPanelPosition of(double x, double y) {
        return ofExplained(x, y).position();
    }
    public static RestoreResult ofExplained(double x, double y) {
        List<String> issues = new ArrayList<>();
        return new RestoreResult(new FloatingPanelPosition(
                clean(x, DEFAULT_X_FRACTION, "x", issues),
                clean(y, DEFAULT_Y_FRACTION, "y", issues)), issues);
    }
    public double xFraction() { return x; }
    public double yFraction() { return y; }

    public enum HorizontalLayout { CENTERED_X, FREE_X }

    public static final class LayoutArea {
        private final int left, top, width, height;
        private LayoutArea(int left, int top, int width, int height) {
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
        public static LayoutArea of(int left, int top, int width, int height) {
            // Negative sizes become zero. Right/bottom must remain int-representable.
            int w = Math.max(0, width), h = Math.max(0, height);
            if ((long) left + w > Integer.MAX_VALUE) w = Integer.MAX_VALUE - left;
            if ((long) top + h > Integer.MAX_VALUE) h = Integer.MAX_VALUE - top;
            return new LayoutArea(left, top, w, h);
        }
        public int left() { return left; }
        public int top() { return top; }
        public int width() { return width; }
        public int height() { return height; }
        public int right() { return left + width; }
        public int bottom() { return top + height; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof LayoutArea)) return false;
            LayoutArea a = (LayoutArea) o;
            return left == a.left && top == a.top && width == a.width && height == a.height;
        }
        @Override public int hashCode() { return Objects.hash(left, top, width, height); }
        @Override public String toString() { return "LayoutArea{" + left + "," + top + "," + width + "," + height + "}"; }
    }

    public static final class Placement {
        private final int left, top, width, height;
        private final double xFraction, yFraction;
        private final boolean sizeClamped;
        private Placement(int left, int top, int width, int height,
                          double xFraction, double yFraction, boolean sizeClamped) {
            this.left = left; this.top = top; this.width = width; this.height = height;
            this.xFraction = xFraction; this.yFraction = yFraction; this.sizeClamped = sizeClamped;
        }
        public int left() { return left; }
        public int top() { return top; }
        public int width() { return width; }
        public int height() { return height; }
        public int right() { return left + width; }
        public int bottom() { return top + height; }
        public double xFraction() { return xFraction; }
        public double yFraction() { return yFraction; }
        public boolean sizeClamped() { return sizeClamped; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Placement)) return false;
            Placement p = (Placement) o;
            return left == p.left && top == p.top && width == p.width && height == p.height;
        }
        @Override public int hashCode() { return Objects.hash(left, top, width, height); }
        @Override public String toString() { return "Placement{" + left + "," + top + "," + width + "," + height + "}"; }
    }

    public Placement place(LayoutArea a, int panelWidth, int panelHeight, HorizontalLayout layout) {
        Objects.requireNonNull(a, "area");
        Objects.requireNonNull(layout, "layout");
        int width = clipSize(panelWidth, a.width), height = clipSize(panelHeight, a.height);
        int slackX = a.width - width, slackY = a.height - height;
        int localX = layout == HorizontalLayout.CENTERED_X ? slackX / 2 : toPixel(x, slackX);
        int localY = toPixel(y, slackY);
        return new Placement(a.left + localX, a.top + localY, width, height, x, y,
                width != Math.max(0, panelWidth) || height != Math.max(0, panelHeight));
    }

    /** targetLeft/Top are absolute pixel positions; the caller owns gesture authorization. */
    public FloatingPanelPosition dragTo(LayoutArea a, int panelWidth, int panelHeight,
                                        HorizontalLayout layout, int targetLeft, int targetTop) {
        Objects.requireNonNull(a, "area");
        Objects.requireNonNull(layout, "layout");
        int slackX = a.width - clipSize(panelWidth, a.width);
        int slackY = a.height - clipSize(panelHeight, a.height);
        double nextX = layout == HorizontalLayout.CENTERED_X || slackX == 0 ? x
                : (double) clampLong((long) targetLeft - a.left, 0, slackX) / slackX;
        double nextY = slackY == 0 ? y
                : (double) clampLong((long) targetTop - a.top, 0, slackY) / slackY;
        return new FloatingPanelPosition(nextX, nextY);
    }

    public interface PositionStorage { String read(); void write(String value); }

    public static final class RestoreResult {
        private final FloatingPanelPosition position;
        private final List<String> issues;
        private RestoreResult(FloatingPanelPosition position, List<String> issues) {
            this.position = position;
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }
        public FloatingPanelPosition position() { return position; }
        public List<String> issues() { return issues; }
        public boolean hadIssues() { return !issues.isEmpty(); }
        @Override public String toString() { return "RestoreResult{position=" + position + ",issues=" + issues + "}"; }
    }

    public String toStorageValue() { return "x=" + Double.toString(x) + ";y=" + Double.toString(y); }
    public void persist(PositionStorage storage) {
        Objects.requireNonNull(storage, "storage").write(toStorageValue());
    }
    public static RestoreResult restore(PositionStorage storage) {
        return fromStorageValue(Objects.requireNonNull(storage, "storage").read());
    }

    /**
     * First occurrence wins for duplicate x/y fields, ignoring case.
     * Bad axes fall back independently; no arbitrary input is copied to diagnostics.
     */
    public static RestoreResult fromStorageValue(String raw) {
        List<String> issues = new ArrayList<>(4);
        if (raw == null || raw.trim().isEmpty()) {
            issues.add("stored value is absent; using defaults");
            return new RestoreResult(defaults(), issues);
        }
        if (raw.length() > MAX_STORED_CHARS) {
            issues.add("stored value exceeds size limit; using defaults");
            return new RestoreResult(defaults(), issues);
        }
        String[] fields = raw.trim().split(";", -1);
        if (fields.length > MAX_STORED_FIELDS) {
            issues.add("stored value has too many fields; using defaults");
            return new RestoreResult(defaults(), issues);
        }
        Double px = null, py = null;
        boolean seenX = false, seenY = false;
        for (String field : fields) {
            String item = field.trim();
            if (item.isEmpty()) continue;
            int eq = item.indexOf('=');
            if (eq < 0) {
                issues.add("ignored segment without '='");
                continue;
            }
            String key = item.substring(0, eq).trim();
            String value = item.substring(eq + 1).trim();
            if ("x".equalsIgnoreCase(key)) {
                if (seenX) { issues.add("duplicate x ignored; first value retained"); continue; }
                seenX = true;
                px = parse(value, DEFAULT_X_FRACTION, "x", issues);
            } else if ("y".equalsIgnoreCase(key)) {
                if (seenY) { issues.add("duplicate y ignored; first value retained"); continue; }
                seenY = true;
                py = parse(value, DEFAULT_Y_FRACTION, "y", issues);
            } else {
                issues.add("ignored unknown key");
            }
        }
        if (!seenX) issues.add("x is missing; fell back to default " + DEFAULT_X_FRACTION);
        if (!seenY) issues.add("y is missing; fell back to default " + DEFAULT_Y_FRACTION);
        return new RestoreResult(new FloatingPanelPosition(
                px != null ? px : DEFAULT_X_FRACTION,
                py != null ? py : DEFAULT_Y_FRACTION), issues);
    }

    private static Double parse(String raw, double fallback, String axis, List<String> issues) {
        if (raw.isEmpty()) {
            issues.add(axis + ": value is empty; fell back to default " + fallback);
            return null;
        }
        try {
            double parsed = Double.parseDouble(raw);
            if (!Double.isFinite(parsed)) {
                issues.add(axis + ": value is not finite; fell back to default " + fallback);
                return null;
            }
            return clean(parsed, fallback, axis, issues);
        } catch (NumberFormatException bad) {
            issues.add(axis + ": value is not a number; fell back to default " + fallback);
            return null;
        }
    }
    private static double clean(double value, double fallback, String axis, List<String> issues) {
        if (!Double.isFinite(value)) {
            issues.add(axis + ": value is not finite; fell back to default " + fallback);
            return fallback;
        }
        if (value < 0d) {
            issues.add(axis + ": " + value + " is below 0.0; clamped to 0.0");
            return 0d;
        }
        if (value > 1d) {
            issues.add(axis + ": " + value + " is above 1.0; clamped to 1.0");
            return 1d;
        }
        return value == 0d ? 0d : value;
    }
    private static int clipSize(int requested, int available) {
        return Math.max(0, Math.min(requested, available));
    }
    private static int toPixel(double fraction, int slack) {
        if (slack <= 0) return 0;
        return (int) Math.min(slack, Math.max(0L, Math.round(fraction * slack)));
    }
    private static long clampLong(long value, long min, long max) {
        return Math.min(max, Math.max(min, value));
    }
    @Override public boolean equals(Object o) {
        if (!(o instanceof FloatingPanelPosition)) return false;
        FloatingPanelPosition p = (FloatingPanelPosition) o;
        return Double.compare(x, p.x) == 0 && Double.compare(y, p.y) == 0;
    }
    @Override public int hashCode() { return Objects.hash(x, y); }
    @Override public String toString() { return "FloatingPanelPosition{x=" + x + ", y=" + y + "}"; }
}
