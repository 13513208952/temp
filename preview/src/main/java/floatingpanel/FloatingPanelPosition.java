package floatingpanel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 与具体应用无关的浮动面板位置模型。
 *
 * <p>位置以归一化比例 {@code xFraction}/{@code yFraction}（闭区间 [0, 1]）保存，
 * 而不是保存上一次的绝对像素，因此在窗口尺寸、横竖比例或安全区域变化后仍然有效。
 *
 * <p>本类只做纯几何换算与（反）序列化，不涉及窗口显示、悬浮权限、手势授权、
 * 媒体播放或任何业务逻辑；也不依赖 Android、数据库、网络或真实设备。
 *
 * <p>副作用约定：构造、{@link #place}、{@link #dragTo}、{@link #restore} 与
 * {@link #toStorageValue} 都不会触碰存储。只有调用者显式调用 {@link #persist}
 * 才会写入 {@link PositionStorage}。
 */
public final class FloatingPanelPosition {

    /** 默认横向比例：水平居中。 */
    public static final double DEFAULT_X_FRACTION = 0.5d;

    /** 默认纵向比例：偏上四分之一。 */
    public static final double DEFAULT_Y_FRACTION = 0.25d;

    /** 允许的最小比例。 */
    public static final double MIN_FRACTION = 0.0d;

    /** 允许的最大比例。 */
    public static final double MAX_FRACTION = 1.0d;

    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final char SEPARATOR = ';';

    private final double xFraction;
    private final double yFraction;

    private FloatingPanelPosition(double xFraction, double yFraction) {
        this.xFraction = xFraction;
        this.yFraction = yFraction;
    }

    // ------------------------------------------------------------------
    // 工厂方法
    // ------------------------------------------------------------------

    /** 返回默认位置 {@code x=0.5, y=0.25}。 */
    public static FloatingPanelPosition defaults() {
        return new FloatingPanelPosition(DEFAULT_X_FRACTION, DEFAULT_Y_FRACTION);
    }

    /**
     * 用给定比例创建位置。
     *
     * <p>非有限值（NaN / Infinity）逐轴回退到该轴默认值，越界值（含 -0.0）被裁剪到
     * [0, 1]。两个轴互相独立：一个轴非法不会影响另一个轴。如需知道发生了什么，
     * 请使用 {@link #fromStorageValue} 或 {@link #ofExplained}。
     */
    public static FloatingPanelPosition of(double xFraction, double yFraction) {
        return ofExplained(xFraction, yFraction).position();
    }

    /**
     * 与 {@link #of} 相同，但返回带说明的回退/裁剪记录。
     */
    public static RestoreResult ofExplained(double xFraction, double yFraction) {
        List<String> issues = new ArrayList<>(2);
        double x = sanitizeFraction(xFraction, DEFAULT_X_FRACTION, KEY_X, issues);
        double y = sanitizeFraction(yFraction, DEFAULT_Y_FRACTION, KEY_Y, issues);
        return new RestoreResult(new FloatingPanelPosition(x, y), issues);
    }

    // ------------------------------------------------------------------
    // 几何换算：纯函数
    // ------------------------------------------------------------------

    /**
     * 根据调用者给出的安全可用矩形与面板尺寸，计算限制在矩形内的放置坐标与实际尺寸。
     *
     * <p>面板尺寸大于可用区域时会被压缩到可用区域大小（不会出现负尺寸或溢出）；
     * {@link HorizontalLayout#CENTERED_X} 忽略保存的横向比例，强制水平居中，
     * 纵向仍然按比例恢复。
     *
     * @param area            安全可用矩形，非 null
     * @param panelWidth      面板期望宽度，负数按 0 处理
     * @param panelHeight     面板期望高度，负数按 0 处理
     * @param layout          横向布局策略，非 null
     * @return 放置结果，永不溢出 int 范围，宽高非负
     */
    public Placement place(LayoutArea area,
                           int panelWidth,
                           int panelHeight,
                           HorizontalLayout layout) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(layout, "layout");

        int availableWidth = area.width();
        int availableHeight = area.height();
        int width = clampSize(panelWidth, availableWidth);
        int height = clampSize(panelHeight, availableHeight);
        int slackX = availableWidth - width;
        int slackY = availableHeight - height;

        int offsetX = layout == HorizontalLayout.CENTERED_X
                ? slackX / 2
                : fractionToOffset(xFraction, slackX);
        int offsetY = fractionToOffset(yFraction, slackY);

        int left = area.left() + offsetX;
        int top = area.top() + offsetY;
        boolean sizeClamped = width != Math.max(0, panelWidth) || height != Math.max(0, panelHeight);
        return new Placement(left, top, width, height, xFraction, yFraction, sizeClamped);
    }

    /**
     * 接受调用者给出的拖动目标坐标（期望的面板左上角像素位置），把它裁剪到可移动区域，
     * 再换算成新的归一化比例。
     *
     * <p>规则：
     * <ul>
     *   <li>横向策略为 {@link HorizontalLayout#CENTERED_X} 时拖动不改变横向比例；</li>
     *   <li>某个轴没有可移动余量（面板尺寸等于可用尺寸或可用尺寸为 0）时，该轴保持原比例；</li>
     *   <li>目标坐标超出可移动区域时被裁剪到边界，得到的比例为 0 或 1。</li>
     * </ul>
     *
     * <p>本方法返回新实例，不修改当前对象，也不触碰存储。
     */
    public FloatingPanelPosition dragTo(LayoutArea area,
                                        int panelWidth,
                                        int panelHeight,
                                        HorizontalLayout layout,
                                        int targetLeft,
                                        int targetTop) {
        Objects.requireNonNull(area, "area");
        Objects.requireNonNull(layout, "layout");

        int width = clampSize(panelWidth, area.width());
        int height = clampSize(panelHeight, area.height());
        int slackX = area.width() - width;
        int slackY = area.height() - height;

        double newX;
        if (layout == HorizontalLayout.CENTERED_X || slackX <= 0) {
            newX = xFraction;
        } else {
            int clamped = clampToRange(targetLeft, area.left(), area.left() + slackX);
            newX = offsetToFraction(clamped - area.left(), slackX);
        }

        double newY;
        if (slackY <= 0) {
            newY = yFraction;
        } else {
            int clamped = clampToRange(targetTop, area.top(), area.top() + slackY);
            newY = offsetToFraction(clamped - area.top(), slackY);
        }
        return new FloatingPanelPosition(newX, newY);
    }

    // ------------------------------------------------------------------
    // 持久化：编码/解码与显式读写
    // ------------------------------------------------------------------

    /** 序列化为稳定、与区域设置无关的字符串，例如 {@code x=0.5;y=0.25}。 */
    public String toStorageValue() {
        return KEY_X + "=" + Double.toString(xFraction)
                + SEPARATOR + KEY_Y + "=" + Double.toString(yFraction);
    }

    /**
     * 解析持久化字符串。null、空串、缺失键、未知键、非数字、NaN、Infinity 与越界值
     * 都会产生可读的说明，并且逐轴回退或裁剪；一个轴出错不会覆盖另一个轴的合法值。
     *
     * @param raw 存储中的原始字符串，可为 null
     * @return 解析结果，永不为 null
     */
    public static RestoreResult fromStorageValue(String raw) {
        List<String> issues = new ArrayList<>(4);
        Double parsedX = null;
        Double parsedY = null;
        boolean xReported = false;
        boolean yReported = false;

        if (raw == null) {
            issues.add("stored value is null; using defaults");
        } else {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                issues.add("stored value is blank; using defaults");
            } else {
                for (String segment : trimmed.split(String.valueOf(SEPARATOR))) {
                    String item = segment.trim();
                    if (item.isEmpty()) {
                        continue;
                    }
                    int eq = item.indexOf('=');
                    if (eq < 0) {
                        issues.add("ignored segment without '=': \"" + item + "\"");
                        continue;
                    }
                    String key = item.substring(0, eq).trim();
                    String value = item.substring(eq + 1).trim();
                    if (KEY_X.equalsIgnoreCase(key)) {
                        parsedX = parseAxis(value, KEY_X, DEFAULT_X_FRACTION, issues);
                        xReported = true;
                    } else if (KEY_Y.equalsIgnoreCase(key)) {
                        parsedY = parseAxis(value, KEY_Y, DEFAULT_Y_FRACTION, issues);
                        yReported = true;
                    } else {
                        issues.add("ignored unknown key: \"" + key + "\"");
                    }
                }
                if (!xReported) {
                    issues.add("x is missing; fell back to default " + DEFAULT_X_FRACTION);
                }
                if (!yReported) {
                    issues.add("y is missing; fell back to default " + DEFAULT_Y_FRACTION);
                }
            }
        }

        double x = parsedX != null ? parsedX : DEFAULT_X_FRACTION;
        double y = parsedY != null ? parsedY : DEFAULT_Y_FRACTION;
        return new RestoreResult(new FloatingPanelPosition(x, y), issues);
    }

    /**
     * 从调用者注入的存储读取位置。只读，绝不写入。
     *
     * @param storage 存储实现，非 null
     * @return 解析结果（含回退说明），永不为 null
     */
    public static RestoreResult restore(PositionStorage storage) {
        Objects.requireNonNull(storage, "storage");
        return fromStorageValue(storage.read());
    }

    /**
     * 把当前位置写入调用者注入的存储。只由调用者显式触发。
     *
     * @param storage 存储实现，非 null
     */
    public void persist(PositionStorage storage) {
        Objects.requireNonNull(storage, "storage");
        storage.write(toStorageValue());
    }

    // ------------------------------------------------------------------
    // 访问器
    // ------------------------------------------------------------------

    public double xFraction() {
        return xFraction;
    }

    public double yFraction() {
        return yFraction;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FloatingPanelPosition)) {
            return false;
        }
        FloatingPanelPosition that = (FloatingPanelPosition) other;
        return Double.compare(xFraction, that.xFraction) == 0
                && Double.compare(yFraction, that.yFraction) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(xFraction) + Double.hashCode(yFraction);
    }

    @Override
    public String toString() {
        return "FloatingPanelPosition{x=" + xFraction + ", y=" + yFraction + "}";
    }

    // ------------------------------------------------------------------
    // 内部计算
    // ------------------------------------------------------------------

    /** 比例换算为 [0, slack] 内的像素偏移，采用四舍五入（.5 向上）。 */
    private static int fractionToOffset(double fraction, int slack) {
        if (slack <= 0) {
            return 0;
        }
        long rounded = Math.round(fraction * (double) slack);
        if (rounded < 0L) {
            return 0;
        }
        if (rounded > slack) {
            return slack;
        }
        return (int) rounded;
    }

    /** 像素偏移换算为 [0, 1] 内的比例。 */
    private static double offsetToFraction(int offset, int slack) {
        if (slack <= 0) {
            return DEFAULT_X_FRACTION;
        }
        double fraction = (double) offset / (double) slack;
        if (!Double.isFinite(fraction)) {
            return DEFAULT_X_FRACTION;
        }
        if (fraction < MIN_FRACTION) {
            return MIN_FRACTION;
        }
        if (fraction > MAX_FRACTION) {
            return MAX_FRACTION;
        }
        return fraction == 0.0d ? 0.0d : fraction;
    }

    /** 面板尺寸裁剪到 [0, available]，负数按 0 处理，避免负尺寸与溢出。 */
    private static int clampSize(int requested, int available) {
        if (requested <= 0 || available <= 0) {
            return 0;
        }
        return Math.min(requested, available);
    }

    private static int clampToRange(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static Double parseAxis(String value, String axis, double fallback, List<String> issues) {
        if (value.isEmpty()) {
            issues.add(axis + ": value is empty; fell back to default " + fallback);
            return null;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            issues.add(axis + ": \"" + value + "\" is not a number; fell back to default " + fallback);
            return null;
        }
        if (!Double.isFinite(parsed)) {
            issues.add(axis + ": value is not finite (" + parsed + "); fell back to default " + fallback);
            return null;
        }
        if (parsed < MIN_FRACTION) {
            issues.add(axis + ": " + parsed + " is below " + MIN_FRACTION + "; clamped to " + MIN_FRACTION);
            return MIN_FRACTION;
        }
        if (parsed > MAX_FRACTION) {
            issues.add(axis + ": " + parsed + " is above " + MAX_FRACTION + "; clamped to " + MAX_FRACTION);
            return MAX_FRACTION;
        }
        return parsed == 0.0d ? 0.0d : parsed;
    }

    private static double sanitizeFraction(double value, double fallback, String axis, List<String> issues) {
        if (!Double.isFinite(value)) {
            issues.add(axis + ": value is not finite (" + value + "); fell back to default " + fallback);
            return fallback;
        }
        if (value < MIN_FRACTION) {
            issues.add(axis + ": " + value + " is below " + MIN_FRACTION + "; clamped to " + MIN_FRACTION);
            return MIN_FRACTION;
        }
        if (value > MAX_FRACTION) {
            issues.add(axis + ": " + value + " is above " + MAX_FRACTION + "; clamped to " + MAX_FRACTION);
            return MAX_FRACTION;
        }
        return value == 0.0d ? 0.0d : value;
    }

    // ------------------------------------------------------------------
    // 嵌套类型
    // ------------------------------------------------------------------

    /** 横向布局策略。这里只是通用策略，不推断真实设备类型。 */
    public enum HorizontalLayout {
        /** 强制水平居中，忽略保存的横向比例，纵向仍按比例恢复。 */
        CENTERED_X,
        /** 按保存的横向、纵向比例恢复。 */
        FREE_X
    }

    /** 放置结果：绝对像素坐标、实际尺寸与本次使用的比例。 */
    public static final class Placement {
        private final int left;
        private final int top;
        private final int width;
        private final int height;
        private final double xFraction;
        private final double yFraction;
        private final boolean sizeClamped;

        Placement(int left, int top, int width, int height,
                  double xFraction, double yFraction, boolean sizeClamped) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.xFraction = xFraction;
            this.yFraction = yFraction;
            this.sizeClamped = sizeClamped;
        }

        public int left() {
            return left;
        }

        public int top() {
            return top;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int right() {
            return left + width;
        }

        public int bottom() {
            return top + height;
        }

        public double xFraction() {
            return xFraction;
        }

        public double yFraction() {
            return yFraction;
        }

        /** 期望尺寸是否被可用区域压缩过。 */
        public boolean sizeClamped() {
            return sizeClamped;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Placement)) {
                return false;
            }
            Placement that = (Placement) other;
            return left == that.left
                    && top == that.top
                    && width == that.width
                    && height == that.height;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + top;
            result = 31 * result + width;
            result = 31 * result + height;
            return result;
        }

        @Override
        public String toString() {
            return "Placement{left=" + left + ", top=" + top
                    + ", width=" + width + ", height=" + height
                    + ", xFraction=" + xFraction + ", yFraction=" + yFraction + "}";
        }
    }

    /**
     * 调用者传入的安全可用矩形（像素）。左侧允许为负（多屏偏移），宽度/高度为负按 0 处理，
     * 右边界/下边界超出 int 范围时收缩尺寸，保证后续加法不溢出。
     */
    public static final class LayoutArea {
        private final int left;
        private final int top;
        private final int width;
        private final int height;

        private LayoutArea(int left, int top, int width, int height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }

        public static LayoutArea of(int left, int top, int width, int height) {
            int safeWidth = Math.max(0, width);
            int safeHeight = Math.max(0, height);
            long right = (long) left + safeWidth;
            if (right > Integer.MAX_VALUE) {
                safeWidth = Integer.MAX_VALUE - left;
            }
            long bottom = (long) top + safeHeight;
            if (bottom > Integer.MAX_VALUE) {
                safeHeight = Integer.MAX_VALUE - top;
            }
            return new LayoutArea(left, top, safeWidth, safeHeight);
        }

        public int left() {
            return left;
        }

        public int top() {
            return top;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** 右边界（像素），保证不溢出 int。 */
        public int right() {
            return left + width;
        }

        /** 下边界（像素），保证不溢出 int。 */
        public int bottom() {
            return top + height;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LayoutArea)) {
                return false;
            }
            LayoutArea that = (LayoutArea) other;
            return left == that.left && top == that.top
                    && width == that.width && height == that.height;
        }

        @Override
        public int hashCode() {
            int result = left;
            result = 31 * result + top;
            result = 31 * result + width;
            result = 31 * result + height;
            return result;
        }

        @Override
        public String toString() {
            return "LayoutArea{left=" + left + ", top=" + top
                    + ", width=" + width + ", height=" + height + "}";
        }
    }

    /**
     * 存储抽象。由调用者注入实现；本组件不提供任何具体实现，也不依赖 Android、
     * 数据库或网络。
     *
     * <p>契约：{@link #read()} 在没有任何已存数据时返回 {@code null}；
     * {@link #write(String)} 只在调用者显式调用 {@link #persist} 时被触发。
     */
    public interface PositionStorage {
        String read();

        void write(String value);
    }

    /** 解析/构造结果：位置本身加上人类可读的回退或裁剪说明。 */
    public static final class RestoreResult {
        private final FloatingPanelPosition position;
        private final List<String> issues;

        RestoreResult(FloatingPanelPosition position, List<String> issues) {
            this.position = position;
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
        }

        public FloatingPanelPosition position() {
            return position;
        }

        /** 回退/裁剪说明列表；无异常时为空。 */
        public List<String> issues() {
            return issues;
        }

        /** 是否发生过回退或裁剪。 */
        public boolean hadIssues() {
            return !issues.isEmpty();
        }

        @Override
        public String toString() {
            return "RestoreResult{position=" + position + ", issues=" + issues + "}";
        }
    }
}
