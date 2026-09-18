package com.stardew.craft.client.npcnative;

/** Walking phase with movement detection kept separate from the authored animation cadence. */
public final class NativeWalkClock {
    public record Sample(double phase, double weight) {}
    private final double stride;
    private final double fixedPhaseRate;
    private double lastTime=Double.NaN, lastX, lastZ, phase=.3, blend, lastMoved=Double.NEGATIVE_INFINITY;

    public NativeWalkClock(double stride) {
        if(!Double.isFinite(stride) || stride<=0)throw new IllegalArgumentException("Invalid stride");
        this.stride=stride;
        this.fixedPhaseRate=0;
    }

    /** Fixed authored cadence for routine NPCs whose world travel speed may be scaled independently. */
    public NativeWalkClock(double stride,double fixedPhaseRate) {
        if(!Double.isFinite(stride) || stride<=0)throw new IllegalArgumentException("Invalid stride");
        if(!Double.isFinite(fixedPhaseRate) || fixedPhaseRate<=0)throw new IllegalArgumentException("Invalid phase rate");
        this.stride=stride;
        this.fixedPhaseRate=fixedPhaseRate;
    }

    public Sample sample(double time,double x,double z,boolean active) {
        double dt=time-lastTime, distance=Math.hypot(x-lastX,z-lastZ);
        if(Double.isNaN(lastTime) || dt<0 || dt>.5 || distance>2) {
            lastTime=time;lastX=x;lastZ=z;phase=.3;blend=0;lastMoved=Double.NEGATIVE_INFINITY;
            return new Sample(phase,0);
        }
        if(dt==0)return result(); // Multiple render passes must not advance the animation.
        boolean moved=active && (fixedPhaseRate>0 || distance/dt>.025);
        if(moved) {
            if(blend==0)phase=.3; // Start near passing, not with both legs already stretched apart.
            phase+=fixedPhaseRate>0 ? dt*fixedPhaseRate : distance/stride;
            lastMoved=time;
        }
        boolean walking=active && time-lastMoved<.08;
        blend=walking?Math.min(1,blend+dt/.18):Math.max(0,blend-dt/.22);
        lastTime=time;lastX=x;lastZ=z;
        return result();
    }

    private Sample result() { return new Sample(phase,blend*blend*(3-2*blend)); }
}
