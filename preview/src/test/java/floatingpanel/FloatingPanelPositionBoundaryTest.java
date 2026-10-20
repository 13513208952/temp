package floatingpanel;

import floatingpanel.FloatingPanelPosition.HorizontalLayout;
import floatingpanel.FloatingPanelPosition.LayoutArea;
import floatingpanel.FloatingPanelPosition.Placement;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 任务 B：针对本仓库任务 A 实现的几何边界回归测试。
 *
 * <p>关注点：比例转整数的舍入、边界加法、缩放后是否仍在安全区域、重复放置是否累积偏移、
 * 边界附近的非法尺寸与非有限数，以及大量安全矩形/面板尺寸组合。
 */
public class FloatingPanelPositionBoundaryTest {

    private static final double EPS = 1e-9;

    // ------------------------------------------------------------------
    // 比例转整数的舍入行为
    // ------------------------------------------------------------------

    @Test
    public void fractionToPixelRoundsHalfUp() {
        LayoutArea area = LayoutArea.of(0, 0, 4, 4);

        assertEquals(0, place(0d, area, 0, 0).left());
        assertEquals(1, place(0.125d, area, 0, 0).left());
        assertEquals(1, place(0.249d, area, 0, 0).left());
        assertEquals(2, place(0.375d, area, 0, 0).left());
        assertEquals(3, place(0.625d, area, 0, 0).left());
        assertEquals(4, place(0.875d, area, 0, 0).left());
        assertEquals(4, place(1d, area, 0, 0).left());
    }

    @Test
    public void roundingNeverLeavesTheSlackRange() {
        LayoutArea area = LayoutArea.of(0, 0, 100, 100);
        for (int panel = 0; panel <= 100; panel++) {
            for (double fraction = 0d; fraction <= 1d; fraction += 0.01d) {
                Placement placement = place(fraction, area, panel, panel);
                assertTrue(placement.left() >= 0);
                assertTrue(placement.right() <= area.right());
                assertTrue(placement.bottom() <= area.bottom());
            }
        }
    }

    @Test
    public void boundaryAdditionWithNonZeroOriginIsExact() {
        LayoutArea area = LayoutArea.of(1000, 2000, 200, 400);
        Placement placement = place(0.25d, 0.75d, area, 50, 50);
        // slackX = 150 -> 37.5 -> 38 ; slackY = 350 -> 262.5 -> 263
        assertEquals(1000 + 38, placement.left());
        assertEquals(2000 + 263, placement.top());
        assertEquals(1038 + 50, placement.right());
        assertEquals(2263 + 50, placement.bottom());
    }

    @Test
    public void exactOneAndZeroHitTheAreaEdges() {
        LayoutArea area = LayoutArea.of(37, 41, 640, 480);
        Placement atRight = place(1d, 1d, area, 120, 80);
        assertEquals(area.right(), atRight.right());
        assertEquals(area.bottom(), atRight.bottom());

        Placement atLeft = place(0d, 0d, area, 120, 80);
        assertEquals(area.left(), atLeft.left());
        assertEquals(area.top(), atLeft.top());
    }

    // ------------------------------------------------------------------
    // 缩放后是否始终在安全区域内
    // ------------------------------------------------------------------

    @Test
    public void anySizeCombinationStaysInsideTheSafeArea() {
        Random random = new Random(20240617L);
        for (int i = 0; i < 5000; i++) {
            int left = random.nextInt(2001) - 1000;
            int top = random.nextInt(2001) - 1000;
            int width = random.nextInt(2000);
            int height = random.nextInt(2000);
            int panelWidth = random.nextInt(3000) - 100;
            int panelHeight = random.nextInt(3000) - 100;
            double x = random.nextDouble() * 1.2d - 0.1d;
            double y = random.nextDouble() * 1.2d - 0.1d;
            HorizontalLayout layout = random.nextBoolean()
                    ? HorizontalLayout.FREE_X
                    : HorizontalLayout.CENTERED_X;

            LayoutArea area = LayoutArea.of(left, top, width, height);
            Placement placement = FloatingPanelPosition.of(x, y).place(area, panelWidth, panelHeight, layout);

            String message = "area=" + area + " panel=" + panelWidth + "x" + panelHeight
                    + " fraction=" + x + "," + y + " layout=" + layout + " -> " + placement;

            assertTrue(message, placement.width() >= 0);
            assertTrue(message, placement.height() >= 0);
            assertTrue(message, placement.width() <= area.width());
            assertTrue(message, placement.height() <= area.height());
            assertTrue(message, placement.left() >= area.left());
            assertTrue(message, placement.top() >= area.top());
            assertTrue(message, placement.right() <= area.right());
            assertTrue(message, placement.bottom() <= area.bottom());
        }
    }

    @Test
    public void centeredLayoutIgnoresFractionEntirely() {
        LayoutArea area = LayoutArea.of(-50, -20, 777, 555);
        int expectedLeft = area.left() + (area.width() - 200) / 2;
        for (double x = -0.5d; x <= 1.5d; x += 0.1d) {
            Placement placement = FloatingPanelPosition.of(x, 0.5d)
                    .place(area, 200, 100, HorizontalLayout.CENTERED_X);
            assertEquals(expectedLeft, placement.left());
        }
    }

    // ------------------------------------------------------------------
    // 重复放置是否产生累计偏移
    // ------------------------------------------------------------------

    @Test
    public void repeatedPlacementDoesNotAccumulateOffset() {
        LayoutArea area = LayoutArea.of(0, 0, 1000, 700);
        FloatingPanelPosition position = FloatingPanelPosition.of(0.37d, 0.61d);
        Placement first = position.place(area, 137, 93, HorizontalLayout.FREE_X);

        for (int i = 0; i < 500; i++) {
            Placement again = position.place(area, 137, 93, HorizontalLayout.FREE_X);
            assertEquals("iteration " + i, first, again);
        }
    }

    @Test
    public void placeDragCycleIsStableOverManyIterations() {
        LayoutArea area = LayoutArea.of(11, 13, 1000, 700);
        FloatingPanelPosition position = FloatingPanelPosition.of(0.37d, 0.61d);

        Placement placement = position.place(area, 137, 93, HorizontalLayout.FREE_X);
        int firstLeft = placement.left();
        int firstTop = placement.top();

        for (int i = 0; i < 500; i++) {
            position = position.dragTo(area, 137, 93, HorizontalLayout.FREE_X,
                    placement.left(), placement.top());
            placement = position.place(area, 137, 93, HorizontalLayout.FREE_X);
            assertEquals("left drifted at iteration " + i, firstLeft, placement.left());
            assertEquals("top drifted at iteration " + i, firstTop, placement.top());
        }
        // 一次往返后比例被量化到整像素网格：319/863 与 370/607，之后不再变化。
        assertEquals(319d / 863d, position.xFraction(), 1e-12);
        assertEquals(370d / 607d, position.yFraction(), 1e-12);
    }

    @Test
    public void repeatedDragFromTheSamePixelReturnsTheSameFraction() {
        LayoutArea area = LayoutArea.of(0, 0, 640, 480);
        FloatingPanelPosition position = FloatingPanelPosition.of(0.5d, 0.5d);
        FloatingPanelPosition once = position.dragTo(area, 100, 60, HorizontalLayout.FREE_X, 213, 177);
        FloatingPanelPosition twice = once.dragTo(area, 100, 60, HorizontalLayout.FREE_X, 213, 177);
        assertEquals(once.xFraction(), twice.xFraction(), 0d);
        assertEquals(once.yFraction(), twice.yFraction(), 0d);
    }

    // ------------------------------------------------------------------
    // 边界附近的无效尺寸与非有限数
    // ------------------------------------------------------------------

    @Test
    public void nonFiniteFractionsStillProduceAValidPlacement() {
        LayoutArea area = LayoutArea.of(0, 0, 300, 300);
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            Placement placement = FloatingPanelPosition.of(bad, bad)
                    .place(area, 100, 100, HorizontalLayout.FREE_X);
            assertEquals(FloatingPanelPosition.DEFAULT_X_FRACTION, placement.xFraction(), 0d);
            assertEquals(FloatingPanelPosition.DEFAULT_Y_FRACTION, placement.yFraction(), 0d);
            assertTrue(placement.left() >= 0);
            assertTrue(placement.right() <= area.right());
            assertTrue(placement.bottom() <= area.bottom());
        }
    }

    @Test
    public void sizesAtIntLimitDoNotOverflowPlacementArithmetic() {
        LayoutArea area = LayoutArea.of(Integer.MAX_VALUE - 1000, Integer.MAX_VALUE - 1000, 5000, 5000);
        Placement placement = FloatingPanelPosition.of(1d, 1d)
                .place(area, Integer.MAX_VALUE, Integer.MAX_VALUE, HorizontalLayout.FREE_X);
        assertEquals(1000, area.width());
        assertTrue(placement.right() <= Integer.MAX_VALUE);
        assertTrue(placement.bottom() <= Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, placement.right());
        assertEquals(Integer.MAX_VALUE, placement.bottom());
    }

    @Test
    public void dragToIntLimitsStaysWithinTheArea() {
        LayoutArea area = LayoutArea.of(-2000, -2000, 4000, 4000);
        FloatingPanelPosition dragged = FloatingPanelPosition.defaults()
                .dragTo(area, 200, 200, HorizontalLayout.FREE_X, Integer.MAX_VALUE, Integer.MIN_VALUE);
        assertEquals(1d, dragged.xFraction(), 0d);
        assertEquals(0d, dragged.yFraction(), 0d);

        Placement placement = dragged.place(area, 200, 200, HorizontalLayout.FREE_X);
        assertEquals(area.right() - 200, placement.left());
        assertEquals(area.top(), placement.top());
    }

    @Test
    public void zeroSizedAreaAndPanelAreConsistentUnderBothLayouts() {
        LayoutArea area = LayoutArea.of(5, -5, 0, 0);
        for (HorizontalLayout layout : HorizontalLayout.values()) {
            Placement placement = FloatingPanelPosition.of(0.9d, 0.1d).place(area, 50, 50, layout);
            assertEquals(5, placement.left());
            assertEquals(-5, placement.top());
            assertEquals(0, placement.width());
            assertEquals(0, placement.height());
            assertTrue(placement.sizeClamped());
        }
    }

    @Test
    public void panelExactlyFillingTheAreaReportsNoClamping() {
        LayoutArea area = LayoutArea.of(3, 4, 250, 150);
        Placement placement = FloatingPanelPosition.of(0.9d, 0.1d)
                .place(area, 250, 150, HorizontalLayout.FREE_X);
        assertEquals(3, placement.left());
        assertEquals(4, placement.top());
        assertFalse(placement.sizeClamped());
    }

    @Test
    public void dragWithZeroSlackOnBothAxesKeepsFractionsExactly() {
        LayoutArea area = LayoutArea.of(0, 0, 50, 50);
        FloatingPanelPosition start = FloatingPanelPosition.of(1d / 3d, 2d / 3d);
        assertEquals(start, start.dragTo(area, 50, 50, HorizontalLayout.FREE_X, 999, -999));
    }

    private static Placement place(double fraction, LayoutArea area, int panelWidth, int panelHeight) {
        return place(fraction, fraction, area, panelWidth, panelHeight);
    }

    private static Placement place(double x, double y, LayoutArea area, int panelWidth, int panelHeight) {
        return FloatingPanelPosition.of(x, y).place(area, panelWidth, panelHeight, HorizontalLayout.FREE_X);
    }
}
