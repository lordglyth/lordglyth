package com.snoplay.blendcraft;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Deterministic CPU image-processing pipeline. Never changes the source Bitmap.
 * A Config can be copied for undo/redo; its layers and brush masks are data only.
 * Every operation acts on ARGB pixels; alpha is preserved for PNG/WebP output.
 */
public final class GlitchFx {
    private GlitchFx() {}
    public static final String[] EFFECTS = {
        "Pixel Sort","RGB Split","Data Smear","VHS / CRT","Signal Noise",
        "Artifact Blocks","Wave Distortion","Posterize","Ghosting","Corrupt Streaks",
        "Glitch","PNG Corruption","WebP Fracture","Paintbrush","Field","Hacker",
        "Triangle","Delaunay","Drone","Pixel","XOR","Scanner","Window",
        "Burn","Quake","Warp","3D","Zalgo","Chromatic","Datamosh",
        "Shifting Runes Curse"
    };
    public static final String[] PRESETS = {
        "Signal Loss","Data Melt","Broken Neon","VHS Haunting",
        "Cyber Static","Pixel Collapse","Corrupt Dream","Darkness Corruption"
    };
    public static final String[] MASKS = {"Whole image","Center ellipse","Background (outside center)","Brush-painted area"};
    public static final String[] BLENDS = {"Normal","Screen","Additive","Multiply"};
    public static final int MAX_LAYERS = 6;
    public static class Point {
        public float x,y;
        Point(float xx,float yy){x=xx;y=yy;}
        Point copy(){return new Point(x,y);}
    }
    public static class Layer {
        public int type=0, intensity=65, scale=40, threshold=40, direction=0, sortKey=0, opacity=100, blend=0;
        public int runeColor=0xFF6DFFCB, phase=0, runeSpeed=55;
        public long seed=2026;
        public Layer copy(){
            Layer l=new Layer();
            l.type=type;l.intensity=intensity;l.scale=scale;l.threshold=threshold;
            l.direction=direction;l.sortKey=sortKey;l.opacity=opacity;l.blend=blend;l.seed=seed;
            l.runeColor=runeColor;l.phase=phase;l.runeSpeed=runeSpeed;
            return l;
        }
    }
    public static class Config {
        public ArrayList<Layer> layers=new ArrayList<>();
        public int active=0, mask=0, brushRadius=13;
        public boolean seedLocked=true;
        public ArrayList<Point> brush=new ArrayList<>();
        public Config() { }
        public Config copy() {
            Config c=new Config();c.active=active;c.mask=mask;c.brushRadius=brushRadius;
            c.seedLocked=seedLocked;
            for(Layer l:layers)c.layers.add(l.copy());
            for(Point p:brush)c.brush.add(p.copy());
            return c;
        }
        public boolean enabled(){return layers.size()>0;}
    }
    public static Config preset(int index) {
        Config c=new Config();
        int[][] combos={
            {3,4,6}, {2,9}, {1,7,6}, {3,8,4},
            {4,1,5}, {0,5,7}, {8,6,1}, {7,9,3}
        };
        int[] types=combos[Math.floorMod(index,combos.length)];
        for(int i=0;i<types.length;i++){
            Layer l=new Layer();l.type=types[i];
            l.intensity=i==0?69:35;l.opacity=i==0?100:72;
            l.seed=9173L+index*193+i*43;
            l.scale=26+i*12;c.layers.add(l);
        }
        return c;
    }
    public static Bitmap render(Bitmap source,Config config) {
        if(source==null)throw new IllegalArgumentException("Missing image");
        if(config==null || config.layers.isEmpty())return source;
        final int w=source.getWidth(),h=source.getHeight();
        if(w<=0 || h<=0 || (long)w*h>20_000_000L)
            throw new IllegalArgumentException("Maximum Glitch Lab image size is 20 megapixels");
        int[] image=new int[w*h];
        source.getPixels(image,0,w,0,0,w,h);
        float[] mask=makeMask(w,h,config);
        for(Layer layer:config.layers) {
            if(layer.intensity<=0 || layer.opacity<=0)continue;
            int[] changed=apply(image,w,h,layer);
            float opacity=clamp01(layer.opacity/100f)*clamp01(layer.intensity/100f);
            for(int i=0;i<image.length;i++){
                float a=opacity*(mask==null?1:mask[i]);
                if(a<=0)continue;
                image[i]=combine(image[i],changed[i],a,layer.blend);
            }
        }
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        out.setPixels(image,0,w,0,0,w,h);
        return out;
    }
    private static float[] makeMask(int w,int h,Config c) {
        if(c.mask==0)return null;
        float[] out=new float[w*h];
        if(c.mask==3) {
            if(c.brush.isEmpty())return out;
            // Circle splats leave a soft-edged continuous brush mask.
            float r=Math.max(3,Math.min(w,h)*c.brushRadius/100f);
            for(Point point:c.brush){
                int x0=Math.max(0,(int)(point.x*w-r)),x1=Math.min(w-1,(int)(point.x*w+r));
                int y0=Math.max(0,(int)(point.y*h-r)),y1=Math.min(h-1,(int)(point.y*h+r));
                float px=point.x*w,py=point.y*h;
                for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                    float d=(float)Math.sqrt((x-px)*(x-px)+(y-py)*(y-py))/r;
                    if(d<1f) out[y*w+x]=Math.max(out[y*w+x],Math.min(1f,(1f-d)*5));
                }
            }
        } else {
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                float dx=((x+.5f)/w-.5f)/.35f,dy=((y+.5f)/h-.5f)/.37f;
                float d=(float)Math.sqrt(dx*dx+dy*dy);
                float subject=clamp01((1.07f-d)*8);
                out[y*w+x]=c.mask==1?subject:1-subject;
            }
        }
        return out;
    }
    private static int combine(int base,int glitch,float amount,int blend) {
        int ar=(base>>>24)&255, a=(glitch>>>24)&255;
        float alpha=clamp01(amount)*(a/255f);
        int br=(base>>>16)&255,bg=(base>>>8)&255,bb=base&255;
        int cr=(glitch>>>16)&255,cg=(glitch>>>8)&255,cb=glitch&255;
        if(blend==1){cr=255-(255-br)*(255-cr)/255;cg=255-(255-bg)*(255-cg)/255;cb=255-(255-bb)*(255-cb)/255;}
        if(blend==2){cr=Math.min(255,br+cr);cg=Math.min(255,bg+cg);cb=Math.min(255,bb+cb);}
        if(blend==3){cr=br*cr/255;cg=bg*cg/255;cb=bb*cb/255;}
        int rr=lerp(br,cr,alpha),rg=lerp(bg,cg,alpha),rb=lerp(bb,cb,alpha);
        return (ar<<24)|(rr<<16)|(rg<<8)|rb;
    }
    private static int[] apply(int[] src,int w,int h,Layer l) {
        switch(l.type){
            case 0:return sort(src,w,h,l);
            case 1:return rgb(src,w,h,l);
            case 2:return smear(src,w,h,l);
            case 3:return vhs(src,w,h,l);
            case 4:return noise(src,w,h,l);
            case 5:return blocks(src,w,h,l);
            case 6:return wave(src,w,h,l);
            case 7:return posterize(src,w,h,l);
            case 8:return ghost(src,w,h,l);
            case 9:return streaks(src,w,h,l);
            default:return ExtraGlitches.apply(src,w,h,l);
        }
    }
    private static int clamp(int v,int min,int max){return Math.min(max,Math.max(min,v));}
    private static float clamp01(float f){return Math.max(0,Math.min(1,f));}
    private static int lerp(int a,int b,float t){return clamp(Math.round(a+(b-a)*t),0,255);}
    private static int pixel(int[] pixels,int w,int h,int x,int y){
        return pixels[clamp(y,0,h-1)*w+clamp(x,0,w-1)];
    }
    private static int light(int p){return (((p>>>16)&255)*54+((p>>>8)&255)*183+(p&255)*19)>>8;}
    private static int sat(int p){
        int r=(p>>>16)&255,g=(p>>>8)&255,b=p&255;
        return Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b));
    }
    private static long score(int p,int direction){
        int rank;
        if(direction==1)rank=sat(p);
        else if(direction==2)rank=((p>>>16)&255);
        else if(direction==3)rank=((p>>>8)&255);
        else if(direction==4)rank=(p&255);
        else rank=light(p);
        return (((long)rank)<<32)|(p&0xffffffffL);
    }
    private static int[] sort(int[] src,int w,int h,Layer l){
        int[] out=src.clone();
        boolean vertical=(l.direction&1)!=0;
        int major=vertical?w:h,minor=vertical?h:w;
        int cutoff=clamp(l.threshold*255/100,0,255);
        // Brightness threshold defines contiguous sortable windows.
        for(int line=0;line<major;line++){
            int n=0;
            while(n<minor){
                int px=vertical?out[n*w+line]:out[line*w+n];
                if(light(px)<cutoff){n++;continue;}
                int start=n;
                while(n<minor && light(vertical?out[n*w+line]:out[line*w+n])>=cutoff)n++;
                int count=n-start;if(count<2)continue;
                long[] values=new long[count];
                for(int i=0;i<count;i++){
                    int p=vertical?out[(start+i)*w+line]:out[line*w+start+i];
                    values[i]=score(p,Math.floorMod(l.sortKey,5));
                }
                Arrays.sort(values);
                for(int i=0;i<count;i++){
                    int p=(int)values[i];
                    if(vertical)out[(start+i)*w+line]=p;
                    else out[line*w+start+i]=p;
                }
            }
        }
        return out;
    }
    private static int[] rgb(int[] src,int w,int h,Layer l){
        int[] out=src.clone();
        int shift=Math.max(1,Math.round(Math.min(w,h)*l.scale/1500f));
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int left=pixel(src,w,h,x-shift,y),right=pixel(src,w,h,x+shift,y);
            int up=pixel(src,w,h,x,y+shift/2);
            int a=src[y*w+x]&0xff000000;
            out[y*w+x]=a|(left&0x00ff0000)|(up&0x0000ff00)|(right&0x000000ff);
        }
        return out;
    }
    private static int[] smear(int[] src,int w,int h,Layer l) {
        int[] out=src.clone();
        Random rnd=new Random(l.seed);
        int stripes=clamp(h/11,8,350),range=Math.max(2,w*l.scale/350);
        for(int k=0;k<stripes;k++){
            int y=rnd.nextInt(h);
            int dy=1+rnd.nextInt(Math.max(1,h/105));
            int shift=(rnd.nextBoolean()?1:-1)*rnd.nextInt(range+1);
            for(int row=y;row<Math.min(h,y+dy);row++){
                for(int x=0;x<w;x++){
                    int offset=(int)(shift*(x/(float)Math.max(1,w)));
                    out[row*w+x]=pixel(src,w,h,x-offset,row);
                }
            }
        }
        return out;
    }
    private static int[] vhs(int[] src,int w,int h,Layer l){
        int[] out=src.clone();
        Random rnd=new Random(l.seed);
        int split=Math.max(1,l.scale*w/2200);
        for(int y=0;y<h;y++){
            int shift=(y%7==0?split:0)+(rnd.nextInt(23)==0?rnd.nextInt(split*5+1):0);
            int flicker=(y%3==0?-29:4)+rnd.nextInt(9)-4;
            for(int x=0;x<w;x++){
                int p=pixel(src,w,h,x+shift,y);
                int r=clamp(((p>>>16)&255)+flicker,0,255),
                    g=clamp(((p>>>8)&255)+flicker,0,255),
                    b=clamp((p&255)+flicker+7,0,255);
                out[y*w+x]=(p&0xff000000)|(r<<16)|(g<<8)|b;
            }
        }
        return out;
    }
    private static int[] noise(int[] src,int w,int h,Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int amplitude=15+l.scale*2;
        for(int i=0;i<src.length;i++){
            int p=src[i];int change=rnd.nextInt(2*amplitude+1)-amplitude;
            int r=clamp(((p>>>16)&255)+change+(rnd.nextInt(11)-5),0,255);
            int g=clamp(((p>>>8)&255)+change+(rnd.nextInt(11)-5),0,255);
            int b=clamp((p&255)+change+(rnd.nextInt(11)-5),0,255);
            out[i]=(p&0xff000000)|(r<<16)|(g<<8)|b;
        }
        return out;
    }
    private static int[] blocks(int[] src,int w,int h,Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int size=clamp(Math.min(w,h)*(l.scale+5)/800,5,Math.max(5,Math.min(w,h)/3));
        for(int n=0;n<clamp((w*h)/(Math.max(1,size*size))*l.intensity/550,4,250);n++){
            int x=rnd.nextInt(w),y=rnd.nextInt(h),sx=rnd.nextInt(w),sy=rnd.nextInt(h);
            for(int dy=0;dy<size && y+dy<h;dy++)
                for(int dx=0;dx<size && x+dx<w;dx++)
                    out[(y+dy)*w+x+dx]=pixel(src,w,h,sx+dx,sy+dy);
        }
        return out;
    }
    private static int[] wave(int[] src,int w,int h,Layer l){
        int[] out=src.clone();
        float mag=w*l.scale/2100f;
        float freq=.004f+l.threshold/2100f;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int dx=l.direction%2==0?(int)(mag*Math.sin(y*freq*12)):(int)(mag*Math.sin(x*freq*16));
            int dy=l.direction%2==0?0:(int)(mag*Math.cos(y*freq*8));
            out[y*w+x]=pixel(src,w,h,x+dx,y+dy);
        }
        return out;
    }
    private static int[] posterize(int[] src,int w,int h,Layer l){
        int[] out=src.clone();int levels=2+clamp((100-l.scale)/10,0,14);
        for(int i=0;i<src.length;i++){
            int p=src[i];
            int r=(((p>>>16)&255)*(levels-1)+127)/255*255/(levels-1);
            int g=(((p>>>8)&255)*(levels-1)+127)/255*255/(levels-1);
            int b=((p&255)*(levels-1)+127)/255*255/(levels-1);
            out[i]=(p&0xff000000)|(r<<16)|(g<<8)|b;
        }
        return out;
    }
    private static int[] ghost(int[] src,int w,int h,Layer l){
        int[] out=src.clone();int shift=Math.max(1,Math.min(w,h)*l.scale/1200);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=src[y*w+x],q=pixel(src,w,h,x-shift,y+shift/2);
            int a=p&0xff000000;
            out[y*w+x]=a|(lerp((p>>>16)&255,(q>>>16)&255,.68f)<<16)
                       |(lerp((p>>>8)&255,(q>>>8)&255,.68f)<<8)
                       |lerp(p&255,q&255,.68f);
        }
        return out;
    }
    private static int[] streaks(int[] src,int w,int h,Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int count=clamp((w*h)/38000,12,320);
        int maxRun=Math.max(4,w*(l.scale+5)/130);
        for(int i=0;i<count;i++){
            int x=rnd.nextInt(w),y=rnd.nextInt(h),p=src[y*w+x];
            int run=rnd.nextInt(maxRun+1);
            if(l.direction%2==1){
                for(int j=0;j<run && y+j<h;j++)out[(y+j)*w+x]=p;
            } else {
                for(int j=0;j<run && x+j<w;j++)out[y*w+x+j]=p;
            }
        }
        return out;
    }
}