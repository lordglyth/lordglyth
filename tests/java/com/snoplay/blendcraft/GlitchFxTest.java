package com.snoplay.blendcraft;
import android.graphics.Bitmap;
import java.util.Arrays;
public final class GlitchFxTest {
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
    private static Bitmap bitmap(int w,int h){
        Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        int[] px=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int v=(x*67+y*113+x*y*19)%256;
            int alpha=100+((x+y)%156);
            px[y*w+x]=(alpha<<24)|(v<<16)|(((v*3+25)%256)<<8)|((v*11+7)%256);
        }
        b.setPixels(px,0,w,0,0,w,h);return b;
    }
    private static int[] pixels(Bitmap b){
        int[] v=new int[b.getWidth()*b.getHeight()];
        b.getPixels(v,0,b.getWidth(),0,0,b.getWidth(),b.getHeight());return v;
    }
    private static Bitmap one(Bitmap src,int type,int direction,int seed){
        GlitchFx.Config c=new GlitchFx.Config();
        GlitchFx.Layer layer=new GlitchFx.Layer();
        layer.type=type;layer.intensity=100;layer.opacity=100;
        layer.threshold=0;layer.scale=90;layer.direction=direction;layer.seed=seed;
        c.layers.add(layer);return GlitchFx.render(src,c);
    }
    public static void main(String[] args){
        Bitmap source=bitmap(52,43);
        int[] pristine=pixels(source);
        for(int type=0;type<GlitchFx.EFFECTS.length;type++){
            Bitmap altered=one(source,type,0,9173);
            check(!Arrays.equals(pristine,pixels(altered)),"effect "+type+" changed pixels");
            check(Arrays.equals(pristine,pixels(source)),"effect "+type+" left source untouched");
            int[] v=pixels(altered);
            for(int i=0;i<v.length;i++)check((v[i]>>>24)==(pristine[i]>>>24),"alpha "+type);
        }
        check(!Arrays.equals(pixels(one(source,0,0,10)),pixels(one(source,0,1,10))),
            "horizontal versus vertical pixel sort differs");
        check(Arrays.equals(pixels(one(source,4,0,123)),pixels(one(source,4,0,123))),
            "random noise reproducible by seed");
        check(!Arrays.equals(pixels(one(source,4,0,123)),pixels(one(source,4,0,124))),
            "different seeds change noise");
        GlitchFx.Config mask=new GlitchFx.Config();
        GlitchFx.Layer layer=new GlitchFx.Layer();layer.type=7;layer.intensity=100;
        layer.scale=100;mask.layers.add(layer);mask.mask=3;mask.brushRadius=13;
        mask.brush.add(new GlitchFx.Point(.50f,.50f));
        int[] m=pixels(GlitchFx.render(source,mask));
        check(m[0]==pristine[0],"brush leaves outside pixels untouched");
        check(m[21*52+26]!=pristine[21*52+26],"brush changes inside pixels");
        GlitchFx.Config copy=mask.copy();copy.layers.get(0).intensity=1;
        check(mask.layers.get(0).intensity==100,"undo copies are deep");
        check(!Arrays.equals(m,pixels(GlitchFx.render(source,copy))),"param changes affect render");
        for(int p=0;p<GlitchFx.PRESETS.length;p++){
            GlitchFx.Config config=GlitchFx.preset(p);
            check(config.layers.size()>0,"preset "+p+" exists");
            check(GlitchFx.render(source,config).getWidth()==52,"preset size "+p);
        }
        check(GlitchFx.EFFECTS.length==31,"31 image effects registered");
        check(ExtraGlitches.NAMES.length==21,"21 additional corruption effects");
        GlitchFx.Config curse=new GlitchFx.Config();
        GlitchFx.Layer rune=new GlitchFx.Layer();
        rune.type=30;rune.intensity=100;rune.opacity=100;rune.scale=90;rune.runeColor=0xFF00FF00;
        curse.layers.add(rune);
        int[] runeFirst=pixels(GlitchFx.render(source,curse));
        rune.phase=1;
        int[] runeShifted=pixels(GlitchFx.render(source,curse));
        check(!Arrays.equals(runeFirst,runeShifted),"shifting rune phase modifies real pixels");
        rune.phase=0;rune.runeColor=0xFFFF00D0;
        int[] runeRecolored=pixels(GlitchFx.render(source,curse));
        check(!Arrays.equals(runeFirst,runeRecolored),"color wheel rune color changes effect");
        check(Arrays.equals(pristine,pixels(source)),"runes never mutate source");
        GlitchFx.Config savedCurse=curse.copy();
        savedCurse.layers.get(0).runeColor=0xFF0000FF;
        check(curse.layers.get(0).runeColor==0xFFFF00D0,"color stored in deep-copied layer history");
        check(GlitchFx.render(source,new GlitchFx.Config())==source,"no effects identity");
        System.out.println("GlitchFx algorithm tests PASS (31 effects including shifting colored runes, 8 presets, sorting, seed, masks, source, alpha, history)");
    }
}