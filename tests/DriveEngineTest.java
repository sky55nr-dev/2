import com.riskmemory.map.DriveEngine;
import java.util.*;
public class DriveEngineTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static DriveEngine.Sample sample(double t,double speed,double angle){return new DriveEngine.Sample(t,speed,angle);}
    public static void main(String[] args){
        DriveEngine e=new DriveEngine();List<DriveEngine.Event> all=new ArrayList<>();
        for(int i=0;i<=64;i++)all.addAll(e.accept(DriveEngine.demo(i)));
        check(all.size()==3,"Demo must produce exactly 3 candidates");
        check(all.get(0).kind.equals("brake")&&all.get(1).kind.equals("turn")&&all.get(2).kind.equals("brake"),"Event ordering");
        e=new DriveEngine();e.accept(sample(0,60,0));check(e.accept(sample(1,49.2,0)).size()==1,"Exact braking threshold");
        check(e.accept(sample(2,30,0)).isEmpty(),"Cooldown prevents repeated braking event");
        e=new DriveEngine();e.accept(sample(0,10,0));check(e.accept(sample(1,10,150)).isEmpty(),"Parking maneuver is excluded");
        e=new DriveEngine();e.accept(sample(0,50,0));check(e.accept(sample(0,0,120)).isEmpty(),"Duplicate timestamp ignored");
        check(e.accept(sample(-1,0,0)).isEmpty(),"Negative time ignored");
        check(e.accept(sample(1,Double.NaN,0)).isEmpty(),"Invalid speed ignored");
        check(e.accept(sample(1,50,70)).size()==1,"Valid sample after invalid input and exact turn threshold");
        e=new DriveEngine();e.accept(sample(0,60,0));check(e.accept(sample(10,0,100)).isEmpty(),"Missing data gap does not create false event");
        check(e.accept(null).isEmpty(),"Null input handled");
        e=new DriveEngine();e.accept(sample(0,60,0));check(e.accept(sample(1,60,71)).size()==1,"Turning detected at speed");
        check(e.accept(sample(.5,0,0)).isEmpty(),"Out-of-order timestamp ignored");
        check(e.accept(sample(2,60,72)).isEmpty(),"Out-of-order sample must not corrupt baseline");
        System.out.println("PASS: demo ordering, braking boundary, cooldown, parking filter, invalid data, time ordering and gaps");
    }
}