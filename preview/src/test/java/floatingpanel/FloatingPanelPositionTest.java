package floatingpanel;

import floatingpanel.FloatingPanelPosition.HorizontalLayout;
import floatingpanel.FloatingPanelPosition.LayoutArea;
import floatingpanel.FloatingPanelPosition.Placement;
import floatingpanel.FloatingPanelPosition.PositionStorage;
import floatingpanel.FloatingPanelPosition.RestoreResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 任务 A 的单元测试：位置模型、几何换算、布局策略与持久化契约。
 */
public class FloatingPanelPositionTest {

    private static final double EPS = 1e-12;

    // ------------------------------------------------------------------
    // 1. 首次默认位置与正常保存/读取
    // ------------------------------------------------------------------

    @Test
    public void defaultsAreHalfWidthAndQuarterHeight() {
        FloatingPanelPosition position = FloatingPanelPosition.defaults();
        assertEquals(0.5d, position.xFraction(), EPS);
        assertEquals(0.25d, position.yFraction(), EPS);
        assertEquals(FloatingPanelPosition.defaults(), FloatingPanelPosition.of(0.5d, 0.25d));
    }

    @Test
    public void firstRunWithoutStoredValueUsesDefaultsAndWritesNothing() {
        RecordingStorage storage = new RecordingStorage(null);
        RestoreResult result = FloatingPanelPosition.restore(storage);
        assertEquals(0.5d, result.position().xFraction(), EPS);
        assertEquals(0.25d, result.position().yFraction(), EPS);
        assertTrue(result.hadIssues());
        assertEquals(0, storage.writeCount());
    }

    @Test
    public void saveThenReadRoundTripsExactly() {
        RecordingStorage storage = new RecordingStorage(null);
        FloatingPanelPosition saved = FloatingPanelPosition.of(0.123456789d, 0.987654321d);
        saved.persist(storage);

        assertEquals(1, storage.writeCount());
        assertEquals("x=0.123456789;y=0.987654321", storage.lastWritten());

        RestoreResult restored = FloatingPanelPosition.restore(storage);
        assertEquals(saved, restored.position());
        assertFalse(restored.hadIssues());
        assertEquals(0.123456789d, restored.position().xFraction(), 0d);
    }

    @Test
    public void storageValueIsStableAndLocaleIndependent() {
        assertEquals("x=0.5;y=0.25", FloatingPanelPosition.defaults().toStorageValue());
        assertEquals("x=0.0;y=1.0",
                FloatingPanelPosition.of(0d, 1d).toStorageValue());
    }

    @Test
    public void parsingToleratesWhitespaceAndKeyOrder() {
        RestoreResult result = FloatingPanelPosition.fromStorageValue("  y = 0.75 ; x = 0.25 ");
        assertEquals(0.25d, result.position().xFraction(), EPS);
        assertEquals(0.75d, result.position().yFraction(), EPS);
        assertFalse(result.hadIssues());
    }

    // ------------------------------------------------------------------
    // 2. 坏数据 / NaN / Infinity / 越界比例
    // ------------------------------------------------------------------

    @Test
    public void nanFallsBackPerAxisWithoutTouchingTheOtherAxis() {
        RestoreResult result = FloatingPanelPosition.fromStorageValue("x=NaN;y=0.3");
        assertEquals(FloatingPanelPosition.DEFAULT_X_FRACTION, result.position().xFraction(), EPS);
        assertEquals(0.3d, result.position().yFraction(), EPS);
        assertEquals(1, result.issues().size());
        assertTrue(result.issues().get(0).contains("x:"));
    }

    @Test
    public void infinityFallsBackPerAxis() {
        RestoreResult both = FloatingPanelPosition.fromStorageValue("x=Infinity;y=-Infinity");
        assertEquals(FloatingPanelPosition.DEFAULT_X_FRACTION, both.position().xFraction(), EPS);
        assertEquals(FloatingPanelPosition.DEFAULT_Y_FRACTION, both.position().yFraction(), EPS);
        assertEquals(2, both.issues().size());

        RestoreResult overflowLiteral = FloatingPanelPosition.fromStorageValue("x=1e400;y=-1e400");
        assertEquals(FloatingPanelPosition.DEFAULT_X_FRACTION, overflowLiteral.position().xFraction(), EPS);
        assertEquals(FloatingPanelPosition.DEFAULT_Y_FRACTION, overflowLiteral.position().yFraction(), EPS);
        assertTrue(overflowLiteral.hadIssues());
    }

    @Test
    public void outOfRangeFractionsAreClampedNotDiscarded() {
        RestoreResult result = FloatingPanelPosition.fromStorageValue("x=-0.4;y=1.4");
        assertEquals(0d, result.position().xFraction(), 0d);
        assertEquals(1d, result.position().yFraction(), 0d);
        assertEquals(2, result.issues().size());
    }

    @Test
    public void unparsableAndMissingDataFallBackWithExplanation() {
        RestoreResult garbage = FloatingPanelPosition.fromStorageValue("x=abc;y=0.4");
        assertEquals(FloatingPanelPosition.DEFAULT_X_FRACTION, garbage.position().xFraction(), EPS);
        assertEquals(0.4d, garbage.position().yFraction(), EPS);
        assertTrue(garbage.issues().get(0).contains("not a number"));

        RestoreResult missingY = FloatingPanelPosition.fromStorageValue("x=0.2");
        assertEquals(0.2d, missingY.position().xFraction(), EPS);
        assertEquals(FloatingPanelPosition.DEFAULT_Y_FRACTION, missingY.position().yFraction(), EPS);
        assertEquals(1, missingY.issues().size());
        assertTrue(missingY.issues().get(0).contains("y is missing"));

        RestoreResult empty = FloatingPanelPosition.fromStorageValue("   ");
        assertEquals(FloatingPanelPosition.defaults(), empty.position());
        assertTrue(empty.hadIssues());
    }

    @Test
    public void unknownKeysAreReportedButDoNotDestroyKnownValues() {
        RestoreResult result = FloatingPanelPosition.fromStorageValue("theme=dark;x=0.7;y=0.6");
        assertEquals(0.7d, result.position().xFraction(), EPS);
        assertEquals(0.6d, result.position().yFraction(), EPS);
        assertEquals(1, result.issues().size());
        assertTrue(result.issues().get(0).contains("unknown key"));
    }

    @Test
    public void nonFiniteFractionsPassedDirectlyAreRejected() {
        FloatingPanelPosition position = FloatingPanelPosition.of(Double.NaN, Double.POSITIVE_INFINITY);
        assertEquals(FloatingPanelPosition.defaults(), position);

        FloatingPanelPosition clamped = FloatingPanelPosition.of(-9d, 42d);
        assertEquals(0d, clamped.xFraction(), 0d);
        assertEquals(1d, clamped.yFraction(), 0d);
    }

    // ------------------------------------------------------------------
    // 3. CENTERED_X 与 FREE_X 的横向差异
    // ------------------------------------------------------------------

    @Test
    public void centeredXIgnoresSavedXWhileFreeXHonoursIt() {
        LayoutArea area = LayoutArea.of(0, 0, 1000, 1000);

        FloatingPanelPosition position = FloatingPanelPosition.of(0.9d, 0.5d);
        Placement free = position.place(area, 100, 100, HorizontalLayout.FREE_X);
        Placement centered = position.place(area, 100, 100, HorizontalLayout.CENTERED_X);

        assertEquals(810, free.left());
        assertEquals(450, centered.left());
        assertEquals(free.top(), centered.top());
    }

    @Test
    public void centeredXProducesSameLeftForEveryStoredX() {
        LayoutArea area = LayoutArea.of(100, 20, 900, 700);
        int expected = 100 + (900 - 300) / 2;
        for (double x = 0d; x <= 1d; x += 0.05d) {
            Placement placement = FloatingPanelPosition.of(x, 0.5d)
                    .place(area, 300, 200, HorizontalLayout.CENTERED_X);
            assertEquals(expected, placement.left());
        }
    }

    @Test
    public void freeXUsesFractionOverTheSlack() {
        LayoutArea area = LayoutArea.of(0, 0, 1000, 1000);
        assertEquals(0, FloatingPanelPosition.of(0d, 0d).place(area, 100, 100, HorizontalLayout.FREE_X).left());
        assertEquals(900, FloatingPanelPosition.of(1d, 0d).place(area, 100, 100, HorizontalLayout.FREE_X).left());
    }

    // ------------------------------------------------------------------
    // 4. 窗口尺寸变化、横竖比例变化、安全区域非零偏移
    // ------------------------------------------------------------------

    @Test
    public void sameFractionFollowsWindowResize() {
        FloatingPanelPosition position = FloatingPanelPosition.of(0.5d, 0.25d);

        Placement portrait = position.place(LayoutArea.of(0, 0, 1080, 1920), 200, 100, HorizontalLayout.FREE_X);
        assertEquals(440, portrait.left());
        assertEquals(455, portrait.top());

        Placement landscape = position.place(LayoutArea.of(0, 0, 1920, 1080), 200, 100, HorizontalLayout.FREE_X);
        assertEquals(860, landscape.left());
        assertEquals(245, landscape.top());
    }

    @Test
    public void nonzeroSafeAreaOffsetIsRespected() {
        LayoutArea area = LayoutArea.of(120, 60, 800, 600);
        Placement placement = FloatingPanelPosition.of(0.5d, 0.25d)
                .place(area, 200, 100, HorizontalLayout.FREE_X);

        assertEquals(120 + 300, placement.left());
        assertEquals(60 + 125, placement.top());
        assertEquals(420, placement.left());
        assertEquals(185, placement.top());
    }

    @Test
    public void zeroSizedPanelStaysOnTheAreaCorner() {
        LayoutArea area = LayoutArea.of(30, 40, 500, 500);
        Placement placement = FloatingPanelPosition.of(0.5d, 0.5d)
                .place(area, 0, 0, HorizontalLayout.FREE_X);
        assertEquals(30 + 250, placement.left());
        assertEquals(40 + 250, placement.top());
        assertEquals(0, placement.width());
        assertEquals(0, placement.height());
    }

    @Test
    public void centeredXWithOddSlackRoundsDownConsistently() {
        LayoutArea area = LayoutArea.of(0, 0, 101, 101);
        Placement placement = FloatingPanelPosition.defaults()
                .place(area, 100, 100, HorizontalLayout.CENTERED_X);
        assertEquals(0, (101 - 100) / 2);
        assertEquals(0, placement.left());
    }

    // ------------------------------------------------------------------
    // 5. 面板超过可用区域、极窄/零尺寸、边界拖动
    // ------------------------------------------------------------------

    @Test
    public void panelLargerThanAreaIsShrunkToTheArea() {
        LayoutArea area = LayoutArea.of(10, 20, 100, 80);
        Placement placement = FloatingPanelPosition.of(0.5d, 0.5d)
                .place(area, 400, 400, HorizontalLayout.FREE_X);

        assertEquals(10, placement.left());
        assertEquals(20, placement.top());
        assertEquals(100, placement.width());
        assertEquals(80, placement.height());
        assertTrue(placement.sizeClamped());
    }

    @Test
    public void zeroSizedAreaProducesZeroSizedPlacementAtOrigin() {
        LayoutArea area = LayoutArea.of(7, 9, 0, 0);
        Placement placement = FloatingPanelPosition.of(0.3d, 0.8d)
                .place(area, 100, 100, HorizontalLayout.FREE_X);
        assertEquals(7, placement.left());
        assertEquals(9, placement.top());
        assertEquals(0, placement.width());
        assertEquals(0, placement.height());
    }

    @Test
    public void extremelyNarrowAreaStillContainsThePanel() {
        LayoutArea area = LayoutArea.of(15, 15, 1, 1);
        Placement placement = FloatingPanelPosition.of(0.5d, 0.5d)
                .place(area, 100, 100, HorizontalLayout.FREE_X);
        assertEquals(15, placement.left());
        assertEquals(15, placement.top());
        assertEquals(1, placement.width());
        assertEquals(1, placement.height());
        assertEquals(area.right(), placement.right());
    }

    @Test
    public void dragBeyondLeftAndTopEdgesClampsToZeroFraction() {
        LayoutArea area = LayoutArea.of(0, 0, 1000, 500);
        FloatingPanelPosition dragged = FloatingPanelPosition.of(0.5d, 0.5d)
                .dragTo(area, 100, 100, HorizontalLayout.FREE_X, -5000, -5000);
        assertEquals(0d, dragged.xFraction(), 0d);
        assertEquals(0d, dragged.yFraction(), 0d);
    }

    @Test
    public void dragBeyondRightAndBottomEdgesClampsToOneFraction() {
        LayoutArea area = LayoutArea.of(0, 0, 1000, 500);
        FloatingPanelPosition dragged = FloatingPanelPosition.of(0.5d, 0.5d)
                .dragTo(area, 100, 100, HorizontalLayout.FREE_X, 5000, 5000);
        assertEquals(1d, dragged.xFraction(), 0d);
        assertEquals(1d, dragged.yFraction(), 0d);
    }

    @Test
    public void dragInsideAreaConvertsPixelsBackToFractions() {
        LayoutArea area = LayoutArea.of(100, 100, 1000, 500);
        FloatingPanelPosition dragged = FloatingPanelPosition.of(0.5d, 0.5d)
                .dragTo(area, 100, 100, HorizontalLayout.FREE_X, 550, 300);
        assertEquals(450d / 900d, dragged.xFraction(), EPS);
        assertEquals(200d / 400d, dragged.yFraction(), EPS);
    }

    // ------------------------------------------------------------------
    // 6. 不能移动的轴保持位置
    // ------------------------------------------------------------------

    @Test
    public void dragCannotChangeXUnderCenteredX() {
        FloatingPanelPosition start = FloatingPanelPosition.of(0.2d, 0.2d);
        FloatingPanelPosition dragged = start.dragTo(
                LayoutArea.of(0, 0, 1000, 1000), 100, 100, HorizontalLayout.CENTERED_X, 900, 500);
        assertEquals(0.2d, dragged.xFraction(), 0d);
        assertEquals(500d / 900d, dragged.yFraction(), EPS);
    }

    @Test
    public void axesWithoutSlackKeepTheirFractions() {
        LayoutArea area = LayoutArea.of(0, 0, 100, 100);
        FloatingPanelPosition start = FloatingPanelPosition.of(0.8d, 0.7d);
        FloatingPanelPosition dragged = start.dragTo(area, 100, 100, HorizontalLayout.FREE_X, 90, 10);
        assertEquals(start, dragged);
    }

    @Test
    public void verticalSlackCanBeExhaustedWhileHorizontalCanStillMove() {
        LayoutArea area = LayoutArea.of(0, 0, 500, 100);
        FloatingPanelPosition start = FloatingPanelPosition.of(0.5d, 0.9d);
        FloatingPanelPosition dragged = start.dragTo(area, 100, 100, HorizontalLayout.FREE_X, 100, 0);
        assertEquals(0.25d, dragged.xFraction(), EPS);
        assertEquals(0.9d, dragged.yFraction(), 0d);
    }

    // ------------------------------------------------------------------
    // 7. 大数、溢出、非法输入
    // ------------------------------------------------------------------

    @Test
    public void layoutAreaNearIntMaxShrinksInsteadOfOverflowing() {
        LayoutArea area = LayoutArea.of(Integer.MAX_VALUE - 10, Integer.MAX_VALUE - 10, 100, 100);
        assertEquals(10, area.width());
        assertEquals(10, area.height());
        assertEquals(Integer.MAX_VALUE, area.right());
        assertEquals(Integer.MAX_VALUE, area.bottom());

        Placement placement = FloatingPanelPosition.of(1d, 1d)
                .place(area, 5, 5, HorizontalLayout.FREE_X);
        assertTrue(placement.right() <= Integer.MAX_VALUE);
        assertTrue(placement.bottom() <= Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, placement.right());
    }

    @Test
    public void hugeNegativeOriginDoesNotOverflow() {
        LayoutArea area = LayoutArea.of(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        Placement placement = FloatingPanelPosition.of(1d, 1d)
                .place(area, Integer.MAX_VALUE, 100, HorizontalLayout.FREE_X);
        assertTrue(placement.left() >= area.left());
        assertTrue(placement.right() <= area.right());
        assertTrue(placement.width() >= 0);
        assertTrue(placement.height() >= 0);
    }

    @Test
    public void negativeSizesAreTreatedAsZero() {
        LayoutArea area = LayoutArea.of(0, 0, -100, -50);
        assertEquals(0, area.width());
        assertEquals(0, area.height());

        Placement placement = FloatingPanelPosition.of(0.5d, 0.5d)
                .place(area, -30, -40, HorizontalLayout.FREE_X);
        assertEquals(0, placement.width());
        assertEquals(0, placement.height());
        assertEquals(0, placement.left());
        assertEquals(0, placement.top());
        assertFalse(placement.sizeClamped());
    }

    @Test
    public void maxPanelSizeOverAreaIsClampedToArea() {
        LayoutArea area = LayoutArea.of(0, 0, 320, 480);
        Placement placement = FloatingPanelPosition.defaults()
                .place(area, Integer.MAX_VALUE, Integer.MAX_VALUE, HorizontalLayout.FREE_X);
        assertEquals(320, placement.width());
        assertEquals(480, placement.height());
        assertTrue(placement.sizeClamped());
    }

    @Test(expected = NullPointerException.class)
    public void nullAreaIsRejected() {
        FloatingPanelPosition.defaults().place(null, 10, 10, HorizontalLayout.FREE_X);
    }

    @Test(expected = NullPointerException.class)
    public void nullLayoutIsRejected() {
        FloatingPanelPosition.defaults().place(LayoutArea.of(0, 0, 10, 10), 10, 10, null);
    }

    @Test(expected = NullPointerException.class)
    public void nullStorageIsRejected() {
        FloatingPanelPosition.defaults().persist(null);
    }

    // ------------------------------------------------------------------
    // 8. 读取/计算不触发写入
    // ------------------------------------------------------------------

    @Test
    public void geometryAndRestoreNeverWriteToStorage() {
        RecordingStorage storage = new RecordingStorage("x=0.3;y=0.6");

        RestoreResult restored = FloatingPanelPosition.restore(storage);
        FloatingPanelPosition position = restored.position();
        assertNotNull(position);
        position.place(LayoutArea.of(0, 0, 1000, 1000), 100, 100, HorizontalLayout.FREE_X);
        position.place(LayoutArea.of(0, 0, 1000, 1000), 100, 100, HorizontalLayout.CENTERED_X);
        position.dragTo(LayoutArea.of(0, 0, 1000, 1000), 100, 100, HorizontalLayout.FREE_X, 400, 400);
        position.toStorageValue();
        FloatingPanelPosition.fromStorageValue("x=0.1;y=0.2");
        FloatingPanelPosition.of(0.4d, 0.4d);

        assertEquals(1, storage.readCount());
        assertEquals(0, storage.writeCount());
    }

    @Test
    public void explicitPersistIsTheOnlyWritePath() {
        RecordingStorage storage = new RecordingStorage(null);
        FloatingPanelPosition position = FloatingPanelPosition.of(0.1d, 0.2d);
        assertEquals(0, storage.writeCount());
        position.persist(storage);
        assertEquals(1, storage.writeCount());
        assertEquals("x=0.1;y=0.2", storage.lastWritten());
    }

    @Test
    public void dragResultIsANewInstanceAndLeavesOriginalUntouched() {
        FloatingPanelPosition original = FloatingPanelPosition.of(0.5d, 0.5d);
        FloatingPanelPosition dragged = original.dragTo(
                LayoutArea.of(0, 0, 1000, 1000), 100, 100, HorizontalLayout.FREE_X, 0, 0);
        assertFalse(original.equals(dragged));
        assertEquals(0.5d, original.xFraction(), 0d);
        assertEquals(0d, dragged.xFraction(), 0d);
    }

    /** 记录读写次数的测试替身，用于证明几何/读取路径不会暗中写入。 */
    private static final class RecordingStorage implements PositionStorage {
        private final List<String> writes = new ArrayList<>();
        private String value;
        private int reads;

        RecordingStorage(String initial) {
            this.value = initial;
        }

        @Override
        public String read() {
            reads++;
            return value;
        }

        @Override
        public void write(String newValue) {
            writes.add(newValue);
            value = newValue;
        }

        int readCount() {
            return reads;
        }

        int writeCount() {
            return writes.size();
        }

        String lastWritten() {
            return writes.isEmpty() ? null : writes.get(writes.size() - 1);
        }
    }
}
