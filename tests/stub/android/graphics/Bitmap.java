package android.graphics;
public final class Bitmap {
    public enum Config { ARGB_8888 }
    private final int width,height;
    private final int[] pixels;
    private Bitmap(int w,int h){width=w;height=h;pixels=new int[w*h];}
    public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}
    public int getWidth(){return width;}
    public int getHeight(){return height;}
    public void getPixels(int[] dst,int off,int stride,int x,int y,int w,int h){
        for(int yy=0;yy<h;yy++)System.arraycopy(pixels,(yy+y)*width+x,dst,off+yy*stride,w);
    }
    public void setPixels(int[] src,int off,int stride,int x,int y,int w,int h){
        for(int yy=0;yy<h;yy++)System.arraycopy(src,off+yy*stride,pixels,(yy+y)*width+x,w);
    }
}