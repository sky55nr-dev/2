package com.riskmemory.map;

import java.util.ArrayList;
import java.util.List;

/** Candidate detector: thresholds are for demonstration, not validated safety judgments. */
public final class DriveEngine {
    public static final double BRAKE_THRESHOLD = -3.0;
    public static final double TURN_THRESHOLD = 70.0;
    public static final double MIN_TURN_SPEED = 30.0;
    public static final double COOLDOWN = 8.0;
    public static final int DURATION = 64;
    private Sample previous;
    private double lastBrake = -100, lastTurn = -100;

    public static final class Sample {
        public final double second, speed, steering;
        public Sample(double second, double speed, double steering) {
            this.second=second; this.speed=speed; this.steering=steering;
        }
    }
    public static final class Event {
        public final String kind;
        public final double second, speedBefore, speedAfter, acceleration, steeringRate;
        public String context = "미확인";
        public Event(String kind, double second, double before, double after, double acceleration, double rate) {
            this.kind=kind; this.second=second; speedBefore=before; speedAfter=after;
            this.acceleration=acceleration; steeringRate=rate;
        }
        public double progress() { return Math.min(1, second / DURATION); }
    }
    public List<Event> accept(Sample sample) {
        List<Event> result=new ArrayList<>();
        if (!valid(sample)) return result;
        if (previous==null) { previous=sample; return result; }
        double dt=sample.second-previous.second;
        if(dt<=0) return result;
        if(dt>2.5) { previous=sample; return result; }
        double acceleration=(sample.speed-previous.speed)/3.6/dt;
        double steeringRate=Math.abs(sample.steering-previous.steering)/dt;
        if(acceleration<=BRAKE_THRESHOLD+1e-9 && previous.speed>=15 && sample.second-lastBrake>=COOLDOWN) {
            result.add(new Event("brake",sample.second,previous.speed,sample.speed,acceleration,steeringRate));
            lastBrake=sample.second;
        }
        if(steeringRate>=TURN_THRESHOLD && Math.min(previous.speed,sample.speed)>=MIN_TURN_SPEED
                && sample.second-lastTurn>=COOLDOWN) {
            result.add(new Event("turn",sample.second,previous.speed,sample.speed,acceleration,steeringRate));
            lastTurn=sample.second;
        }
        previous=sample;
        return result;
    }
    private boolean valid(Sample s) {
        return s!=null && Double.isFinite(s.second) && Double.isFinite(s.speed)
            && Double.isFinite(s.steering) && s.second>=0 && s.speed>=0 && s.speed<=250;
    }
    public static Sample demo(int second) {
        double speed;
        if(second<10) speed=second*5.4;
        else if(second<15) speed=54;
        else if(second<=18) speed=54-(second-14)*12;
        else if(second<25) speed=6+(second-18)*6;
        else if(second<38) speed=48;
        else if(second<42) speed=62;
        else if(second<=45) speed=Math.max(8,62-(second-41)*14);
        else if(second<54) speed=8+(second-45)*5;
        else if(second<60) speed=48;
        else speed=Math.max(0,48-(second-59)*9.6);
        double steering=second==30?110:second==31?80:second==32?45:0;
        return new Sample(second,speed,steering);
    }
}