package com.snoplay.blendcraft;
import java.util.Arrays;

/** Protects animation looping and owner-image immutability before Android bundle compilation. */
public final class GifAnimationPlanTest {
    static void ok(boolean good,String label){if(!good)throw new AssertionError(label);}
    public static void main(String[] args){
        GlitchFx.Config empty=new GlitchFx.Config();
        ok(GifAnimationPlan.frameCount(empty)==1,"plain photo has valid single-frame GIF");
        GlitchFx.Layer runes=new GlitchFx.Layer();runes.type=30;runes.phase=4;runes.runeSpeed=80;
        runes.paintedOnly=true;
        GlitchFx.Config painted=new GlitchFx.Config();
        painted.layers.add(runes);
        ok(GifAnimationPlan.frameCount(painted)==1,"unpainted effect stays a still");
        runes.marks.add(new GlitchFx.PaintMark(.43f,.72f,false));
        ok(GifAnimationPlan.frameCount(painted)==GifAnimationPlan.LOOP_FRAMES,"painted runes animate");
        GlitchFx.Config atFirst=GifAnimationPlan.atFrame(painted,0,18);
        GlitchFx.Config atNext=GifAnimationPlan.atFrame(painted,4,18);
        ok(atFirst.layers.get(0).phase==4,"first frame preserves phase");
        ok(atNext.layers.get(0).phase!=4,"animated runes change phase");
        ok(painted.layers.get(0).phase==4,"GIF does not mutate source project");
        ok(painted.layers.get(0).marks.size()==1,"GIF preserves paint mask");
        GlitchFx.Config noise=new GlitchFx.Config();
        GlitchFx.Layer effect=new GlitchFx.Layer();effect.type=4;
        noise.layers.add(effect);
        ok(GifAnimationPlan.frameCount(noise)==18,"noise effect animates");
        ok(GifAnimationPlan.atFrame(noise,5,18).layers.get(0).seed!=effect.seed,"random noise advances seed");
        ok(GifAnimationPlan.atFrame(noise,5,18).layers.get(0).seed==
            GifAnimationPlan.atFrame(noise,5,18).layers.get(0).seed,"frames deterministic");
        ok(GifAnimationPlan.MAX_EDGE<=640,"GIF memory bound");
        System.out.println("GifAnimationPlan PASS: static and looping, rune phase, masks, deterministic seeds");
    }
}