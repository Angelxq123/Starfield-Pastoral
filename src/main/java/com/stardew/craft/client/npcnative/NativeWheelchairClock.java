package com.stardew.craft.client.npcnative;

/** Rear-axle odometry in model units. Differential rolling also covers stationary chair turns. */
public final class NativeWheelchairClock {
    public record Wheel(double angle, double phase, double weight) {}
    public record Sample(Wheel right, Wheel left, double casterRight, double casterLeft, double casterSpinRight, double casterSpinLeft) {
        public double weight() { return Math.max(right.weight(),left.weight()); }
    }
    private double lastTime=Double.NaN,lastX,lastZ,lastYaw;
    private final double[] distance=new double[2], phase=new double[2], blend=new double[2];
    private final double[] caster=new double[2],frontDistance=new double[2];
    private final double travelScale;
    private Sample result=new Sample(new Wheel(0,0,0),new Wheel(0,0,0),0,0,0,0);
    private static double wrap(double d) { return d-360*Math.floor((d+180)/360); }

    public NativeWheelchairClock() { this(1); }
    public NativeWheelchairClock(double travelScale) {
        if(!Double.isFinite(travelScale)||travelScale<=0)throw new IllegalArgumentException("Invalid travel scale");
        this.travelScale=travelScale;
    }

    /** x/z are the rendered rear axle centre in blocks; yaw is Minecraft facing in degrees. */
    public Sample sample(double time,double x,double z,double yaw,boolean grounded) {
        return sample(time,x,z,yaw,grounded,false);
    }
    public Sample sample(double time,double x,double z,double yaw,boolean grounded,boolean preparing) {
        double dt=time-lastTime,dx=x-lastX,dz=z-lastZ,dyaw=wrap(yaw-lastYaw);
        if(Double.isNaN(lastTime)||dt<0||dt>.5||Math.hypot(dx,dz)>2) {
            lastTime=time;lastX=x;lastZ=z;lastYaw=yaw;
            for(int i=0;i<2;i++){blend[i]=0;phase[i]=0;caster[i]=0;}
            return result=finish();
        }
        if(dt==0)return result;
        double mid=Math.toRadians(lastYaw+dyaw/2),turn=-Math.toRadians(dyaw);
        double forward=16*(-dx*Math.sin(mid)+dz*Math.cos(mid));
        double lateral=16*(-dx*Math.cos(mid)-dz*Math.sin(mid));
        for(int i=0;i<2;i++) {
            double side=i==0?-1:1,travel=grounded?forward+side*8.5*turn:0;
            distance[i]+=travel;
            boolean moving=Math.abs(travel)/dt>.4;
            if(blend[i]==0 && (moving || preparing))phase[i]=0;
            // Keep the push cadence while translating faster; in-place turns retain their timing.
            // Wheel/caster rolling above and below still uses the full physical distance.
            if(moving) phase[i]+=(forward/travelScale+side*8.5*turn)/16;
            blend[i]=moving || grounded && preparing ? Math.min(1,blend[i]+dt/.30)
                    : Math.max(0,blend[i]-dt/.55);
            // Front axle is nine units ahead of rear axle. Fork yaw follows its local velocity.
            double vx=lateral-9*turn,vz=-(forward+side*6.5*turn);
            if(grounded && Math.hypot(vx,vz)/dt>.4) {
                double target=Math.toDegrees(Math.atan2(-vx,-vz));
                // Symmetric fork: equivalent headings avoid gratuitous 180-degree flips in reverse.
                target=wrap(target);if(target>90)target-=180;if(target< -90)target+=180;
                frontDistance[i]+=vx*(-Math.sin(Math.toRadians(target)))+vz*(-Math.cos(Math.toRadians(target)));
                caster[i]+=wrap(target-caster[i])*(1-Math.exp(-dt/.10));
            }
        }
        lastTime=time;lastX=x;lastZ=z;lastYaw=yaw;
        return result=finish();
    }
    private Sample finish() { return new Sample(wheel(0),wheel(1),caster[0],caster[1],-Math.toDegrees(frontDistance[0]/2)%360,-Math.toDegrees(frontDistance[1]/2)%360); }
    private Wheel wheel(int i) {
        return new Wheel(-Math.toDegrees(distance[i]/7)%360,
                phase[i],blend[i]*blend[i]*(3-2*blend[i]));
    }
}
