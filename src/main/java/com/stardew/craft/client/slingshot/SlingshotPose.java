package com.stardew.craft.client.slingshot;

/** Item-only draw/release curve. The player continues to use Minecraft's native bow poses. */
public final class SlingshotPose {
    private SlingshotPose() {}
    public static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*t*(10+t*(-15+6*t));}
    public static float draw(float useSeconds,float releaseSeconds,float releasedUseSeconds) {
        if(releaseSeconds<0)return smooth(useSeconds/.3f);
        float power=smooth(releasedUseSeconds/.3f);
        if(releaseSeconds<=.065f)return power*(1-smooth(releaseSeconds/.065f));
        if(releaseSeconds>=.24f)return 0;
        float t=(releaseSeconds-.065f)/.175f;
        return -.12f*power*(float)Math.sin(t*Math.PI*2)*(1-t)*(1-t);
    }
}
