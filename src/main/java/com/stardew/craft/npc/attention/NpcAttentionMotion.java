package com.stardew.craft.npc.attention;

/** Deterministic grounded glance, in authoring degrees/units. No renderer or game dependencies. */
public final class NpcAttentionMotion {
    private NpcAttentionMotion() {}
    /** Measured sole and authored stance; values stay in model units. */
    public record Rig(double hipHeight, double soleY, double toeDepth, double halfStance,
                      double rightZ, double leftZ, double rightYaw, double leftYaw,
                      double lift, double lean, double armSwing) {
        public double restYaw(boolean right) { return right ? rightYaw : leftYaw; }
        public double restX(boolean right) { return right ? -halfStance : halfStance; }
        public double restZ(boolean right) { return right ? rightZ : leftZ; }
        public double toeX(boolean right) { return restX(right)-toeDepth*Math.sin(Math.toRadians(restYaw(right))); }
        public double toeZ(boolean right) { return restZ(right)-toeDepth*Math.cos(Math.toRadians(restYaw(right))); }
    }
    public static final Rig SAM = new Rig(12,0,3,2.12,.15,-.30,-5,4,.85,1.6,4);
    /** Contact point is the front sole edge, authored at Y=0, Z=-3. */
    public record Foot(double angle, double heel, double lift, double toeX, double toeZ) {}
    public record Sample(double bodyYaw, double rootX, double rootZ, double shoulderYaw,
                         double headYaw, double headPitch, double lean, double armSwing,
                         double blink, Foot right, Foot left) {}
    private record Turn(double yaw, double x, double z, double transfer, Foot right, Foot left) {}

    public static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t*t*(3-2*t);
    }
    private static double clamp(double x, double limit) { return Math.max(-limit, Math.min(limit, x)); }
    public static double bodyTarget(double yaw) {
        return Math.copySign(Math.max(0, Math.abs(yaw)-35), yaw);
    }
    public static int steps(double yaw) {
        double angle=Math.abs(bodyTarget(yaw));
        return angle<.001 ? 0 : angle<=110 ? 1 : 2;
    }
    public static double turnTime(double yaw) { return steps(yaw)==0 ? 0 : steps(yaw)==1 ? .78 : 1.30; }
    public static double holdEnd(double yaw) { return .30+turnTime(yaw)+1.30; }
    public static double duration(double yaw) { return holdEnd(yaw)+.30+turnTime(yaw)+.30; }

    public static double restYaw(boolean right) { return right ? -5 : 4; }
    public static double toeX(boolean right) {
        return (right ? -2.12 : 2.12)-3*Math.sin(Math.toRadians(restYaw(right)));
    }
    public static double toeZ(boolean right) {
        return (right ? .15 : -.30)-3*Math.cos(Math.toRadians(restYaw(right)));
    }
    private static double rx(double x,double z,double angle) {
        double r=Math.toRadians(angle);return Math.cos(r)*x+Math.sin(r)*z;
    }
    private static double rz(double x,double z,double angle) {
        double r=Math.toRadians(angle);return -Math.sin(r)*x+Math.cos(r)*z;
    }

    /** One turning step around a loaded forefoot. Only a near-rear turn needs a second step. */
    private static Turn turn(double time, double yaw, Rig rig) {
        int count=steps(yaw);
        double angle=bodyTarget(yaw), baseYaw=0, baseX=0, baseZ=0, cursor=0;
        Foot right=new Foot(0,0,0,rig.toeX(true),rig.toeZ(true));
        Foot left=new Foot(0,0,0,rig.toeX(false),rig.toeZ(false));
        for(int step=0;step<count;step++) {
            double length=step==0?.78:.52;
            double f=Math.max(0,Math.min(1,(time-cursor)/length));
            boolean swingRight=(angle>0) == (step%2==0);
            boolean supportRight=!swingRight;
            double px=baseX+rx(rig.toeX(supportRight),rig.toeZ(supportRight),baseYaw);
            double pz=baseZ+rz(rig.toeX(supportRight),rig.toeZ(supportRight),baseYaw);
            double turnAmount=angle/count;
            // Brief transfer, then one continuous turn; no equal-angle staircase of tiny steps.
            double body=baseYaw+turnAmount*smooth((f-.08)/.92);
            double swing=baseYaw+turnAmount*smooth((f-.04)/.91);
            double x=px-rx(rig.toeX(supportRight),rig.toeZ(supportRight),body);
            double z=pz-rz(rig.toeX(supportRight),rig.toeZ(supportRight),body);
            double lift=rig.lift()*Math.pow(Math.sin(Math.PI*f),2);
            double heel=-5*Math.pow(Math.sin(Math.PI*f),2);
            Foot support=new Foot(body,heel,0,px,pz);
            Foot moving=new Foot(swing,0,lift,
                    px+rx(rig.toeX(swingRight)-rig.toeX(supportRight),rig.toeZ(swingRight)-rig.toeZ(supportRight),swing),
                    pz+rz(rig.toeX(swingRight)-rig.toeX(supportRight),rig.toeZ(swingRight)-rig.toeZ(supportRight),swing));
            right=swingRight?moving:support;left=swingRight?support:moving;
            if(f<1) return new Turn(body,x,z,(supportRight?-1:1)*Math.sin(Math.PI*f),right,left);
            baseYaw=body;baseX=x;baseZ=z;cursor+=length;
        }
        return new Turn(baseYaw,baseX,baseZ,0,right,left);
    }

    public static Sample sample(double time, double yaw, double pitch) {
        return sample(time,yaw,pitch,64);
    }

    public static Sample sample(double time, double yaw, double pitch, double targetDistance) {
        return sample(time,yaw,pitch,targetDistance,SAM);
    }

    public static Sample sample(double time, double yaw, double pitch, double targetDistance, Rig rig) {
        yaw=clamp(yaw,180);pitch=clamp(pitch,18);
        double end=holdEnd(yaw), length=turnTime(yaw);
        double turnClock=time<end+.30 ? time-.30 : length-(time-end-.30);
        Turn turn=turn(Math.max(0,turnClock),yaw,rig);
        // A pivot also translates the body. Aim from the new location, especially at close range.
        double distance=Math.max(8,targetDistance), targetAngle=Math.toRadians(yaw);
        double dx=-Math.sin(targetAngle)*distance-turn.x, dz=-Math.cos(targetAngle)*distance-turn.z;
        double aim=Math.toDegrees(Math.atan2(-dx,-dz));
        while(aim-yaw>180)aim-=360;
        while(aim-yaw< -180)aim+=360;
        double aimPitch=Math.toDegrees(Math.atan2(Math.tan(Math.toRadians(pitch))*distance,Math.hypot(dx,dz)));
        double headWeight=smooth((time-.08)/.38)*(1-smooth((time-end-.10)/(.40+length)));
        double shoulder=clamp((aim*headWeight-turn.yaw)*.18,7);
        double head=clamp(aim*headWeight-turn.yaw-shoulder,48);
        double blink=-1;
        if(Math.abs(yaw)>35 && time>=.10 && time<=.33)blink=time-.10;
        if(time>=end+.04 && time<=end+.27)blink=time-end-.04;
        return new Sample(turn.yaw,turn.x,turn.z,shoulder,head,clamp(aimPitch,18)*headWeight,
                turn.transfer*rig.lean(),(turn.right.lift-turn.left.lift)*rig.armSwing(),blink,turn.right,turn.left);
    }
}
