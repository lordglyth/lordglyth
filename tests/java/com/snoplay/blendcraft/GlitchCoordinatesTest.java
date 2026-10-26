package com.snoplay.blendcraft;

/** Precision checks for brush placement across output crops, screenshot framing, zoom and pan. */
public final class GlitchCoordinatesTest {
    static void check(boolean b,String name){if(!b)throw new AssertionError(name);}
    static void near(float a,float b,String name){
        if(Math.abs(a-b)>.0006f)throw new AssertionError(name+": "+a+" != "+b);
    }
    static void roundtrip(GlitchCoordinates m,float x,float y,String name){
        GlitchCoordinates.Point source=m.toSource(x,y);
        check(source!=null,name+" touch within photo");
        GlitchCoordinates.Point canvas=m.toOutput(source.x,source.y);
        check(canvas!=null,name+" source is displayed");
        near(canvas.x,x,name+" x");
        near(canvas.y,y,name+" y");
    }
    public static void main(String[] args){
        GlitchCoordinates uncropped=GlitchCoordinates.photo(1920,1080,1920f/1080);
        roundtrip(uncropped,.1f,.95f,"uncropped");
        near(uncropped.toSource(.45f,.31f).x,.45f,"full image untouched");
        GlitchCoordinates square=GlitchCoordinates.photo(1920,1080,1f);
        near(square.toSource(0,0).x,420f/1920,"landscape square crop left");
        near(square.toSource(1,1).x,1500f/1920,"landscape square crop right");
        roundtrip(square,.17f,.83f,"landscape square");
        GlitchCoordinates portrait=GlitchCoordinates.photo(1080,1920,1f);
        near(portrait.toSource(.5f,0).y,420f/1920,"portrait square crop top");
        near(portrait.toSource(.5f,1).y,1500f/1920,"portrait square crop bottom");
        roundtrip(portrait,.73f,.29f,"portrait square");
        GlitchCoordinates screenshot=GlitchCoordinates.screenshot(1920,1080,1080,1080);
        check(screenshot.toSource(.02f,.02f)==null,"screenshot frame is not paintable");
        near(screenshot.toSource(.5f,.5f).x,.5f,"screenshot center maps to source center");
        roundtrip(screenshot,.35f,.55f,"landscape screenshot");
        GlitchCoordinates tallScreenshot=GlitchCoordinates.screenshot(1080,1920,1080,1080);
        check(tallScreenshot.toSource(.03f,.5f)==null,"portrait screenshot sidebars are not paintable");
        roundtrip(tallScreenshot,.5f,.85f,"portrait screenshot");
        // This simulates a viewport scaled 2.7x with a negative pan in both axes.
        float left=-520f,top=-165f,width=2410f,height=1356f;
        GlitchCoordinates.Point p=square.viewToSource(447f,533f,left,top,width,height);
        check(p!=null,"zoomed and panned coordinate in bounds");
        GlitchCoordinates.Point screen=square.toOutput(p.x,p.y);
        near(left+screen.x*width,447f,"zoom/pan x alignment");
        near(top+screen.y*height,533f,"zoom/pan y alignment");
        check(square.viewToSource(-950f,-800f,left,top,width,height)==null,
            "touch outside zoomed canvas ignored");
        System.out.println("GlitchCoordinates tests PASS: crop, frames, zoom/pan, boundaries, roundtrips");
    }
}