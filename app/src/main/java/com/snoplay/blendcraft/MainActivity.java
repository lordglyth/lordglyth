package com.snoplay.blendcraft;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(13,16,35);
    private static final int PANEL = Color.rgb(28,32,59);
    private static final int CYAN = Color.rgb(139,232,236);
    private static final int PINK = Color.rgb(255,142,210);
    private static final int WHITE = Color.rgb(246,244,255);
    private static final String[] RATIOS = {"Original","1:1","4:5","9:16","16:9"};
    private static final int[] INKS = {Color.WHITE, Color.CYAN, Color.YELLOW, Color.MAGENTA, Color.BLACK};
    private Bitmap source;
    private Bitmap rotatedCache;
    private int cacheAngle = -1;
    private String mode = "Photo";
    private State state = new State();
    private final ArrayDeque<State> undo = new ArrayDeque<>();
    private final ArrayDeque<State> redo = new ArrayDeque<>();
    private Preview preview;
    private boolean drawing = false;
    private int inkIndex = 0;
    private Stroke activeStroke;
    private String nextMode = "Photo";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final SharedPreferences.OnSharedPreferenceChangeListener noop = (prefs, key) -> {};

    static class Stroke {
        int color;
        ArrayList<Float> coords = new ArrayList<>();
        Stroke copy() {
            Stroke s = new Stroke();
            s.color = color;
            s.coords = new ArrayList<>(coords);
            return s;
        }
    }
    static class State {
        int rotation = 0;
        int ratio = 0;
        int brightness = 0, contrast = 100, saturation = 100;
        int frame = 0, textColor = Color.WHITE;
        String top = "", bottom = "", extra = "", watermark = "Made with BlendCraft";
        boolean waterOn = false;
        int watermarkAlpha = 65;
        float waterX = .5f, waterY = .93f;
        ArrayList<Stroke> strokes = new ArrayList<>();
        State copy() {
            State s = new State();
            s.rotation=rotation; s.ratio=ratio; s.brightness=brightness;
            s.contrast=contrast; s.saturation=saturation; s.frame=frame;
            s.textColor=textColor; s.top=top; s.bottom=bottom;
            s.extra=extra; s.watermark=watermark; s.waterOn=waterOn;
            s.watermarkAlpha=watermarkAlpha; s.waterX=waterX; s.waterY=waterY;
            for(Stroke stroke:strokes) s.strokes.add(stroke.copy());
            return s;
        }
    }

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showHome();
        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType()!=null
                && intent.getType().startsWith("image/")) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(uri!=null) loadImage(uri,"Screenshot");
        }
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if(Intent.ACTION_SEND.equals(intent.getAction()) && intent.getType()!=null && intent.getType().startsWith("image/")) {
            Uri uri=intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(uri!=null) loadImage(uri,"Screenshot");
        }
    }
    private int dp(float n) {return (int)(n * getResources().getDisplayMetrics().density + .5f);}
    private LinearLayout column() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout row() {
        LinearLayout l=new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private TextView text(String content,int size,int color,boolean bold) {
        TextView t=new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(color);
        if(bold)t.setTypeface(null,1);
        return t;
    }
    private android.graphics.drawable.GradientDrawable box(int color,int radius) {
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    private Button button(String label,Runnable click) {
        Button b=new Button(this);
        b.setText(label); b.setAllCaps(false); b.setTextColor(WHITE); b.setTextSize(13);
        b.setBackground(box(PANEL,16)); b.setMinimumHeight(dp(42));
        b.setPadding(dp(14),dp(5),dp(14),dp(5));
        b.setOnClickListener(v->click.run());
        return b;
    }
    private void gap(LinearLayout parent,int n) {
        View v=new View(this);
        parent.addView(v,new LinearLayout.LayoutParams(1,dp(n)));
    }
    private void toast(String msg) {Toast.makeText(this,msg,Toast.LENGTH_SHORT).show();}
    private TextView heading(String title,String sub) {
        LinearLayout wrap=column(); wrap.setPadding(dp(22),dp(18),dp(22),dp(10));
        TextView h=text(title,28,WHITE,true); wrap.addView(h);
        TextView s=text(sub,13,Color.rgb(185,194,221),false); wrap.addView(s);
        // Return headline, caller can add wrapper directly where needed.
        return h;
    }
    private LinearLayout card(String icon,String title,String summary,int accent,Runnable action) {
        LinearLayout card=column(); card.setPadding(dp(18),dp(17),dp(18),dp(17));
        card.setBackground(box(PANEL,22));
        TextView a=text(icon+"   "+title+"   ›",20,accent,true);
        card.addView(a);
        gap(card,8);
        card.addView(text(summary,13,Color.rgb(197,204,228),false));
        card.setOnClickListener(v->action.run());
        return card;
    }
    private void showHome() {
        LinearLayout base=column(); base.setBackgroundColor(BG);
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); base.addView(sc);
        LinearLayout body=column(); body.setPadding(dp(20),dp(28),dp(20),dp(28)); sc.addView(body);
        TextView logo=text("BLENDCRAFT  ✦",14,CYAN,true); body.addView(logo);
        gap(body,15);
        body.addView(text("Create something\nworth sharing.",34,WHITE,true));
        gap(body,10);
        body.addView(text("Three studios. One canvas. Zero subscriptions or internet required.",14,Color.rgb(188,198,225),false));
        gap(body,22);
        LinearLayout hero=column(); hero.setPadding(dp(20),dp(22),dp(20),dp(22));
        hero.setBackground(new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF5346AB,0xFF3C789B,0xFF1E9A9D}));
        hero.addView(text("✦  YOUR CREATIVE PLAYGROUND",12,WHITE,true));
        gap(hero,8);
        hero.addView(text("Edit photos. Dress up screenshots. Make memes.",21,WHITE,true));
        gap(hero,12);
        Button begin=button("Import a picture  →",()->pick("Photo"));
        begin.setTextColor(BG); begin.setBackground(box(CYAN,14)); hero.addView(begin);
        body.addView(hero);
        gap(body,21);
        body.addView(text("CHOOSE YOUR STUDIO",12,PINK,true));
        gap(body,12);
        body.addView(card("◈","Photo Studio","Crop, rotate, adjust color and add text.",CYAN,()->pick("Photo")));
        gap(body,10);
        body.addView(card("▧","Screenshot Studio","Gradient frames, markup and clean exports.",PINK,()->pick("Screenshot")));
        gap(body,10);
        body.addView(card("✦","Meme Studio","Classic captions, stickers and quick edits.",0xFFFFD991,()->pick("Meme")));
        gap(body,10);
        body.addView(card("◉","Blank Canvas","Start creating without importing a picture.",0xFFB4ACFF,()->startBlank()));
        gap(body,10);
        body.addView(card("▤","My Creations","Reopen images saved from BlendCraft.",CYAN,()->showCreations()));
        gap(body,18);
        body.addView(text("SnoPlay • All editing stays on your device",12,Color.rgb(153,160,193),false));
        applySafeArea(base);
        setContentView(base);
    }
    private void pick(String pickMode) {
        nextMode=pickMode;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent,12);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==12 && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);} catch(Exception ignored){}
            loadImage(uri,nextMode);
        }
    }
    private void loadImage(Uri uri,String studio) {
        try(InputStream input=getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inJustDecodeBounds=true;
            BitmapFactory.decodeStream(input,null,options);
            int sample=1;
            while(options.outWidth/sample>2600 || options.outHeight/sample>2600) sample*=2;
            options.inSampleSize=sample; options.inJustDecodeBounds=false;
            try(InputStream actual=getContentResolver().openInputStream(uri)) {
                Bitmap bm=BitmapFactory.decodeStream(actual,null,options);
                if(bm==null)throw new Exception("Not a supported image.");
                source=bm; rotatedCache=null; cacheAngle=-1;
                state=new State(); undo.clear(); redo.clear(); mode=studio;
                if("Meme".equals(mode)){state.top="TOP TEXT";state.bottom="BOTTOM TEXT";}
                showStudio();
            }
        } catch(Exception error){toast("Could not open image: "+error.getMessage());}
    }
    private void startBlank() {
        Bitmap bm=Bitmap.createBitmap(1080,1350,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(bm);
        Paint p=new Paint(3);
        p.setShader(new LinearGradient(0,0,1080,1350,new int[]{0xFF402E83,0xFF166778,0xFF15192F},null,Shader.TileMode.CLAMP));
        c.drawRect(0,0,1080,1350,p); p.setShader(null);
        p.setColor(0x33FFFFFF);
        c.drawCircle(840,240,320,p); c.drawCircle(120,1050,280,p);
        p.setColor(Color.WHITE);p.setTypeface(android.graphics.Typeface.create("sans-serif-black",1));
        p.setTextSize(90);c.drawText("BLENDCRAFT",115,680,p);
        p.setTextSize(42);c.drawText("Your canvas is ready.",115,756,p);
        source=bm;rotatedCache=null;cacheAngle=-1;
        state=new State();undo.clear();redo.clear();mode="Meme";showStudio();
    }
    private void setNewMode(String studio) {
        if(source==null){pick(studio);return;}
        push();mode=studio;showStudio();
    }
    private void push() {
        if(undo.size()>35) undo.removeLast();
        undo.push(state.copy());redo.clear();
    }
    private void undoAction() {
        if(undo.isEmpty()){toast("Nothing to undo");return;}
        redo.push(state.copy());state=undo.pop();showStudio();
    }
    private void redoAction() {
        if(redo.isEmpty()){toast("Nothing to redo");return;}
        undo.push(state.copy());state=redo.pop();showStudio();
    }
    private void textPrompt(String title,String initial,java.util.function.Consumer<String> done) {
        EditText box=new EditText(this);box.setSingleLine(false);
        box.setText(initial);box.setTextColor(Color.BLACK);
        box.setPadding(dp(18),dp(10),dp(18),dp(10));
        new AlertDialog.Builder(this).setTitle(title).setView(box)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(d,w)->{push();done.accept(box.getText().toString());showStudio();})
            .show();
    }
    private void filters() {
        LinearLayout controls=column();controls.setPadding(dp(20),dp(4),dp(20),dp(4));
        final int[] values={state.brightness+100,state.contrast,state.saturation};
        String[] labels={"Brightness","Contrast","Saturation"};
        for(int i=0;i<3;i++){
            final int j=i;
            TextView label=text(labels[i]+"  "+(values[i]-(i==0?100:0)),15,Color.BLACK,false);
            controls.addView(label);
            SeekBar seek=new SeekBar(this);
            seek.setMax(200);seek.setProgress(values[i]);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar b,int n,boolean user){values[j]=n;label.setText(labels[j]+"  "+(n-(j==0?100:0)));}
                public void onStartTrackingTouch(SeekBar b){}
                public void onStopTrackingTouch(SeekBar b){}
            });
            controls.addView(seek);
        }
        new AlertDialog.Builder(this).setTitle("Adjust Colors").setView(controls)
            .setNegativeButton("Cancel",null)
            .setNeutralButton("Reset",(d,w)->{push();state.brightness=0;state.contrast=100;state.saturation=100;showStudio();})
            .setPositiveButton("Apply",(d,w)->{push();state.brightness=values[0]-100;state.contrast=values[1];state.saturation=values[2];showStudio();})
            .show();
    }
    private void captionDialog() {
        EditText top=new EditText(this);top.setHint("Top caption");top.setText(state.top);
        top.setSingleLine(true);top.setSelectAllOnFocus(true);
        top.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText bottom=new EditText(this);bottom.setHint("Bottom caption");bottom.setText(state.bottom);
        bottom.setSingleLine(true);bottom.setSelectAllOnFocus(true);
        bottom.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout stack=column();stack.setPadding(dp(18),dp(4),dp(18),dp(4));
        stack.addView(top);stack.addView(bottom);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Edit meme captions").setView(stack)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Apply",(d,w)->{push();state.top=top.getText().toString();
                state.bottom=bottom.getText().toString();showStudio();}).create();
        dialog.getWindow();
        dialog.show();
        top.requestFocus();
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }
    private void watermarkDialog() {
        EditText edit=new EditText(this);edit.setHint("Your signature / brand");
        edit.setText(state.watermark);
        LinearLayout stack=column();stack.setPadding(dp(18),dp(6),dp(18),dp(6));
        stack.addView(edit);
        TextView label=text("Opacity",14,Color.DKGRAY,false);stack.addView(label);
        SeekBar alpha=new SeekBar(this);alpha.setMax(100);alpha.setProgress(state.watermarkAlpha);stack.addView(alpha);
        new AlertDialog.Builder(this).setTitle("Personal watermark").setView(stack)
            .setNegativeButton("Cancel",null)
            .setNeutralButton(state.waterOn?"Turn off":"Turn on",(d,w)->{push();state.waterOn=!state.waterOn;showStudio();})
            .setPositiveButton("Apply",(d,w)->{push();state.watermark=edit.getText().toString();
                state.watermarkAlpha=alpha.getProgress();state.waterOn=true;showStudio();}).show();
    }
    // Keep the editor away from Android's status, gesture and navigation bars.
    private void applySafeArea(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });
        root.requestApplyInsets();
    }

    private void showStudio() {
        if(source==null){showHome();return;}
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout top=row();top.setPadding(dp(10),dp(10),dp(10),dp(7));
        Button back=button("‹ Home",()->showHome());
        top.addView(back);
        TextView title=text("  "+mode+" Studio",18,WHITE,true);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        Button save=button("Save ↓",()->save(false));
        save.setBackground(box(0xFF426E82,14));top.addView(save);
        root.addView(top);
        LinearLayout switcher=row();switcher.setPadding(dp(10),0,dp(10),dp(5));
        for(String m:new String[]{"Photo","Screenshot","Meme"}){
            Button b=button(m,()->setNewMode(m));
            b.setTextSize(12);
            if(mode.equals(m)){b.setTextColor(BG);b.setBackground(box(CYAN,14));}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
            lp.setMargins(dp(3),0,dp(3),0);switcher.addView(b,lp);
        }
        root.addView(switcher);
        // Primary text tools must not be buried inside the scrolling toolbar.
        LinearLayout textActions=row();
        textActions.setPadding(dp(10),dp(2),dp(10),dp(8));
        Button editCaptions=button("✎ Edit meme captions",()->captionDialog());
        editCaptions.setBackground(box(CYAN,14));
        editCaptions.setTextColor(BG);
        editCaptions.setTextSize(14);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(49),1);
        cp.setMargins(dp(3),0,dp(3),0);
        textActions.addView(editCaptions,cp);
        Button addText=button("＋ Add text",()->textPrompt("Add text or emoji",state.extra,s->{state.extra=s;}));
        addText.setTextSize(14);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(49),1);
        ap.setMargins(dp(3),0,dp(3),0);
        textActions.addView(addText,ap);
        root.addView(textActions);
        FrameLayout canvasHolder=new FrameLayout(this);
        canvasHolder.setBackgroundColor(0xFF171B32);
        preview=new Preview();
        canvasHolder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        root.addView(canvasHolder,new LinearLayout.LayoutParams(-1,0,1));
        TextView tip=text(drawing?"DRAW MODE · Drag to annotate":"Tap artwork to move your watermark",11,0xFFCAD6EA,false);
        tip.setGravity(Gravity.CENTER);tip.setPadding(0,dp(7),0,dp(5));
        root.addView(tip);
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);
        LinearLayout bar=row();bar.setPadding(dp(8),dp(5),dp(8),dp(5));
        addTool(bar,"↶ Undo",()->undoAction());
        addTool(bar,"↷ Redo",()->redoAction());
        addTool(bar,"Crop "+RATIOS[state.ratio],()->{push();state.ratio=(state.ratio+1)%RATIOS.length;showStudio();});
        addTool(bar,"Rotate ↻",()->{push();state.rotation=(state.rotation+90)%360;rotatedCache=null;showStudio();});
        addTool(bar,"Adjust ◐",()->filters());
        addTool(bar,"Captions",()->captionDialog());
        addTool(bar,"Add text",()->textPrompt("Text or emoji",state.extra,s->{state.extra=s;}));
        addTool(bar,"Text color",()->{push();state.textColor=state.textColor==Color.WHITE?0xFFFCE269:state.textColor==0xFFFCE269?CYAN:Color.WHITE;showStudio();});
        addTool(bar,drawing?"✓ Draw on":"✎ Draw",()->{drawing=!drawing;showStudio();});
        addTool(bar,"Ink",()->{inkIndex=(inkIndex+1)%INKS.length;toast("Ink color changed");});
        addTool(bar,"Clear ink",()->{push();state.strokes.clear();showStudio();});
        addTool(bar,"Frame ✦",()->{push();state.frame=(state.frame+1)%4;mode="Screenshot";showStudio();});
        addTool(bar,"Watermark",()->watermarkDialog());
        addTool(bar,"New photo",()->pick(mode));
        sc.addView(bar);root.addView(sc);
        LinearLayout actions=row();actions.setPadding(dp(10),dp(4),dp(10),dp(12));
        Button export=button("↓ Export PNG",()->save(false));
        export.setBackground(box(CYAN,17));export.setTextColor(BG);
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(51),1));
        Button share=button("↗ Share",()->save(true));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(51),1);
        sp.setMargins(dp(8),0,0,0);actions.addView(share,sp);
        root.addView(actions);
        applySafeArea(root);
        setContentView(root);
    }
    private void addTool(LinearLayout bar,String name,Runnable cb){
        Button b=button(name,cb);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));
        lp.setMargins(dp(3),0,dp(3),0);
        bar.addView(b,lp);
    }
    private Bitmap rotated() {
        if(state.rotation==0)return source;
        if(rotatedCache!=null && cacheAngle==state.rotation)return rotatedCache;
        Matrix m=new Matrix();m.postRotate(state.rotation);
        if(rotatedCache!=null && rotatedCache!=source && !rotatedCache.isRecycled())rotatedCache.recycle();
        rotatedCache=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);
        cacheAngle=state.rotation;return rotatedCache;
    }
    private float imageRatio(){
        if(source==null)return 1;
        if(state.rotation%180==0)return (float)source.getWidth()/source.getHeight();
        return (float)source.getHeight()/source.getWidth();
    }
    private float outputRatio() {
        switch(state.ratio){
            case 1: return 1f;
            case 2: return 4f/5f;
            case 3: return 9f/16f;
            case 4: return 16f/9f;
            default: return "Screenshot".equals(mode)?1f:imageRatio();
        }
    }
    private int outputWidth(){return 1440;}
    private int outputHeight(){return Math.max(450,Math.min(2800,Math.round(1440f/outputRatio())));}
    private Rect crop(Bitmap bm,float ratio) {
        int w=bm.getWidth(),h=bm.getHeight();
        float r=(float)w/h;
        if(r>ratio){int newW=Math.round(h*ratio);return new Rect((w-newW)/2,0,(w+newW)/2,h);}
        int newH=Math.round(w/ratio);return new Rect(0,(h-newH)/2,w,(h+newH)/2);
    }
    private Paint filteredPaint(){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix sat=new ColorMatrix();sat.setSaturation(state.saturation/100f);
        ColorMatrix levels=new ColorMatrix(new float[]{
            state.contrast/100f,0,0,0,(1-state.contrast/100f)*128+state.brightness*1.3f,
            0,state.contrast/100f,0,0,(1-state.contrast/100f)*128+state.brightness*1.3f,
            0,0,state.contrast/100f,0,(1-state.contrast/100f)*128+state.brightness*1.3f,
            0,0,0,1,0});
        sat.postConcat(levels);p.setColorFilter(new ColorMatrixColorFilter(sat));return p;
    }
    private void drawBitmap(Canvas c,Bitmap bm,RectF dst, boolean crop){
        c.save();c.clipRect(dst);
        if(crop)c.drawBitmap(bm,crop(bm,dst.width()/dst.height()),dst,filteredPaint());
        else {
            float scale=Math.min(dst.width()/bm.getWidth(),dst.height()/bm.getHeight());
            float w=bm.getWidth()*scale,h=bm.getHeight()*scale;
            RectF fitted=new RectF(dst.centerX()-w/2,dst.centerY()-h/2,dst.centerX()+w/2,dst.centerY()+h/2);
            c.drawBitmap(bm,null,fitted,filteredPaint());
        }
        c.restore();
    }
    private void artwork(Canvas c,int w,int h) {
        c.drawColor(BG);
        Bitmap bm=rotated();
        if("Screenshot".equals(mode)){
            int[][] gradients={
                {0xFF7C55CF,0xFF33BDD2},{0xFFFF75B4,0xFFFFD0A1},
                {0xFF102B56,0xFF54BFC2},{0xFF212136,0xFF8B76BD}};
            int[] g=gradients[state.frame];
            Paint gp=new Paint(3);
            gp.setShader(new LinearGradient(0,0,w,h,g[0],g[1],Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,gp);gp.setShader(null);
            float pad=w*.09f,outerPad=w*.025f;
            float maxW=w-2*pad,maxH=h-2*pad;
            float ratio=(float)bm.getWidth()/bm.getHeight();
            float dw=maxW,dh=dw/ratio;
            if(dh>maxH){dh=maxH;dw=dh*ratio;}
            RectF dst=new RectF((w-dw)/2,(h-dh)/2,(w+dw)/2,(h+dh)/2);
            gp.setColor(0x88000000);gp.setShadowLayer(w*.025f,0,w*.018f,0x77000000);
            c.drawRoundRect(new RectF(dst.left-outerPad,dst.top-outerPad,dst.right+outerPad,dst.bottom+outerPad),w*.023f,w*.023f,gp);
            gp.clearShadowLayer();
            gp.setColor(Color.WHITE);
            c.drawRoundRect(new RectF(dst.left-outerPad,dst.top-outerPad,dst.right+outerPad,dst.bottom+outerPad),w*.02f,w*.02f,gp);
            drawBitmap(c,bm,dst,false);
        }else{
            RectF full=new RectF(0,0,w,h);drawBitmap(c,bm,full,true);
        }
        for(Stroke s:state.strokes) {
            if(s.coords.size()<4)continue;
            Paint line=new Paint(3);line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);
            line.setColor(s.color);line.setStrokeWidth(w*.009f);
            Path path=new Path();path.moveTo(s.coords.get(0)*w,s.coords.get(1)*h);
            for(int j=2;j+1<s.coords.size();j+=2)path.lineTo(s.coords.get(j)*w,s.coords.get(j+1)*h);
            c.drawPath(path,line);
        }
        drawLabel(c,state.top,w/2f,h*.1f,w*.085f,w*.92f,state.textColor,true);
        drawLabel(c,state.bottom,w/2f,h*.93f,w*.085f,w*.92f,state.textColor,true);
        drawLabel(c,state.extra,w/2f,h*.52f,w*.07f,w*.9f,state.textColor,false);
        if(state.waterOn&&!state.watermark.trim().isEmpty()){
            Paint wp=new Paint(3);wp.setTypeface(android.graphics.Typeface.create("sans-serif-medium",1));
            wp.setTextSize(w*.035f);wp.setColor(Color.WHITE);
            wp.setAlpha(Math.round(state.watermarkAlpha*2.55f));
            wp.setShadowLayer(w*.004f,0,w*.003f,0xFF000000);
            wp.setTextAlign(Paint.Align.CENTER);
            String stamp=state.watermark;
            if(wp.measureText(stamp)>w*.9f)wp.setTextSize(w*.035f*(w*.9f/wp.measureText(stamp)));
            c.drawText(stamp,state.waterX*w,state.waterY*h,wp);
        }
    }
    private void drawLabel(Canvas c,String s,float x,float y,float font,float maxWidth,int color,boolean impact){
        if(s==null || s.trim().isEmpty())return;
        Paint t=new Paint(3);t.setTypeface(android.graphics.Typeface.create(impact?"sans-serif-condensed":"sans-serif",1));
        t.setTextAlign(Paint.Align.CENTER);t.setColor(color);t.setTextSize(font);
        t.setStrokeJoin(Paint.Join.ROUND);
        String[] lines=s.split("\n",-1);
        for(int j=0;j<lines.length;j++){
            String line=lines[j];
            float size=font;
            if(t.measureText(line)>maxWidth)size=font*maxWidth/t.measureText(line);
            t.setTextSize(Math.max(size,font*.45f));
            float py=y+j*font*1.1f;
            t.setStyle(Paint.Style.STROKE);t.setStrokeWidth(Math.max(2,font*.085f));t.setColor(Color.BLACK);
            c.drawText(line,x,py,t);
            t.setStyle(Paint.Style.FILL);t.setColor(color);
            c.drawText(line,x,py,t);
        }
    }
    private class Preview extends View {
        float left,top,scaledW,scaledH;
        Preview(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w=outputWidth(),h=outputHeight();
            float scale=Math.min((getWidth()-dp(20))/(float)w,(getHeight()-dp(20))/(float)h);
            if(scale<=0)return;
            scaledW=w*scale;scaledH=h*scale;
            left=(getWidth()-scaledW)/2;top=(getHeight()-scaledH)/2;
            Paint shadow=new Paint(3);shadow.setColor(0xAA000000);shadow.setShadowLayer(dp(8),0,dp(4),0xAA000000);
            canvas.drawRect(left,top,left+scaledW,top+scaledH,shadow);
            canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);
            artwork(canvas,w,h);canvas.restore();
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            int a=event.getActionMasked();
            if(a!=MotionEvent.ACTION_DOWN && a!=MotionEvent.ACTION_MOVE && a!=MotionEvent.ACTION_UP)return true;
            if(scaledW<=0 || scaledH<=0)return true;
            float x=Math.max(0,Math.min(1,(event.getX()-left)/scaledW));
            float y=Math.max(0,Math.min(1,(event.getY()-top)/scaledH));
            if(drawing){
                if(a==MotionEvent.ACTION_DOWN){
                    push();activeStroke=new Stroke();activeStroke.color=INKS[inkIndex];
                    activeStroke.coords.add(x);activeStroke.coords.add(y);state.strokes.add(activeStroke);
                }else if(a==MotionEvent.ACTION_MOVE && activeStroke!=null){
                    activeStroke.coords.add(x);activeStroke.coords.add(y);
                }else if(a==MotionEvent.ACTION_UP)activeStroke=null;
            }else if(state.waterOn){
                if(a==MotionEvent.ACTION_DOWN)push();
                state.waterX=x;state.waterY=y;
            }
            invalidate();return true;
        }
    }
    private Bitmap exportBitmap() {
        Bitmap result=Bitmap.createBitmap(outputWidth(),outputHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);artwork(c,result.getWidth(),result.getHeight());return result;
    }
    private void save(boolean share) {
        try {
            Bitmap bitmap=exportBitmap();
            ContentValues cv=new ContentValues();
            String name="BlendCraft_"+System.currentTimeMillis()+".png";
            cv.put(MediaStore.Images.Media.DISPLAY_NAME,name);
            cv.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/BlendCraft");
            cv.put(MediaStore.Images.Media.IS_PENDING,1);
            Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
            if(uri==null)throw new Exception("Media library unavailable");
            try(OutputStream stream=getContentResolver().openOutputStream(uri)){
                if(stream==null || !bitmap.compress(Bitmap.CompressFormat.PNG,100,stream))
                    throw new Exception("PNG encoder failed");
            } catch(Exception ex){getContentResolver().delete(uri,null,null);throw ex;}
            cv.clear();cv.put(MediaStore.Images.Media.IS_PENDING,0);
            getContentResolver().update(uri,cv,null,null);
            bitmap.recycle();
            addRecent(uri);
            if(share){
                Intent i=new Intent(Intent.ACTION_SEND);i.setType("image/png");
                i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i,"Share BlendCraft creation"));
            }else toast("Saved to Pictures/BlendCraft");
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("Export failed")
            .setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void addRecent(Uri uri) {
        try {
            SharedPreferences pref=getSharedPreferences("creations",MODE_PRIVATE);
            JSONArray prior=new JSONArray(pref.getString("items","[]"));
            JSONArray arr=new JSONArray();arr.put(uri.toString());
            for(int i=0;i<prior.length() && i<39;i++){
                String item=prior.optString(i);
                if(!item.equals(uri.toString()))arr.put(item);
            }
            pref.edit().putString("items",arr.toString()).apply();
        }catch(Exception ignored){}
    }
    private void showCreations(){
        LinearLayout base=column();base.setBackgroundColor(BG);
        ScrollView sc=new ScrollView(this);base.addView(sc);
        LinearLayout body=column();body.setPadding(dp(16),dp(16),dp(16),dp(16));sc.addView(body);
        body.addView(button("‹ Back to studios",()->showHome()));
        gap(body,16);body.addView(text("My Creations",28,WHITE,true));gap(body,8);
        body.addView(text("Images you exported from BlendCraft. Tap to edit again.",13,CYAN,false));
        gap(body,16);
        try{
            JSONArray arr=new JSONArray(getSharedPreferences("creations",MODE_PRIVATE).getString("items","[]"));
            if(arr.length()==0)body.addView(text("Nothing here yet. Make something!",15,WHITE,false));
            for(int j=0;j<arr.length();j++){
                Uri uri=Uri.parse(arr.optString(j));
                final int n=j+1;
                LinearLayout tile=card("▧","Creation "+n,"Reopen a saved PNG",CYAN,()->loadImage(uri,"Photo"));
                body.addView(tile);gap(body,8);
            }
        }catch(Exception ignored){toast("Couldn't list your recent creations");}
        applySafeArea(base);
        setContentView(base);
    }
}
