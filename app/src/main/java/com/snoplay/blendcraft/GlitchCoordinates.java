package com.snoplay.blendcraft;

/**
 * The effect engine stores masks in rotated SOURCE coordinates.
 * Touches arrive on the visible output canvas (which can be cropped or framed).
 * This mapper is the single source of truth for both directions.
 * Deliberately pure Java for deterministic JVM regression tests.
 */
public final class GlitchCoordinates {
    public static final class Point {
        public final float x,y;
        public Point(float x,float y){this.x=x;this.y=y;}
    }
    // Visible image in OUTPUT-normalized coordinates.
    public final float left,top,right,bottom;
    // Source rectangle corresponding to that visible image in SOURCE-normalized coordinates.
    public final float sourceLeft,sourceTop,sourceRight,sourceBottom;

    private GlitchCoordinates(float l,float t,float r,float b,
                              float sl,float st,float sr,float sb){
        left=l;top=t;right=r;bottom=b;
        sourceLeft=sl;sourceTop=st;sourceRight=sr;sourceBottom=sb;
    }

    /** Exact normalized center crop matching MainActivity.crop(Bitmap,float). */
    public static GlitchCoordinates photo(int sourceWidth,int sourceHeight,float ratio){
        if(sourceWidth<1||sourceHeight<1||ratio<=0)throw new IllegalArgumentException("Invalid image ratio");
        float sw=sourceWidth,sh=sourceHeight;
        if(sw/sh>ratio){
            int nw=Math.max(1,Math.round(sourceHeight*ratio));
            int start=(sourceWidth-nw)/2;
            return new GlitchCoordinates(0,0,1,1,start/sw,0,(start+nw)/sw,1);
        }
        int nh=Math.max(1,Math.round(sourceWidth/ratio));
        int start=(sourceHeight-nh)/2;
        return new GlitchCoordinates(0,0,1,1,0,start/sh,1,(start+nh)/sh);
    }

    /** Screenshot Studio centers the photo in a framed/square output canvas. */
    public static GlitchCoordinates screenshot(int sw,int sh,int outputWidth,int outputHeight){
        if(sw<1||sh<1||outputWidth<1||outputHeight<1)
            throw new IllegalArgumentException("Invalid screenshot dimensions");
        float pad=outputWidth*.09f;
        float maxW=outputWidth-2*pad,maxH=outputHeight-2*pad;
        float dw=maxW,dh=dw*sh/sw;
        if(dh>maxH){dh=maxH;dw=dh*sw/sh;}
        float left=(outputWidth-dw)/2f,top=(outputHeight-dh)/2f;
        return new GlitchCoordinates(left/outputWidth,top/outputHeight,
            (left+dw)/outputWidth,(top+dh)/outputHeight,0,0,1,1);
    }

    /** Returns null outside the visible photograph (e.g. screenshot frame margin). */
    public Point toSource(float outX,float outY){
        if(outX<left||outX>right||outY<top||outY>bottom)return null;
        float sx=sourceLeft+(outX-left)/(right-left)*(sourceRight-sourceLeft);
        float sy=sourceTop+(outY-top)/(bottom-top)*(sourceBottom-sourceTop);
        return new Point(Math.max(0,Math.min(1,sx)),Math.max(0,Math.min(1,sy)));
    }

    /** Locate a SOURCE brush mark on the cropped/framed output, or null if cropped away. */
    public Point toOutput(float srcX,float srcY){
        if(srcX<sourceLeft||srcX>sourceRight||srcY<sourceTop||srcY>sourceBottom)return null;
        float x=left+(srcX-sourceLeft)/(sourceRight-sourceLeft)*(right-left);
        float y=top+(srcY-sourceTop)/(sourceBottom-sourceTop)*(bottom-top);
        return new Point(x,y);
    }

    public Point viewToSource(float viewX,float viewY,float viewLeft,float viewTop,float viewWidth,float viewHeight){
        if(viewWidth<=0||viewHeight<=0)return null;
        return toSource((viewX-viewLeft)/viewWidth,(viewY-viewTop)/viewHeight);
    }
}