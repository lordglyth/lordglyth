package com.snoplay.blendcraft;

import java.util.Random;

/** Extended glitch image algorithms. All transforms are deterministic with the same seed and phase.
 *  Index 10–30 is stable for saved Glitch Lab presets.
 */
public final class ExtraGlitches {
    private ExtraGlitches(){}
    public static final String[] NAMES={
        "Glitch","PNG Corruption","WebP Fracture","Paintbrush",
        "Field","Hacker","Triangle","Delaunay","Drone","Pixel",
        "XOR","Scanner","Window","Burn","Quake","Warp","3D",
        "Zalgo","Chromatic","Datamosh","Shifting Runes Curse"
    };
    private static int bound(int x,int low,int high){return Math.max(low,Math.min(high,x));}
    private static int sample(int[] a,int w,int h,int x,int y){return a[bound(y,0,h-1)*w+bound(x,0,w-1)];}
    private static int red(int p){return p>>>16&255;}
    private static int green(int p){return p>>>8&255;}
    private static int blue(int p){return p&255;}
    private static int shade(int a,int r,int g,int b){return (a&0xff000000)|(bound(r,0,255)<<16)|(bound(g,0,255)<<8)|bound(b,0,255);}
    private static int mix(int a,int b,float t){
        t=Math.min(1,Math.max(0,t));float u=1-t;
        return (a&0xff000000)
            | (bound(Math.round(red(a)*u+red(b)*t),0,255)<<16)
            | (bound(Math.round(green(a)*u+green(b)*t),0,255)<<8)
            | bound(Math.round(blue(a)*u+blue(b)*t),0,255);
    }
    private static int rgba(int a,int r,int g,int b){return (a&0xff000000)|((r&255)<<16)|((g&255)<<8)|(b&255);}
    public static int[] apply(int[] src,int w,int h,GlitchFx.Layer l){
        switch(l.type) {
            case 10: return glitch(src,w,h,l);
            case 11: return png(src,w,h,l);
            case 12: return webp(src,w,h,l);
            case 13: return brush(src,w,h,l);
            case 14: return field(src,w,h,l);
            case 15: return hacker(src,w,h,l);
            case 16: return triangle(src,w,h,l);
            case 17: return lowpoly(src,w,h,l);
            case 18: return drone(src,w,h,l);
            case 19: return pixel(src,w,h,l);
            case 20: return xor(src,w,h,l);
            case 21: return scanner(src,w,h,l);
            case 22: return window(src,w,h,l);
            case 23: return burn(src,w,h,l);
            case 24: return quake(src,w,h,l);
            case 25: return warp(src,w,h,l);
            case 26: return stereoscopic(src,w,h,l);
            case 27: return zalgo(src,w,h,l);
            case 28: return chromatic(src,w,h,l);
            case 29: return datamosh(src,w,h,l);
            case 30: return shiftingRunes(src,w,h,l);
            default: return src.clone();
        }
    }
    private static int[] glitch(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random r=new Random(l.seed+l.phase*7);
        int bands=Math.max(6,l.scale*h/1400);
        for(int k=0;k<bands;k++){
            int y=r.nextInt(h),th=1+r.nextInt(Math.max(1,h/27));
            int shift=(r.nextBoolean()?1:-1)*(1+r.nextInt(Math.max(1,w*l.scale/150)));
            int[] colors={0xFF4EFEE5,0xFFF927A5,0xFFF4DD39};
            for(int j=y;j<Math.min(h,y+th);j++)for(int x=0;x<w;x++){
                int i=j*w+x;
                int p=sample(src,w,h,x-shift,j);
                if(r.nextInt(20)==0)p=mix(p,colors[r.nextInt(3)],.8f);
                out[i]=p;
            }
        }return out;
    }
    private static int[] png(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random r=new Random(l.seed+l.phase*19);
        int maxShift=Math.max(1,w*l.scale/220);
        for(int stripe=0;stripe<Math.max(5,h/16);stripe++){
            int y=r.nextInt(h),height=1+r.nextInt(Math.max(1,h/24));
            int xDelta=r.nextInt(maxShift*2+1)-maxShift;
            for(int j=y;j<Math.min(h,y+height);j++)for(int x=0;x<w;x++){
                int original=src[j*w+x];
                int shifted=sample(src,w,h,x+xDelta,j);
                if((x+j+stripe)%13<5){
                    int p=original^((r.nextInt(8)+1)<<((stripe%3)*8));
                    out[j*w+x]=rgba(original,p>>>16,p>>>8,p);
                }else out[j*w+x]=shifted;
            }
        }return out;
    }
    private static int[] webp(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int block=Math.max(4,Math.min(w,h)*Math.max(4,l.scale)/510);
        for(int y=0;y<h;y+=block)for(int x=0;x<w;x+=block){
            int p=src[Math.min(h-1,y+block/2)*w+Math.min(w-1,x+block/2)];
            int r=(red(p)/32)*32,g=(green(p)/32)*32,b=(blue(p)/32)*32;
            boolean distort=rnd.nextInt(9)<4;
            for(int dy=0;dy<block && y+dy<h;dy++)for(int dx=0;dx<block && x+dx<w;dx++){
                int orig=src[(y+dy)*w+x+dx];
                out[(y+dy)*w+x+dx]=distort?shade(orig,r,g,b):mix(orig,shade(orig,r,g,b),.34f);
            }
        }return out;
    }
    private static int[] brush(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int strokes=Math.max(10,(w*h)/Math.max(100,4000-(l.scale*20)));
        int max=Math.max(2,Math.min(w,h)*l.scale/220);
        for(int k=0;k<strokes;k++){
            int x=rnd.nextInt(w),y=rnd.nextInt(h);
            int color=src[y*w+x],length=1+rnd.nextInt(max),width=1+rnd.nextInt(Math.max(1,max/9));
            boolean vert=l.direction%2==1;
            for(int j=0;j<length;j++)for(int v=-width;v<=width;v++){
                int px=x+(vert?v:j),py=y+(vert?j:v);
                if(px>=0&&px<w&&py>=0&&py<h)
                    out[py*w+px]=mix(src[py*w+px],color,.83f);
            }
        }return out;
    }
    private static int[] field(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();float fx=w*.5f,fy=h*.5f;
        float r=Math.min(w,h)*(.18f+l.scale/175f);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float dx=x-fx,dy=y-fy,d=(float)Math.sqrt(dx*dx+dy*dy);
            float fall=Math.max(0,1-d/Math.max(1,r));
            float distorted=1f+.4f*fall*fall;
            out[y*w+x]=sample(src,w,h,Math.round(fx+dx*distorted),Math.round(fy+dy*distorted));
        }return out;
    }
    private static int[] hacker(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int step=Math.max(5,Math.min(w,h)*Math.max(4,l.scale)/900);
        Random rnd=new Random(l.seed+l.phase*29);
        for(int i=0;i<w*h;i++){
            int p=src[i],gray=(red(p)*30+green(p)*59+blue(p)*11)/100;
            out[i]=shade(p,gray/5,Math.min(255,gray+30),gray/4);
        }
        for(int x=0;x<w;x+=step){
            int start=rnd.nextInt(h),len=Math.max(3,l.scale*h/220);
            for(int y=0;y<len;y++){
                int yy=(start+y)%h;
                for(int xx=x;xx<Math.min(w,x+Math.max(1,step/3));xx++){
                    int i=yy*w+xx;out[i]=mix(out[i],0xFF23FF82,y%4==0?.9f:.5f);
                }
            }
        }return out;
    }
    private static int[] triangle(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int band=Math.max(5,Math.min(w,h)*Math.max(10,l.scale)/450);
        for(int y0=0;y0<h;y0+=band)for(int x0=0;x0<w;x0+=band){
            int ox=rnd.nextInt(band*2+1)-band;
            for(int dy=0;dy<band && y0+dy<h;dy++)for(int dx=0;dx<band && x0+dx<w;dx++){
                boolean upper=dx+dy<band;
                int delta=upper?ox:-ox;
                out[(y0+dy)*w+x0+dx]=sample(src,w,h,x0+dx+delta,y0+dy);
            }
        }return out;
    }
    private static int[] lowpoly(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int size=Math.max(6,Math.min(w,h)*Math.max(10,l.scale)/570);
        for(int y0=0;y0<h;y0+=size)for(int x0=0;x0<w;x0+=size){
            int p1=sample(src,w,h,x0+size/3,y0+size/3);
            int p2=sample(src,w,h,x0+size*2/3,y0+size*2/3);
            for(int dy=0;dy<size&&y0+dy<h;dy++)for(int dx=0;dx<size&&x0+dx<w;dx++){
                int i=(y0+dy)*w+x0+dx;
                out[i]=mix(src[i],(dx+dy<size)?p1:p2,.85f);
            }
        }return out;
    }
    private static int[] drone(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int delta=Math.max(1,Math.min(w,h)*l.scale/1200);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=src[y*w+x],q=sample(src,w,h,x+delta,y+delta);
            int edge=Math.min(255,(Math.abs(red(p)-red(q))+Math.abs(green(p)-green(q))+Math.abs(blue(p)-blue(q)))/2);
            int lum=(red(p)*3+green(p)*6+blue(p))/10;
            out[y*w+x]=shade(p,(lum+edge)/3,(lum+edge)*2/3,Math.min(255,(lum+edge)*7/8));
        }return out;
    }
    private static int[] pixel(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int size=Math.max(3,Math.min(w,h)*Math.max(5,l.scale)/650);
        for(int y=0;y<h;y+=size)for(int x=0;x<w;x+=size){
            int center=sample(src,w,h,x+size/2,y+size/2);
            for(int yy=y;yy<Math.min(h,y+size);yy++)
                for(int xx=x;xx<Math.min(w,x+size);xx++)
                    out[yy*w+xx]=mix(src[yy*w+xx],center,.95f);
        }return out;
    }
    private static int[] xor(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int period=Math.max(4,Math.min(w,h)*l.scale/800);
        int[] keys={0x004088C0,0x000F3C78,0x00A13618,0x00227FAF};
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=src[y*w+x];
            if(((x/period+y/period+l.phase)&3)==0){
                int n=p^keys[(x/period+y/period)&3];
                out[y*w+x]=(p&0xff000000)|(n&0x00ffffff);
            }
        }return out;
    }
    private static int[] scanner(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int period=Math.max(3,Math.min(w,h)*Math.max(5,l.scale)/530);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=src[y*w+x];
            int delta=(y+l.phase*3)%period;
            int bright=delta==0?90:delta==1?35:-8;
            out[y*w+x]=shade(p,red(p)+bright,green(p)+bright,blue(p)+bright);
        }return out;
    }
    private static int[] window(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed+l.phase);
        int columns=Math.max(3,Math.min(22,4+l.scale/9));
        int cell=Math.max(4,w/columns);
        for(int x0=0;x0<w;x0+=cell){
            int shift=(rnd.nextBoolean()?1:-1)*rnd.nextInt(Math.max(1,h/7));
            for(int y=0;y<h;y++)for(int x=x0;x<Math.min(w,x0+cell);x++)
                out[y*w+x]=sample(src,w,h,x,y+shift);
        }return out;
    }
    private static int[] burn(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed);
        int glow=Math.max(20,l.scale*2);
        for(int i=0;i<src.length;i++){
            int p=src[i],lum=(red(p)*3+green(p)*6+blue(p))/10;
            float heat=(Math.max(0,lum-65)/190f);
            int flame=(int)(heat*glow)+(rnd.nextInt(21)-10);
            out[i]=shade(p,red(p)+flame+22,green(p)+flame/2,blue(p)-flame/2-12);
        }return out;
    }
    private static int[] quake(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed+l.phase*211);
        int rowSpan=Math.max(2,Math.min(w,h)/Math.max(6,33-l.scale/5));
        for(int y=0;y<h;y+=rowSpan){
            int shift=(rnd.nextBoolean()?1:-1)*rnd.nextInt(Math.max(2,w*l.scale/290));
            for(int yy=y;yy<Math.min(h,y+rowSpan);yy++)for(int x=0;x<w;x++)
                out[yy*w+x]=sample(src,w,h,x+shift,yy);
        }return out;
    }
    private static int[] warp(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();float amp=Math.max(2,Math.min(w,h)*l.scale/280f);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float normX=x/(float)Math.max(1,w);
            float normY=y/(float)Math.max(1,h);
            int dx=(int)(amp*Math.sin(normY*12.5+normX*7.8+l.phase*.7));
            int dy=(int)(amp*.35*Math.cos(normX*19+normY*9));
            out[y*w+x]=sample(src,w,h,x+dx,y+dy);
        }return out;
    }
    private static int[] stereoscopic(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int shift=Math.max(1,Math.min(w,h)*l.scale/710);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=src[y*w+x],left=sample(src,w,h,x-shift,y),right=sample(src,w,h,x+shift,y);
            out[y*w+x]=shade(p,red(left),green(p),blue(right));
        }return out;
    }
    private static int[] zalgo(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed+l.phase*7);
        int count=Math.min(600,Math.max(16,(w*h)/2500));
        for(int k=0;k<count;k++){
            int x=rnd.nextInt(w),y=rnd.nextInt(h);
            int p=src[y*w+x],run=2+rnd.nextInt(Math.max(3,Math.min(w,h)*l.scale/400));
            int color=0xFF18FD61;
            for(int j=0;j<run;j++){
                int xx=x+rnd.nextInt(9)-4,yy=y+j;
                if(xx>=0&&xx<w&&yy<h)out[yy*w+xx]=mix(p,color,.85f);
            }
        }return out;
    }
    private static int[] chromatic(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();int shift=Math.max(2,Math.min(w,h)*l.scale/420);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int p=src[y*w+x],a=sample(src,w,h,x-shift,y),b=sample(src,w,h,x+shift,y);
            int rr=red(a),gg=green(p),bb=blue(b);
            out[y*w+x]=shade(p,rr,gg,bb);
        }return out;
    }
    private static int[] datamosh(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();Random rnd=new Random(l.seed+l.phase*31);
        int blocks=Math.max(10,w*h/2200);
        int size=Math.max(4,Math.min(w,h)*l.scale/430);
        for(int k=0;k<blocks;k++){
            int x=rnd.nextInt(w),y=rnd.nextInt(h);
            int shift=(rnd.nextBoolean()?1:-1)*rnd.nextInt(Math.max(2,w/6));
            for(int dy=0;dy<size&&y+dy<h;dy++)for(int dx=0;dx<size&&x+dx<w;dx++){
                int off=(y+dy)*w+x+dx;
                out[off]=mix(src[off],sample(src,w,h,x+dx+shift,y+dy),.95f);
            }
        }return out;
    }
    /* Geometric pseudo-runic alphabet: actual glyph-shaped luminous strokes,
       not font-dependent "cursed" stickers. Phase rotates the layout and symbols. */
    private static final int[][] SEGMENTS={
        {0,0,0,6, 0,0,4,0, 0,3,4,3},       // Fehu-like
        {0,0,0,5, 4,0,4,5, 0,5,2,6, 2,6,4,5}, // Uruz-like
        {0,0,0,6, 0,1,4,3, 4,3,0,5},       // Thurisaz-like
        {0,0,4,2, 4,2,0,6, 1,3,4,5},       // Ansuz-like
        {0,0,0,6, 0,0,4,2, 0,3,4,5},       // Raidho-like
        {0,0,0,6, 4,0,0,3, 0,3,4,6},       // Kaunan-like
        {0,0,4,6, 4,0,0,6},               // Gebo-like
        {0,0,4,0, 2,0,2,6, 0,6,4,6},       // Hagall-like
        {0,0,4,0, 2,0,2,6, 0,3,4,3},       // Isa-like
        {2,0,2,6, 0,2,4,4}                // Naudiz-like
    };
    private static void stroke(int[] img,int w,int h,float x1,float y1,float x2,float y2,int rgb,int radius){
        int steps=(int)Math.max(3,Math.max(Math.abs(x2-x1),Math.abs(y2-y1))*2);
        int cr=red(rgb),cg=green(rgb),cb=blue(rgb);
        for(int i=0;i<=steps;i++){
            float t=i/(float)steps;
            int cx=Math.round(x1+(x2-x1)*t),cy=Math.round(y1+(y2-y1)*t);
            for(int dy=-radius;dy<=radius;dy++)for(int dx=-radius;dx<=radius;dx++){
                int x=cx+dx,y=cy+dy;
                if(x<0||x>=w||y<0||y>=h)continue;
                float dist=(float)Math.sqrt(dx*dx+dy*dy)/(radius+1f);
                if(dist>1f)continue;
                float alpha=dist<.38f?.95f:Math.max(.12f,(1-dist)*.48f);
                int idx=y*w+x,p=img[idx];
                img[idx]=shade(p,(int)(red(p)*(1-alpha)+cr*alpha),
                    (int)(green(p)*(1-alpha)+cg*alpha),(int)(blue(p)*(1-alpha)+cb*alpha));
            }
        }
    }
    private static int[] shiftingRunes(int[] src,int w,int h,GlitchFx.Layer l){
        int[] out=src.clone();
        Random rnd=new Random(l.seed);
        int size=Math.max(12,Math.min(w,h)*(l.scale+35)/730);
        int stride=Math.max(14,size*2);
        int radius=Math.max(1,size/19);
        for(int row=0,y=size/2;y<h;y+=stride,row++){
            int shift=((row*37+l.phase*11)%(stride*2))-stride;
            for(int x=size/2+shift;x<w;x+=stride){
                if(x<0)continue;
                int rune=Math.floorMod((int)(l.seed%10)+row*17+x/stride+l.phase,SEGMENTS.length);
                int[] seg=SEGMENTS[rune];
                int ofsY=((row+l.phase)%3-1)*Math.max(1,size/5);
                for(int i=0;i+3<seg.length;i+=4){
                    float x1=x+(seg[i]-2)*size/6f,x2=x+(seg[i+2]-2)*size/6f;
                    float y1=y+ofsY+(seg[i+1]-3)*size/7f,y2=y+ofsY+(seg[i+3]-3)*size/7f;
                    stroke(out,w,h,x1,y1,x2,y2,l.runeColor,radius);
                }
            }
        }
        return out;
    }
}