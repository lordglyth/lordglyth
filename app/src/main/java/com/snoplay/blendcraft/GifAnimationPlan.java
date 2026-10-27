package com.snoplay.blendcraft;

/** Immutable animation planning for GIF89a output.
 *  Every frame uses a copy; saved photo/layers are never changed.
 */
public final class GifAnimationPlan {
    private GifAnimationPlan(){}
    public static final int MAX_EDGE=640;
    public static final int DELAY_MS=140;
    public static final int LOOP_FRAMES=18;

    public static int frameCount(GlitchFx.Config config){
        if(config==null || config.layers.isEmpty())return 1;
        for(GlitchFx.Layer l:config.layers){
            if(l.intensity>0 && l.opacity>0 && (!l.paintedOnly || !l.marks.isEmpty()))
                return LOOP_FRAMES;
        }
        return 1;
    }
    public static GlitchFx.Config atFrame(GlitchFx.Config config,int frame,int total){
        GlitchFx.Config result=config.copy();
        if(frame==0||total<=1)return result;
        double angle=2*Math.PI*frame/total;
        for(GlitchFx.Layer l:result.layers){
            // Phase changes pseudo-runes, scanner, PNG corruption, block motion,
            // and other Glitch Lab variants without changing user's stored state.
            l.phase+=frame*Math.max(1,1+l.runeSpeed/25);
            if(l.type!=30 && l.type!=0 && l.type!=7){
                // Seeded effects respond to a new deterministic per-frame seed.
                l.seed+=frame*1009L;
            }
            if(l.type!=30){
                l.scale=Math.max(1,Math.min(100,l.scale+(int)Math.round(9*Math.sin(angle))));
                if(l.type==0){
                    l.threshold=Math.max(0,Math.min(100,l.threshold+(int)Math.round(16*Math.sin(angle))));
                }
            }
        }
        return result;
    }
}