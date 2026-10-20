package floatingpanel;

import org.junit.Test;
import static org.junit.Assert.*;
import floatingpanel.FloatingPanelPosition.*;
import java.util.Random;

public final class FloatingPanelPositionTest {
    private static final double EPS=1e-12;
    private static FloatingPanelPosition position(double x,double y){return FloatingPanelPosition.of(x,y);}

    @Test public void defaultsAndStorageRoundTrip(){
        assertEquals("x=0.5;y=0.25",FloatingPanelPosition.defaults().toStorageValue());
        class Storage implements PositionStorage {
            String value; int writes;
            public String read(){return value;}
            public void write(String s){value=s;writes++;}
        }
        Storage s=new Storage();
        assertEquals(FloatingPanelPosition.defaults(),FloatingPanelPosition.restore(s).position());
        assertEquals(0,s.writes);
        FloatingPanelPosition original=position(.3,.8);
        original.persist(s);
        assertEquals(1,s.writes);
        assertEquals(original,FloatingPanelPosition.restore(s).position());
    }

    @Test public void duplicateFieldsCannotOverwriteFirstOccurrence(){
        RestoreResult r=FloatingPanelPosition.fromStorageValue("x=0.2;x=NaN;y=0.8;Y=0.1");
        assertEquals(.2,r.position().xFraction(),0d);
        assertEquals(.8,r.position().yFraction(),0d);
        assertEquals(2,r.issues().size());
    }

    @Test public void firstInvalidAxisDoesNotConsumeOtherAxis(){
        RestoreResult r=FloatingPanelPosition.fromStorageValue("X=NaN;x=0.9;y=0.3");
        assertEquals(.5,r.position().xFraction(),0d);
        assertEquals(.3,r.position().yFraction(),0d);
        assertTrue(r.hadIssues());
    }

    @Test public void corruptAndOutOfRangeFractionsHaveBoundedFallback(){
        RestoreResult r=FloatingPanelPosition.fromStorageValue("x=-3;y=2");
        assertEquals(0,r.position().xFraction(),0d);
        assertEquals(1,r.position().yFraction(),0d);
        assertEquals(FloatingPanelPosition.defaults(),
                FloatingPanelPosition.fromStorageValue("x=NaN;y=Infinity").position());
        assertEquals(.5,position(Double.NaN,Double.POSITIVE_INFINITY).xFraction(),0d);
    }

    @Test public void unknownPayloadIsNotEchoedAndOversizeIsBounded(){
        String secret="secret-value-should-not-be-logged";
        RestoreResult r=FloatingPanelPosition.fromStorageValue("x="+secret+";y=0.8");
        assertFalse(r.issues().toString().contains(secret));
        assertEquals(.8,r.position().yFraction(),0d);
        assertEquals(FloatingPanelPosition.defaults(),FloatingPanelPosition.fromStorageValue(
                "x=0.3;y=0.2;"+"a".repeat(513)).position());
        assertEquals(FloatingPanelPosition.defaults(),FloatingPanelPosition.fromStorageValue(
                "x=0.3;y=0.2;"+"a=1;".repeat(16)).position());
    }

    @Test public void centeredAndFreeHorizontalPoliciesDiffer(){
        LayoutArea a=LayoutArea.of(10,20,1000,400);
        FloatingPanelPosition p=position(.75,.25);
        Placement free=p.place(a,300,100,HorizontalLayout.FREE_X);
        Placement centered=p.place(a,300,100,HorizontalLayout.CENTERED_X);
        assertEquals(535,free.left());
        assertEquals(360,centered.left());
        assertEquals(95,free.top());
        assertEquals(95,centered.top());
    }

    @Test public void windowResizingKeepsPlacementInSafeArea(){
        FloatingPanelPosition p=position(1,1);
        Placement placement=p.place(LayoutArea.of(5,7,200,150),600,200,HorizontalLayout.FREE_X);
        assertEquals(5,placement.left());
        assertEquals(7,placement.top());
        assertEquals(200,placement.width());
        assertEquals(150,placement.height());
        assertTrue(placement.sizeClamped());
    }

    @Test public void dragClampsAndCenteringCannotChangeX(){
        LayoutArea a=LayoutArea.of(20,30,200,100);
        FloatingPanelPosition p=position(.3,.4);
        FloatingPanelPosition edge=p.dragTo(a,50,20,HorizontalLayout.FREE_X,-1000,2000);
        assertEquals(0,edge.xFraction(),0d);
        assertEquals(1,edge.yFraction(),0d);
        FloatingPanelPosition centered=p.dragTo(a,50,20,HorizontalLayout.CENTERED_X,2000,30);
        assertEquals(.3,centered.xFraction(),0d);
        assertEquals(0,centered.yFraction(),0d);
    }

    @Test public void zeroSlackRetainsFractions(){
        FloatingPanelPosition p=position(.3,.4);
        FloatingPanelPosition moved=p.dragTo(LayoutArea.of(0,0,10,10),10,10,
                HorizontalLayout.FREE_X,Integer.MAX_VALUE,Integer.MIN_VALUE);
        assertEquals(p,moved);
    }

    @Test public void boundaryAdditionAndSubtractionCannotOverflow(){
        LayoutArea huge=LayoutArea.of(Integer.MAX_VALUE-2,Integer.MIN_VALUE,100,100);
        assertEquals(Integer.MAX_VALUE,huge.right());
        FloatingPanelPosition p=position(1,1);
        Placement v=p.place(huge,1,1,HorizontalLayout.FREE_X);
        assertEquals(Integer.MAX_VALUE,v.right());
        assertTrue(v.bottom()<=huge.bottom());
        FloatingPanelPosition dragged=p.dragTo(huge,1,1,HorizontalLayout.FREE_X,
                Integer.MIN_VALUE,Integer.MAX_VALUE);
        assertEquals(0,dragged.xFraction(),0d);
        assertEquals(1,dragged.yFraction(),0d);
    }

    @Test public void randomPlacementAlwaysWithinBounds(){
        Random r=new Random(19831109L);
        for(int i=0;i<5000;i++){
            int left=r.nextInt(2001)-1000,top=r.nextInt(2001)-1000;
            LayoutArea a=LayoutArea.of(left,top,r.nextInt(2000),r.nextInt(2000));
            Placement p=position(r.nextDouble(),r.nextDouble()).place(a,r.nextInt(2500),
                    r.nextInt(2500),r.nextBoolean()?HorizontalLayout.FREE_X:HorizontalLayout.CENTERED_X);
            assertTrue(p.left()>=a.left()); assertTrue(p.top()>=a.top());
            assertTrue(p.right()<=a.right()); assertTrue(p.bottom()<=a.bottom());
            assertTrue(p.width()>=0 && p.height()>=0);
        }
    }

    @Test public void noCumulativeOffsetAfterRepeatedPlacement(){
        LayoutArea a=LayoutArea.of(123,456,1000,800);
        FloatingPanelPosition p=position(.345,.678);
        Placement first=p.place(a,240,90,HorizontalLayout.FREE_X);
        for(int i=0;i<500;i++)
            assertEquals(first,p.place(a,240,90,HorizontalLayout.FREE_X));
    }
}
