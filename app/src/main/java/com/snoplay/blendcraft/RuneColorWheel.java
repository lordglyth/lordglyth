package com.snoplay.blendcraft;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Touch-accessible HSV color wheel for Shifting Runes Curse.
 * Color selection happens in the actual circular surface, not a static swatch.
 */
public final class RuneColorWheel extends View {
    public interface Listener { void onColor(int argb); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final float[] hsv={165f,.72f,1f};
    private Bitmap wheel;
    private Listener listener;
    public RuneColorWheel(Context context,int initial,Listener cb){
        super(context);
        listener=cb;
        Color.colorToHSV(initial,hsv);
        hsv[2]=1f;
        setContentDescription("Rune glow color wheel. Touch to select hue and saturation.");
    }
    public int getColor(){return Color.HSVToColor(255,hsv);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        if(w<=0||h<=0)return;
        int size=Math.min(w,h);
        int[] pixels=new int[size*size];
        float c=(size-1)*.5f,r=size*.46f;
        float[] sample={0,0,1};
        for(int y=0;y<size;y++)for(int x=0;x<size;x++){
            float dx=x-c,dy=y-c,mag=(float)Math.sqrt(dx*dx+dy*dy);
            if(mag>r){pixels[y*size+x]=0;continue;}
            sample[0]=(float)((Math.toDegrees(Math.atan2(dy,dx))+450)%360);
            sample[1]=Math.min(1,mag/r);
            pixels[y*size+x]=Color.HSVToColor(sample);
        }
        if(wheel!=null)wheel.recycle();
        wheel=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(wheel==null)return;
        float x=(getWidth()-wheel.getWidth())*.5f;
        float y=(getHeight()-wheel.getHeight())*.5f;
        paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);
        canvas.drawBitmap(wheel,x,y,paint);
        float centerX=getWidth()*.5f,centerY=getHeight()*.5f;
        float radius=wheel.getWidth()*.46f;
        float angle=(float)Math.toRadians(hsv[0]-90);
        float px=centerX+(float)Math.cos(angle)*radius*hsv[1];
        float py=centerY+(float)Math.sin(angle)*radius*hsv[1];
        paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5);
        canvas.drawCircle(px,py,12,paint);
        paint.setColor(Color.WHITE);paint.setStrokeWidth(3);
        canvas.drawCircle(px,py,10,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(getColor());canvas.drawCircle(px,py,7,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int act=event.getActionMasked();
        if(act!=MotionEvent.ACTION_DOWN && act!=MotionEvent.ACTION_MOVE && act!=MotionEvent.ACTION_UP)
            return super.onTouchEvent(event);
        float dx=event.getX()-getWidth()*.5f,dy=event.getY()-getHeight()*.5f;
        float r=(float)Math.hypot(dx,dy),max=Math.min(getWidth(),getHeight())*.46f;
        hsv[0]=(float)((Math.toDegrees(Math.atan2(dy,dx))+450)%360);
        hsv[1]=Math.max(0,Math.min(1,r/max));
        hsv[2]=1f;
        invalidate();
        if(listener!=null)listener.onColor(getColor());
        return true;
    }
}