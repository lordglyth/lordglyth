package com.snoplay.blendcraft;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import android.widget.ImageView;
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
import java.util.HashMap;
import java.util.Map;

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
    private boolean inGlitchLab = false, glitchBrushMode = false, glitchCompare = false;
    private int glitchTab = 0;
    private Bitmap glitchCache = null;
    private String glitchCacheKey = "", glitchRequestedKey = "";
    private final ExecutorService glitchWorkers = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean glitchWorking = false;
    private final ExecutorService thumbnailWorkers=Executors.newFixedThreadPool(2);
    private final Map<String,Bitmap> glitchThumbnails=new HashMap<>();
    private boolean runeLive=true, runeFrameScheduled=false;
    private final Runnable runeFrameTicker=new Runnable(){
        @Override public void run(){
            runeFrameScheduled=false;
            if(!inGlitchLab || !runeLive || !state.glitch.enabled())return;
            boolean changed=false;
            int speed=50;
            for(GlitchFx.Layer l:state.glitch.layers){
                if(l.type==30){
                    speed=l.runeSpeed;
                    if(!glitchWorking){l.phase=(l.phase+1)%8192;changed=true;}
                }
            }
            if(changed)requestGlitchRender();
            armRuneAnimation(speed);
        }
    };
    private void armRuneAnimation(int speed){
        if(runeFrameScheduled||!inGlitchLab||!runeLive)return;
        boolean found=false;
        for(GlitchFx.Layer l:state.glitch.layers)if(l.type==30)found=true;
        if(!found)return;
        runeFrameScheduled=true;
        mainHandler.postDelayed(runeFrameTicker,Math.max(180,850-speed*6));
    }
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
        String top = "", bottom = "", extra = "";
        ArrayList<Stroke> strokes = new ArrayList<>();
        GlitchFx.Config glitch=new GlitchFx.Config();
        State copy() {
            State s = new State();
            s.rotation=rotation; s.ratio=ratio; s.brightness=brightness;
            s.contrast=contrast; s.saturation=saturation; s.frame=frame;
            s.textColor=textColor; s.top=top; s.bottom=bottom;
            s.extra=extra;s.glitch=glitch.copy();
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
        LinearLayout wrapper=row();
        wrapper.setPadding(dp(13),dp(13),dp(14),dp(13));
        wrapper.setBackground(outlined(0xFF1A2038,0xFF35415E,20));
        TextView symbol=text(icon,24,accent,true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(gradient(0xFF333456,0xFF1D3655,17));
        wrapper.addView(symbol,new LinearLayout.LayoutParams(dp(55),dp(55)));
        LinearLayout descriptions=column();
        descriptions.setPadding(dp(13),0,dp(7),0);
        descriptions.addView(text(title,18,WHITE,true));
        gap(descriptions,4);
        descriptions.addView(text(summary,12,0xFFB8C2DF,false));
        wrapper.addView(descriptions,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=text("›",27,accent,true);
        wrapper.addView(arrow);
        wrapper.setElevation(dp(2));
        wrapper.setOnClickListener(v->action.run());
        return wrapper;
    }
    private class HeroArtwork extends View {
        HeroArtwork(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            canvas.save();canvas.scale(w/360f,h/198f);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0,0,360,200,new int[]{0xFF46307B,0xFF29558D,0xFF15908B},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(0,0,360,198,24,24,p);p.setShader(null);

            p.setColor(0x224BFFFF);
            canvas.drawCircle(310,24,100,p);canvas.drawCircle(14,185,75,p);
            for(int n=0;n<24;n++){
                float x=(n*63+29)%353,y=(n*41+19)%190;
                p.setColor(n%3==0?0x66E4FBEA:0x50FFFFFF);
                canvas.drawCircle(x,y,n%5==0?2f:1f,p);
            }

            // Floating photo, polished with shadows and borders.
            canvas.save();canvas.rotate(-10,112,105);
            p.setColor(0x69060022);p.setShadowLayer(11,1,8,0xAA060617);
            canvas.drawRoundRect(38,12,188,182,15,15,p);p.clearShadowLayer();
            p.setColor(0xFFF9F4FA);canvas.drawRoundRect(35,8,185,177,15,15,p);
            p.setShader(new LinearGradient(43,16,184,154,new int[]{0xFFB878CD,0xFFFFAC90,0xFFFFD7AB},null,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(45,19,175,145,9,9,p);p.setShader(null);
            p.setColor(0xFFFFE8B8);canvas.drawCircle(143,53,19,p);
            p.setColor(0xFF695EA6);
            Path hill=new Path();hill.moveTo(45,137);hill.lineTo(82,91);hill.lineTo(109,116);hill.lineTo(137,76);
            hill.lineTo(175,129);hill.lineTo(175,145);hill.lineTo(45,145);hill.close();canvas.drawPath(hill,p);
            p.setColor(0xFF303F85);
            Path hill2=new Path();hill2.moveTo(45,142);hill2.lineTo(84,119);hill2.lineTo(130,136);
            hill2.lineTo(175,98);hill2.lineTo(175,145);hill2.close();canvas.drawPath(hill2,p);
            p.setColor(0xFF332D50);p.setTypeface(android.graphics.Typeface.create("sans-serif-black",1));
            p.setTextSize(14);canvas.drawText("good vibes only",53,166,p);
            canvas.restore();

            canvas.save();canvas.rotate(9,230,102);
            p.setColor(0x99000012);p.setShadowLayer(12,2,5,0xAA03091A);
            canvas.drawRoundRect(166,20,312,180,15,15,p);p.clearShadowLayer();
            p.setColor(0xFFEBF8F8);canvas.drawRoundRect(164,18,310,177,15,15,p);
            p.setShader(new LinearGradient(170,25,306,170,0xFF173F68,0xFF2BCDA8,Shader.TileMode.CLAMP));
            canvas.drawRoundRect(173,27,301,150,11,11,p);p.setShader(null);
            p.setColor(0xFFFBEFAF);canvas.drawCircle(235,79,36,p);
            p.setColor(0xFF172C66);p.setTextSize(27);
            p.setTypeface(android.graphics.Typeface.create("sans-serif-black",1));
            p.setTextAlign(Paint.Align.CENTER);canvas.drawText("WOW",235,90,p);
            p.setColor(0xFF272C53);p.setTextSize(13);canvas.drawText("MAKE IT YOURS",239,169,p);
            p.setTextAlign(Paint.Align.LEFT);
            canvas.restore();
            // Floating sparkle badge
            p.setColor(0xFFFFE39D);p.setShadowLayer(8,0,0,0xFFFFD888);
            canvas.drawCircle(315,36,17,p);p.clearShadowLayer();
            p.setColor(0xFF603B79);p.setTextSize(22);canvas.drawText("✦",305,44,p);
            canvas.restore();
        }
    }
    private void showHome() {
        LinearLayout base=column(); base.setBackgroundColor(0xFF0B0E20);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);base.addView(sc);
        LinearLayout body=column();body.setPadding(dp(17),dp(19),dp(17),dp(25));sc.addView(body);

        LinearLayout brand=row();
        TextView mark=text("✦",25,BG,true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(gradient(0xFF8BEAE9,0xFFB59BFB,15));
        brand.addView(mark,new LinearLayout.LayoutParams(dp(45),dp(45)));
        LinearLayout brandLines=column();brandLines.setPadding(dp(10),0,0,0);
        TextView word=text("BLENDCRAFT",16,WHITE,true);word.setLetterSpacing(.16f);
        brandLines.addView(word);brandLines.addView(text("SNO PLAY  /  CREATOR SUITE",10,CYAN,true));
        brand.addView(brandLines,new LinearLayout.LayoutParams(0,-2,1));
        TextView v=text("V 1.1",11,LAVENDER,true);brand.addView(v);
        body.addView(brand);
        gap(body,24);
        body.addView(text("Make it look",29,WHITE,true));
        TextView iconic=text("iconic.",40,0xFF9BEDEB,true);body.addView(iconic);
        gap(body,7);
        body.addView(text("Photos, screenshots and memes — your creative toolkit, beautifully offline.",14,0xFFB6C1DA,false));
        gap(body,19);
        HeroArtwork art=new HeroArtwork();
        body.addView(art,new LinearLayout.LayoutParams(-1,dp(189)));
        gap(body,13);
        Button create=actionButton("＋  START CREATING  →",()->pick("Photo"),true);
        body.addView(create,new LinearLayout.LayoutParams(-1,dp(51)));
        gap(body,23);
        TextView group=kicker("CHOOSE YOUR CREATIVE SPACE");
        group.setTextColor(PINK);body.addView(group);gap(body,10);
        body.addView(card("◈","Photo Lab","Color grading, crops and overlays.",CYAN,()->pick("Photo")));
        gap(body,9);
        body.addView(card("▧","Screen Studio","Showcase screenshots in style.",LAVENDER,()->pick("Screenshot")));
        gap(body,9);
        body.addView(card("✎","Meme Maker","Instant captions and bold typography.",0xFFFFD58E,()->pick("Meme")));
        gap(body,9);
        body.addView(card("⚡","Glitch Lab","Ten live pixel effects, masks, brushes and layers.",LAVENDER,()->{
            if(source==null)pick("Photo");else showGlitchLab();
        }));
        gap(body,9);
        body.addView(card("✦","Blank Canvas","Create something from nothing.",PINK,()->startBlank()));
        gap(body,9);
        body.addView(card("▤","My Creations","Reopen your latest exported work.",CYAN,()->showCreations()));
        gap(body,18);
        TextView footer=text("PRIVATE BY DESIGN  •  NO ACCOUNT  •  OFFLINE",10,0xFF8B98B9,true);
        footer.setGravity(Gravity.CENTER);body.addView(footer);
        applySafeArea(base);setContentView(base);
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
            if(options.outWidth<=0 || options.outHeight<=0) throw new Exception("Invalid image dimensions");
            // Refuse large files instead of silently downsampling output resolution.
            if((long)options.outWidth*options.outHeight>12_000_000L)
                throw new Exception("Image exceeds 12MP memory-safe editing limit. Resize it explicitly first.");
            options.inSampleSize=1; options.inJustDecodeBounds=false;
            try(InputStream actual=getContentResolver().openInputStream(uri)) {
                Bitmap bm=BitmapFactory.decodeStream(actual,null,options);
                if(bm==null)throw new Exception("Not a supported image.");
                source=bm; rotatedCache=null; cacheAngle=-1;
                state=new State(); undo.clear(); redo.clear(); mode=studio;resetGlitchCache();
                // Start with empty captions; the visible editor uses helpful placeholders.
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
        state=new State();undo.clear();redo.clear();mode="Meme";resetGlitchCache();showStudio();
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
        redo.push(state.copy());state=undo.pop();if(inGlitchLab)showGlitchLab();else showStudio();
    }
    private void redoAction() {
        if(redo.isEmpty()){toast("Nothing to redo");return;}
        undo.push(state.copy());state=redo.pop();if(inGlitchLab)showGlitchLab();else showStudio();
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
        new AlertDialog.Builder(this)
            .setTitle("BlendCraft Free branding")
            .setMessage("Free exports include a permanent BlendCraft watermark in the lower-right corner. It cannot be hidden, moved, resized or removed in this version. Premium will allow watermark-free exports when secure billing is available.")
            .setPositiveButton("Got it",null).show();
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

    // BlendCraft Studio v1.1: a persistent, accessible inspector replaces hidden sideways tools.
    private int inspectorTab = 0; // TEXT / TUNE / FRAME / DRAW
    private static final int MUTED = 0xFFA6AFCE;
    private static final int LAVENDER = 0xFFBD9FFF;
    private static final int BORDER = 0xFF3D4568;

    private android.graphics.drawable.GradientDrawable gradient(int start,int end,int radius) {
        android.graphics.drawable.GradientDrawable d =
            new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{start,end});
        d.setCornerRadius(dp(radius));
        return d;
    }
    private android.graphics.drawable.GradientDrawable outlined(int background,int border,int radius) {
        android.graphics.drawable.GradientDrawable d=box(background,radius);
        d.setStroke(dp(1),border);
        return d;
    }
    private Button actionButton(String label,Runnable action,boolean primary) {
        Button b=button(label,action);
        b.setTextSize(14);b.setTypeface(null,1);
        b.setTextColor(primary?BG:WHITE);
        b.setBackground(primary?gradient(0xFF91F1EB,0xFF86C5F4,17):outlined(0xFF262B49,0xFF434F77,17));
        b.setElevation(dp(primary?4:1));
        return b;
    }
    private TextView kicker(String value) {
        TextView t=text(value,10,MUTED,true);
        t.setLetterSpacing(.14f);
        return t;
    }
    private void addFlexButton(LinearLayout row,String label,Runnable action,boolean primary){
        Button b=actionButton(label,action,primary);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);
        lp.setMargins(dp(3),0,dp(3),0);
        row.addView(b,lp);
    }
    private EditText captionInput(String value,String hint,java.util.function.Consumer<String> update) {
        EditText e=new EditText(this);
        e.setSingleLine(true);e.setTextSize(15);e.setTextColor(WHITE);e.setHintTextColor(0xFF8791B2);
        e.setSelectAllOnFocus(false);
        e.setPadding(dp(12),0,dp(12),0);
        e.setBackground(outlined(0xFF13192E,0xFF454E74,12));
        e.setHint(hint);
        e.setText(value);
        e.setOnFocusChangeListener((v,focus)->{ if(focus)push(); });
        e.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int cnt,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int cnt){
                update.accept(s.toString());
                if(preview!=null)preview.invalidate();
            }
            public void afterTextChanged(android.text.Editable ed){}
        });
        return e;
    }
    private void addCaptionField(LinearLayout r,String title,String value,String hint,java.util.function.Consumer<String> listener){
        LinearLayout item=column();
        item.addView(kicker(title));
        gap(item,6);
        EditText input=captionInput(value,hint,listener);
        item.addView(input,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(3),0,dp(3),0);r.addView(item,lp);
    }
    private void addTune(LinearLayout parent,String label,int value,java.util.function.IntConsumer update){
        LinearLayout block=row();
        TextView t=text(label,12,MUTED,true);
        block.addView(t,new LinearLayout.LayoutParams(dp(84),dp(28)));
        SeekBar slider=new SeekBar(this); slider.setMax(200);slider.setProgress(value);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int n,boolean fromUser){
                if(fromUser){update.accept(n);if(preview!=null)preview.invalidate();}
            }
            public void onStartTrackingTouch(SeekBar s){push();}
            public void onStopTrackingTouch(SeekBar s){}
        });
        block.addView(slider,new LinearLayout.LayoutParams(0,dp(35),1));
        parent.addView(block);
    }
    private void openTab(int tab) {
        inspectorTab=tab;
        // Leave keyboard focus naturally before rebuilding inspector.
        showStudio();
    }

    // The lab is an editor surface inside MainActivity: it shares source,
    // undo/redo, caption overlay, watermark and the existing export pipeline.
    private GlitchFx.Layer currentGlitchLayer(){
        if(state.glitch.layers.isEmpty())state.glitch.layers.add(new GlitchFx.Layer());
        state.glitch.active=Math.max(0,Math.min(state.glitch.active,state.glitch.layers.size()-1));
        return state.glitch.layers.get(state.glitch.active);
    }
    private void glitchChange(Runnable r){
        push();r.run();requestGlitchRender();showGlitchLab();
    }
    private void glitchSeek(LinearLayout parent,String title,int val,java.util.function.IntConsumer apply){
        LinearLayout r=row();r.setPadding(dp(2),0,dp(2),0);
        TextView label=text(title,12,0xFFD3D9EE,true);
        r.addView(label,new LinearLayout.LayoutParams(dp(94),dp(42)));
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(val);
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(LAVENDER));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(CYAN));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int n,boolean fromUser) {
                if(fromUser)apply.accept(n);
            }
            public void onStartTrackingTouch(SeekBar b){push();}
            public void onStopTrackingTouch(SeekBar b){showGlitchLab();}
        });
        r.addView(slider,new LinearLayout.LayoutParams(0,dp(43),1));
        TextView value=text(val+"%",11,CYAN,true);
        value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        r.addView(value,new LinearLayout.LayoutParams(dp(44),dp(42)));
        parent.addView(r);
    }
    private void glitchLabel(LinearLayout p,String title){
        TextView label=kicker(title);label.setTextColor(LAVENDER);
        label.setPadding(dp(2),dp(4),0,dp(6));p.addView(label);
    }
    private void glitchTabs(LinearLayout dest){
        String[] names={"Effects","Presets","Controls","Masks","Brush","Layers","Export"};
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=row();strip.setPadding(dp(2),dp(3),dp(2),dp(5));
        for(int i=0;i<names.length;i++){
            final int index=i;
            Button b=button(names[i],()->{glitchTab=index;showGlitchLab();});
            b.setTextSize(12);
            b.setTextColor(i==glitchTab?BG:WHITE);
            b.setBackground(i==glitchTab?gradient(0xFF99E8F4,0xFFC4A2FA,13):box(0xFF29304D,13));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(42));
            lp.setMargins(dp(3),0,dp(3),0);strip.addView(b,lp);
        }
        scroll.addView(strip);dest.addView(scroll);
    }

    private Bitmap effectThumbnailSource;
    private int effectThumbSourceId=0, effectThumbRotation=-1;
    private void effectThumbnail(ImageView target,int type){
        Bitmap current=rotated();
        int id=System.identityHashCode(source);
        if(effectThumbnailSource==null||effectThumbSourceId!=id||effectThumbRotation!=state.rotation){
            effectThumbnailSource=Bitmap.createScaledBitmap(current,145,135,true);
            effectThumbSourceId=id;effectThumbRotation=state.rotation;
            glitchThumbnails.clear();
        }
        final String key=id+":"+state.rotation+":"+type+":"+
            (type==30 && !state.glitch.layers.isEmpty()?currentGlitchLayer().runeColor:0);
        Bitmap thumb=glitchThumbnails.get(key);
        if(thumb!=null && !thumb.isRecycled()){target.setImageBitmap(thumb);return;}
        target.setImageBitmap(effectThumbnailSource);
        final Bitmap small=effectThumbnailSource;
        int tint=type==30 && !state.glitch.layers.isEmpty()
            ?currentGlitchLayer().runeColor:0xFF6DFFCB;
        thumbnailWorkers.submit(()->{
            Bitmap result=null;
            try{
                GlitchFx.Config c=new GlitchFx.Config();
                GlitchFx.Layer layer=new GlitchFx.Layer();
                layer.type=type;layer.intensity=94;layer.opacity=100;layer.scale=62;
                layer.threshold=28;layer.seed=773L+type*9813;layer.runeColor=tint;
                c.layers.add(layer);
                result=GlitchFx.render(small,c);
            }catch(Exception e){android.util.Log.w("BlendCraft","Thumbnail "+type,e);}
            final Bitmap rendered=result;
            mainHandler.post(()->{
                if(rendered==null)return;
                if(effectThumbSourceId!=id)return;
                glitchThumbnails.put(key,rendered);
                if(target.getWindowToken()!=null)target.setImageBitmap(rendered);
            });
        });
    }
    private void glitchEffects(LinearLayout panel){
        glitchLabel(panel,"REAL EFFECTS · TAP AN IMAGE TO APPLY");
        for(int start=0;start<GlitchFx.EFFECTS.length;start+=2){
            LinearLayout tiles=row();
            for(int i=start;i<Math.min(start+2,GlitchFx.EFFECTS.length);i++){
                final int chosen=i;
                boolean selected=!state.glitch.layers.isEmpty()
                    &&state.glitch.layers.get(state.glitch.active).type==i;
                FrameLayout tile=new FrameLayout(this);
                tile.setBackground(outlined(0xFF242945,selected?CYAN:0xFF424A6A,17));
                tile.setClipToOutline(true);
                ImageView img=new ImageView(this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                tile.addView(img,new FrameLayout.LayoutParams(-1,-1));
                effectThumbnail(img,i);
                TextView name=text(GlitchFx.EFFECTS[i],12,WHITE,true);
                name.setGravity(Gravity.CENTER);
                name.setPadding(dp(4),dp(7),dp(4),dp(7));
                name.setBackground(gradient(0xDA1C1235,0xE8222943,0));
                FrameLayout.LayoutParams labelPos=new FrameLayout.LayoutParams(-1,dp(35),Gravity.BOTTOM);
                tile.addView(name,labelPos);
                if(selected){
                    TextView selectedMark=text("●",19,CYAN,true);
                    FrameLayout.LayoutParams marker=new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP|Gravity.RIGHT);
                    selectedMark.setGravity(Gravity.CENTER);tile.addView(selectedMark,marker);
                }
                tile.setOnClickListener(view->glitchChange(()->currentGlitchLayer().type=chosen));
                LinearLayout.LayoutParams bounds=new LinearLayout.LayoutParams(0,dp(130),1);
                bounds.setMargins(dp(3),dp(3),dp(3),dp(3));
                tiles.addView(tile,bounds);
            }
            if(start+1>=GlitchFx.EFFECTS.length){
                View filler=new View(this);
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(130),1);
                p.setMargins(dp(3),dp(3),dp(3),dp(3));tiles.addView(filler,p);
            }
            panel.addView(tiles);
        }
        gap(panel,9);
        panel.addView(text("Thumbnails are generated from your imported image using the real effect algorithms. Pixel sorting, XOR, Zalgo, compression artifacts, and Shifting Runes Curse are all editable layers.",11,MUTED,false));
    }
    private void chooseRuneColor(){
        GlitchFx.Layer layer=currentGlitchLayer();
        LinearLayout layout=column();
        layout.setPadding(dp(15),dp(8),dp(15),dp(12));
        TextView instruction=text("Drag around the wheel to pick your rune glow.",14,Color.DKGRAY,false);
        instruction.setGravity(Gravity.CENTER);layout.addView(instruction);
        gap(layout,10);
        TextView swatch=text("✦  Selected rune color",16,Color.BLACK,true);
        swatch.setGravity(Gravity.CENTER);
        RuneColorWheel wheel=new RuneColorWheel(this,layer.runeColor,
            col->{swatch.setBackground(box(col,16));swatch.setTextColor(0xFF111225);});
        layout.addView(wheel,new LinearLayout.LayoutParams(-1,dp(255)));
        gap(layout,7);
        swatch.setBackground(box(layer.runeColor,16));
        layout.addView(swatch,new LinearLayout.LayoutParams(-1,dp(48)));
        new AlertDialog.Builder(this).setTitle("Shifting Runes · Color Wheel").setView(layout)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Use this color",(dialog,which)->glitchChange(()->currentGlitchLayer().runeColor=wheel.getColor()))
            .show();
    }
    private void glitchPresets(LinearLayout panel){
        glitchLabel(panel,"8 CURATED CORRUPTIONS");
        for(int st=0;st<GlitchFx.PRESETS.length;st+=2){
            LinearLayout line=row();
            for(int i=st;i<Math.min(st+2,GlitchFx.PRESETS.length);i++){
                final int ix=i;
                addFlexButton(line,GlitchFx.PRESETS[i],
                    ()->glitchChange(()->state.glitch=GlitchFx.preset(ix)),false);
            }
            panel.addView(line);gap(panel,dp(2));
        }
        gap(panel,10);
        Button savePreset=actionButton("✦ SAVE MY PRESET",()->saveCustomGlitchPreset(),true);
        panel.addView(savePreset,new LinearLayout.LayoutParams(-1,dp(44)));
        gap(panel,7);
        String json=getSharedPreferences("glitch_lab_presets",MODE_PRIVATE).getString("user_config","");
        if(!json.isEmpty()){
            Button restore=actionButton("↶ LOAD MY SAVED PRESET",()->{
                try {
                    GlitchFx.Config restored=decodeGlitch(json);
                    glitchChange(()->state.glitch=restored);
                }catch(Exception e){toast("Saved preset could not be read");}
            },false);
            panel.addView(restore,new LinearLayout.LayoutParams(-1,dp(44)));
        }
    }
    private void glitchControls(LinearLayout panel){
        GlitchFx.Layer layer=currentGlitchLayer();
        glitchLabel(panel,"LIVE PARAMETER CONTROLS");
        glitchSeek(panel,"Intensity",layer.intensity,v->layer.intensity=v);
        glitchSeek(panel,"Scale",layer.scale,v->layer.scale=v);
        glitchSeek(panel,"Threshold",layer.threshold,v->layer.threshold=v);
        glitchSeek(panel,"Opacity",layer.opacity,v->layer.opacity=v);
        LinearLayout line=row();
        addFlexButton(line,"Direction: "+(layer.direction%2==0?"↔":"↕"),()->glitchChange(()->currentGlitchLayer().direction^=1),false);
        addFlexButton(line,"Seed "+(state.glitch.seedLocked?"🔒":"🔓"),()->{
            state.glitch.seedLocked=!state.glitch.seedLocked;showGlitchLab();
        },false);
        panel.addView(line);gap(panel,7);
        if(layer.type==0){
            String[] sortBy={"Brightness","Saturation","Red channel","Green channel","Blue channel"};
            Button sortButton=actionButton("Sort by: "+sortBy[Math.floorMod(layer.sortKey,5)],()->
                glitchChange(()->currentGlitchLayer().sortKey=(currentGlitchLayer().sortKey+1)%5),false);
            panel.addView(sortButton,new LinearLayout.LayoutParams(-1,dp(42)));
            gap(panel,7);
        }

        if(layer.type==30){
            glitchLabel(panel,"SHIFTING RUNES CURSE");
            Button wheel=actionButton("✦ RUNE COLOR WHEEL",()->chooseRuneColor(),true);
            panel.addView(wheel,new LinearLayout.LayoutParams(-1,dp(46)));
            gap(panel,5);
            glitchSeek(panel,"Shift speed",layer.runeSpeed,v->layer.runeSpeed=v);
            LinearLayout runeOptions=row();
            addFlexButton(runeOptions,runeLive?"⏸ PAUSE SHIFT":"▶ LIVE SHIFT",
                ()->{runeLive=!runeLive;armRuneAnimation(layer.runeSpeed);showGlitchLab();},false);
            addFlexButton(runeOptions,"↻ NEXT RUNES",()->glitchChange(()->currentGlitchLayer().phase++),false);
            panel.addView(runeOptions);
            gap(panel,8);
            panel.addView(text("The selected glow is applied to procedurally drawn runic glyphs. Live Shift cycles their positions; exports capture the current frame.",11,MUTED,false));
        }
        LinearLayout seeds=row();
        addFlexButton(seeds,"Seed: "+layer.seed,()->{
            EditText field=new EditText(this);field.setSingleLine(true);
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
            field.setText(Long.toString(currentGlitchLayer().seed));field.selectAll();
            new AlertDialog.Builder(this).setTitle("Deterministic random seed").setView(field)
                .setNegativeButton("Cancel",null).setPositiveButton("Set",(d,w)->{
                    try{
                        long v=Long.parseLong(field.getText().toString());
                        glitchChange(()->currentGlitchLayer().seed=v);
                    }catch(Exception e){toast("Please enter a whole number");}
                }).show();
        },false);
        addFlexButton(seeds,"⚡ RANDOMIZE",()->randomizeGlitches(),true);
        panel.addView(seeds);
        gap(panel,5);
        panel.addView(text("Locked seeds reproduce the same image with the same settings. Unlock to generate a new seed when randomizing.",11,MUTED,false));
    }
    private void randomizeGlitches(){
        glitchChange(()->{
            java.util.Random rnd=new java.util.Random(System.nanoTime());
            if(!state.glitch.seedLocked){
                for(GlitchFx.Layer l:state.glitch.layers)l.seed=rnd.nextLong();
            }
            for(GlitchFx.Layer l:state.glitch.layers){
                l.type=rnd.nextInt(GlitchFx.EFFECTS.length);
                l.intensity=35+rnd.nextInt(66);
                l.scale=15+rnd.nextInt(75);
                l.threshold=rnd.nextInt(70);
                l.direction=rnd.nextInt(2);
            }
        });
    }
    private void glitchMasks(LinearLayout panel){
        glitchLabel(panel,"MASK WHERE THE EFFECT IS VISIBLE");
        for(int i=0;i<GlitchFx.MASKS.length;i++){
            final int ix=i;
            Button b=actionButton((state.glitch.mask==i?"● ":"○ ")+GlitchFx.MASKS[i],
                ()->glitchChange(()->state.glitch.mask=ix),state.glitch.mask==i);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(42));
            p.setMargins(0,dp(2),0,dp(3));panel.addView(b,p);
        }
        gap(panel,8);
        panel.addView(text("Center and background masks are geometric ellipse selections, NOT AI subject segmentation. Brush masks let you select image areas directly.",11,MUTED,false));
    }
    private void glitchBrush(LinearLayout panel){
        glitchLabel(panel,"PAINT THE GLITCH INTO SELECTED REGIONS");
        LinearLayout actions=row();
        addFlexButton(actions,glitchBrushMode?"✓ STOP PAINTING":"✎ START PAINTING",()->{
            if(state.glitch.mask!=3){push();state.glitch.mask=3;}
            glitchBrushMode=!glitchBrushMode;requestGlitchRender();showGlitchLab();
        },true);
        addFlexButton(actions,"ERASE BRUSH MASK",()->glitchChange(()->state.glitch.brush.clear()),false);
        panel.addView(actions);
        glitchSeek(panel,"Brush radius",state.glitch.brushRadius,v->state.glitch.brushRadius=Math.max(2,v));
        panel.addView(text("After starting, drag a finger over the image. Only brushed areas receive glitch effects. Existing photo annotations remain separate.",11,MUTED,false));
    }
    private void glitchLayers(LinearLayout panel){
        glitchLabel(panel,"NON-DESTRUCTIVE EFFECT STACK  •  MAX 6");
        for(int i=0;i<state.glitch.layers.size();i++){
            final int ix=i;
            GlitchFx.Layer layer=state.glitch.layers.get(i);
            Button b=actionButton((state.glitch.active==i?"● ":"○ ")+"L"+(i+1)+"  "+GlitchFx.EFFECTS[layer.type],
                ()->{state.glitch.active=ix;showGlitchLab();},i==state.glitch.active);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(39));
            lp.setMargins(0,dp(2),0,dp(3));panel.addView(b,lp);
        }
        LinearLayout btns=row();
        addFlexButton(btns,"＋ ADD LAYER",()->{
            if(state.glitch.layers.size()>=GlitchFx.MAX_LAYERS){toast("Maximum six effects");return;}
            glitchChange(()->{
                GlitchFx.Layer l=new GlitchFx.Layer();
                l.type=(state.glitch.layers.size()+1)%GlitchFx.EFFECTS.length;
                l.seed=2026+state.glitch.layers.size()*341;
                state.glitch.layers.add(l);
                state.glitch.active=state.glitch.layers.size()-1;
            });
        },true);
        addFlexButton(btns,"− DELETE LAYER",()->{
            if(state.glitch.layers.isEmpty())return;
            glitchChange(()->{
                state.glitch.layers.remove(state.glitch.active);
                state.glitch.active=Math.max(0,Math.min(state.glitch.active,state.glitch.layers.size()-1));
            });
        },false);
        panel.addView(btns);
        gap(panel,10);
        if(state.glitch.layers.size()>0){
            GlitchFx.Layer layer=currentGlitchLayer();
            LinearLayout blend=row();
            addFlexButton(blend,"Blend: "+GlitchFx.BLENDS[layer.blend],()->glitchChange(()->{
                currentGlitchLayer().blend=(currentGlitchLayer().blend+1)%GlitchFx.BLENDS.length;
            }),false);
            addFlexButton(blend,"CLEAR ALL",()->glitchChange(()->state.glitch.layers.clear()),false);
            panel.addView(blend);
            glitchSeek(panel,"Opacity",layer.opacity,v->layer.opacity=v);
        }
    }
    private void glitchExportPanel(LinearLayout panel){
        glitchLabel(panel,"RENDER FULL-SIZE IMAGE WITH GLITCH");
        LinearLayout row1=row();
        addFlexButton(row1,"↓ PNG",()->save(false,"png"),true);
        addFlexButton(row1,"↓ JPG",()->save(false,"jpg"),false);
        addFlexButton(row1,"↓ WebP",()->save(false,"webp"),false);
        panel.addView(row1);gap(panel,8);
        LinearLayout row2=row();
        addFlexButton(row2,"↗ SHARE PNG",()->save(true,"png"),false);
        addFlexButton(row2,glitchCompare?"VIEW EDITED":"COMPARE ORIGINAL",()->{
            glitchCompare=!glitchCompare;if(preview!=null)preview.invalidate();showGlitchLab();
        },false);
        panel.addView(row2);
        gap(panel,10);
        panel.addView(text("PNG preserves existing transparency. JPG is lossy and flattens transparency. WebP lossless requires Android 11+. GIF/MP4 and AI subject cutouts are not enabled in this Android release.",11,MUTED,false));
    }
    private String encodeGlitch(){
        try{
            JSONObject root=new JSONObject();
            root.put("mask",state.glitch.mask);root.put("brushRadius",state.glitch.brushRadius);
            root.put("seedLocked",state.glitch.seedLocked);
            JSONArray a=new JSONArray();
            for(GlitchFx.Layer layer:state.glitch.layers){
                JSONObject o=new JSONObject();
                o.put("type",layer.type);o.put("intensity",layer.intensity);
                o.put("scale",layer.scale);o.put("threshold",layer.threshold);
                o.put("direction",layer.direction);o.put("sortKey",layer.sortKey);o.put("opacity",layer.opacity);
                o.put("blend",layer.blend);o.put("seed",layer.seed);
                o.put("runeColor",layer.runeColor);o.put("phase",layer.phase);o.put("runeSpeed",layer.runeSpeed);
                a.put(o);
            }
            root.put("layers",a);
            JSONArray pts=new JSONArray();
            for(GlitchFx.Point p:state.glitch.brush) {
                JSONArray xy=new JSONArray();xy.put(p.x);xy.put(p.y);pts.put(xy);
            }
            root.put("brush",pts);
            return root.toString();
        }catch(Exception e){return "";}
    }
    private GlitchFx.Config decodeGlitch(String json)throws Exception{
        JSONObject root=new JSONObject(json);
        GlitchFx.Config c=new GlitchFx.Config();
        c.mask=root.optInt("mask",0);
        c.brushRadius=root.optInt("brushRadius",13);
        c.seedLocked=root.optBoolean("seedLocked",true);
        JSONArray layers=root.optJSONArray("layers");
        if(layers!=null)for(int i=0;i<Math.min(GlitchFx.MAX_LAYERS,layers.length());i++){
            JSONObject o=layers.getJSONObject(i);
            GlitchFx.Layer l=new GlitchFx.Layer();
            l.type=Math.max(0,Math.min(GlitchFx.EFFECTS.length-1,o.optInt("type",0)));
            l.intensity=o.optInt("intensity",65);l.scale=o.optInt("scale",40);
            l.threshold=o.optInt("threshold",95);l.direction=o.optInt("direction",0);l.sortKey=o.optInt("sortKey",0);
            l.opacity=o.optInt("opacity",100);l.blend=o.optInt("blend",0);
            l.seed=o.optLong("seed",2026);
            l.runeColor=o.optInt("runeColor",0xFF6DFFCB);
            l.phase=o.optInt("phase",0);l.runeSpeed=o.optInt("runeSpeed",55);
            c.layers.add(l);
        }
        JSONArray pts=root.optJSONArray("brush");
        if(pts!=null)for(int i=0;i<Math.min(10000,pts.length());i++){
            JSONArray p=pts.getJSONArray(i);
            c.brush.add(new GlitchFx.Point((float)p.getDouble(0),(float)p.getDouble(1)));
        }
        return c;
    }
    private void saveCustomGlitchPreset(){
        String json=encodeGlitch();
        if(json.isEmpty()){toast("Could not save preset");return;}
        getSharedPreferences("glitch_lab_presets",MODE_PRIVATE).edit().putString("user_config",json).apply();
        toast("Glitch preset saved on this device");
        showGlitchLab();
    }
    private void showGlitchLab(){
        if(source==null){showHome();return;}
        inGlitchLab=true;
        requestGlitchRender();
        armRuneAnimation(50);
        LinearLayout root=column();root.setBackgroundColor(0xFF0A0B16);

        LinearLayout titlebar=row();titlebar.setPadding(dp(12),dp(7),dp(12),dp(5));
        Button back=actionButton("‹",()->{inGlitchLab=false;glitchBrushMode=false;showStudio();},false);
        titlebar.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout textCol=column();textCol.setPadding(dp(12),0,0,0);
        textCol.addView(kicker("BLENDCRAFT  /  EXPERIMENTAL STUDIO"));
        textCol.addView(text("⚡ GLITCH LAB",23,0xFFD6B2FF,true));
        titlebar.addView(textCol,new LinearLayout.LayoutParams(0,-2,1));
        Button save=actionButton("SAVE ↗",()->save(false,"png"),true);
        titlebar.addView(save,new LinearLayout.LayoutParams(dp(94),dp(46)));
        root.addView(titlebar);

        FrameLayout canvas=new FrameLayout(this);canvas.setBackgroundColor(0xFF101326);
        preview=new Preview();
        canvas.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        root.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout status=row();status.setPadding(dp(13),dp(5),dp(13),dp(5));
        TextView info=text(glitchWorking?"◌  RENDERING EFFECTS…":glitchCompare?"◯  ORIGINAL COMPARISON":"●  GLITCH PREVIEW",10,CYAN,true);
        status.addView(info,new LinearLayout.LayoutParams(0,dp(25),1));
        status.addView(text(state.glitch.layers.size()+" ACTIVE LAYER(S)",10,MUTED,true));
        root.addView(status);

        LinearLayout surface=column();surface.setPadding(dp(10),dp(8),dp(10),dp(5));
        surface.setBackground(outlined(0xFF161A31,0xFF444264,21));
        glitchTabs(surface);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout panel=column();panel.setPadding(dp(4),dp(3),dp(4),dp(3));
        scroll.addView(panel);
        if(glitchTab==0)glitchEffects(panel);
        else if(glitchTab==1)glitchPresets(panel);
        else if(glitchTab==2)glitchControls(panel);
        else if(glitchTab==3)glitchMasks(panel);
        else if(glitchTab==4)glitchBrush(panel);
        else if(glitchTab==5)glitchLayers(panel);
        else glitchExportPanel(panel);
        surface.addView(scroll,new LinearLayout.LayoutParams(-1,dp(glitchTab==0?310:233)));
        root.addView(surface);

        LinearLayout quick=row();quick.setPadding(dp(7),dp(7),dp(7),dp(8));
        addFlexButton(quick,"↶ UNDO",()->undoAction(),false);
        addFlexButton(quick,"↷ REDO",()->redoAction(),false);
        addFlexButton(quick,glitchCompare?"EDITED":"BEFORE",()->{
            glitchCompare=!glitchCompare;showGlitchLab();
        },false);
        addFlexButton(quick,"↓ EXPORT",()->save(false,"png"),true);
        root.addView(quick);
        applySafeArea(root);setContentView(root);
    }

    private void showStudio() {
        inGlitchLab=false;glitchBrushMode=false;glitchCompare=false;
        requestGlitchRender();
        if(source==null){showHome();return;}
        LinearLayout root=column();root.setBackgroundColor(0xFF0B0E20);

        LinearLayout head=row();head.setPadding(dp(13),dp(8),dp(13),dp(5));
        Button back=actionButton("‹",()->showHome(),false);back.setTextSize(24);
        head.addView(back,new LinearLayout.LayoutParams(dp(45),dp(46)));
        LinearLayout heading=column(); heading.setPadding(dp(12),0,0,0);
        heading.addView(kicker("BLENDCRAFT  /  STUDIO"));
        gap(heading,3);
        heading.addView(text(mode.equals("Meme")?"Meme Maker":mode.equals("Screenshot")?"Screen Studio":"Photo Lab",22,WHITE,true));
        head.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button save=actionButton("SAVE  ↗",()->save(false),true);
        head.addView(save,new LinearLayout.LayoutParams(dp(94),dp(46)));
        root.addView(head);
        LinearLayout switches=row();switches.setPadding(dp(12),dp(7),dp(12),dp(5));
        for(String m:new String[]{"Photo","Screenshot","Meme"}){
            final boolean selected=mode.equals(m);
            Button b=button(selected?"● "+m:m,()->setNewMode(m));
            b.setTextSize(12);b.setTextColor(selected?BG:MUTED);
            b.setBackground(selected?gradient(0xFFA3EFEF,0xFF93B9FC,14):outlined(0xFF1B2039,0xFF323C5A,14));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(37),1);
            lp.setMargins(dp(3),0,dp(3),0);
            switches.addView(b,lp);
        }
        root.addView(switches);
        Button lab=actionButton("✦ GLITCH LAB  ·  "+GlitchFx.EFFECTS.length+" EFFECTS  →",()->showGlitchLab(),false);
        lab.setTextColor(0xFFBB9DFF);
        LinearLayout.LayoutParams labParams=new LinearLayout.LayoutParams(-1,dp(39));
        labParams.setMargins(dp(14),dp(1),dp(14),dp(5));
        root.addView(lab,labParams);

        FrameLayout canvasHolder=new FrameLayout(this);
        canvasHolder.setBackgroundColor(0xFF101429);
        preview=new Preview();
        canvasHolder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        root.addView(canvasHolder,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout canvasStatus=row();canvasStatus.setPadding(dp(16),dp(7),dp(16),dp(7));
        TextView pre=text("✦   LIVE PREVIEW",10,CYAN,true);pre.setLetterSpacing(.12f);
        canvasStatus.addView(pre,new LinearLayout.LayoutParams(0,-2,1));
        canvasStatus.addView(text(drawing?"● DRAWING ENABLED":"FREE  •  BRAND MARK ON",10,MUTED,false));
        root.addView(canvasStatus);

        LinearLayout inspect=column();inspect.setPadding(dp(12),dp(10),dp(12),dp(9));
        inspect.setBackground(outlined(0xFF181E36,0xFF353B5F,25));
        LinearLayout tabs=row();
        String[] names={"✎  TEXT","◐  COLOR","▧  FRAME","✦  DRAW"};
        for(int i=0;i<names.length;i++){
            final int index=i;
            Button tab=button(names[i],()->openTab(index));
            tab.setTextSize(11);tab.setTextColor(i==inspectorTab?BG:0xFFB0B9DA);
            tab.setPadding(0,0,0,0);
            tab.setBackground(i==inspectorTab?gradient(0xFF89F0E2,0xFF9CC6FC,12):box(0xFF222943,12));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(39),1);
            p.setMargins(dp(2),0,dp(2),0);tabs.addView(tab,p);
        }
        inspect.addView(tabs);gap(inspect,9);
        if(inspectorTab==0){
            LinearLayout labels=row();
            addCaptionField(labels,"TOP CAPTION",state.top,"Type top text",v->{state.top=v;});
            addCaptionField(labels,"BOTTOM CAPTION",state.bottom,"Type bottom text",v->{state.bottom=v;});
            inspect.addView(labels);gap(inspect,9);
            LinearLayout extras=row();
            addFlexButton(extras,"＋ Overlay text",()->textPrompt("Overlay text or emoji",state.extra,v->{state.extra=v;}),false);
            addFlexButton(extras,"🔒 Branding",()->watermarkDialog(),false);
            addFlexButton(extras,"Aa Color",()->{push();state.textColor=state.textColor==Color.WHITE?0xFFFFD987:state.textColor==0xFFFFD987?CYAN:Color.WHITE;showStudio();},false);
            inspect.addView(extras);
        }else if(inspectorTab==1){
            addTune(inspect,"Brightness",state.brightness+100,v->{state.brightness=v-100;});
            addTune(inspect,"Contrast",state.contrast,v->{state.contrast=v;});
            addTune(inspect,"Saturation",state.saturation,v->{state.saturation=v;});
            LinearLayout reset=row();
            addFlexButton(reset,"Reset color",()->{push();state.brightness=0;state.contrast=100;state.saturation=100;showStudio();},false);
            addFlexButton(reset,"↶ Undo",()->undoAction(),false);
            inspect.addView(reset);
        }else if(inspectorTab==2){
            inspect.addView(kicker("CROP RATIO"));
            gap(inspect,6);
            LinearLayout crops=row();
            for(int j=0;j<RATIOS.length;j++){
                final int index=j;
                Button crop=button(RATIOS[j],()->{push();state.ratio=index;showStudio();});
                crop.setTextSize(11);crop.setPadding(0,0,0,0);
                crop.setTextColor(state.ratio==j?BG:WHITE);
                crop.setBackground(state.ratio==j?box(CYAN,11):outlined(0xFF252C48,0xFF495371,11));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
                lp.setMargins(dp(2),0,dp(2),0);crops.addView(crop,lp);
            }
            inspect.addView(crops);gap(inspect,10);
            LinearLayout transforms=row();
            addFlexButton(transforms,"↻ Rotate 90°",()->{push();state.rotation=(state.rotation+90)%360;rotatedCache=null;showStudio();},false);
            addFlexButton(transforms,"✦ Change frame",()->{push();state.frame=(state.frame+1)%4;mode="Screenshot";showStudio();},false);
            inspect.addView(transforms);
        }else{
            inspect.addView(kicker("ANNOTATION BRUSH"));
            gap(inspect,7);
            LinearLayout brush=row();
            for(int i=0;i<INKS.length;i++){
                final int ix=i;
                TextView dot=text(inkIndex==i?"✓":"●",21,inkIndex==i?BG:INKS[i],true);
                dot.setGravity(Gravity.CENTER);
                dot.setBackground(outlined(inkIndex==i?INKS[i]:0xFF242B45,inkIndex==i?CYAN:0xFF454C67,13));
                dot.setOnClickListener(v->{inkIndex=ix;showStudio();});
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);
                lp.setMargins(dp(3),0,dp(3),0);brush.addView(dot,lp);
            }
            inspect.addView(brush);gap(inspect,10);
            LinearLayout drawControls=row();
            addFlexButton(drawControls,drawing?"✓ Stop drawing":"✎ Start drawing",()->{drawing=!drawing;showStudio();},true);
            addFlexButton(drawControls,"Clear marks",()->{push();state.strokes.clear();showStudio();},false);
            inspect.addView(drawControls);
        }
        root.addView(inspect);

        LinearLayout controls=row();controls.setPadding(dp(10),dp(9),dp(10),dp(1));
        addFlexButton(controls,"↶ Undo",()->undoAction(),false);
        addFlexButton(controls,"↷ Redo",()->redoAction(),false);
        addFlexButton(controls,"＋ New image",()->pick(mode),false);
        root.addView(controls);
        LinearLayout actions=row();actions.setPadding(dp(10),dp(6),dp(10),dp(8));
        Button export=actionButton("↓  EXPORT PNG",()->save(false),true);
        actions.addView(export,new LinearLayout.LayoutParams(0,dp(51),1));
        Button share=actionButton("↗  SHARE",()->save(true),false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(51),1);
        sp.setMargins(dp(8),0,0,0);actions.addView(share,sp);
        root.addView(actions);
        applySafeArea(root);
        setContentView(root);
    }
    private Bitmap rotated() {
        if(state.rotation==0)return source;
        if(rotatedCache!=null && cacheAngle==state.rotation)return rotatedCache;
        Matrix m=new Matrix();m.postRotate(state.rotation);
        // Workers may still read an earlier rotated bitmap. Do not recycle it here.
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

    private void resetGlitchCache(){
        glitchCache=null;glitchCacheKey="";glitchRequestedKey="";glitchWorking=false;
        for(Bitmap b:glitchThumbnails.values())if(b!=null&&!b.isRecycled())b.recycle();
        glitchThumbnails.clear();
    }
    private String glitchSignature(){
        if(source==null)return "";
        StringBuilder key=new StringBuilder();
        key.append(System.identityHashCode(source)).append(':').append(state.rotation)
            .append(':').append(state.glitch.mask).append(':').append(state.glitch.brushRadius);
        for(GlitchFx.Layer layer:state.glitch.layers){
            key.append('|').append(layer.type).append(',').append(layer.intensity)
                .append(',').append(layer.scale).append(',').append(layer.threshold)
                .append(',').append(layer.direction).append(',').append(layer.sortKey).append(',').append(layer.opacity)
                .append(',').append(layer.seed).append(',').append(layer.blend)
                .append(',').append(layer.runeColor).append(',').append(layer.phase)
                .append(',').append(layer.runeSpeed);
        }
        for(GlitchFx.Point pt:state.glitch.brush)
            key.append(':').append((int)(pt.x*1000)).append(',').append((int)(pt.y*1000));
        return key.toString();
    }
    private void requestGlitchRender(){
        if(source==null || !state.glitch.enabled()){glitchWorking=false;return;}
        final String key=glitchSignature();
        if(key.equals(glitchCacheKey)||(key.equals(glitchRequestedKey)&&glitchWorking))return;
        glitchRequestedKey=key;glitchWorking=true;
        final Bitmap input=rotated();
        final GlitchFx.Config snapshot=state.glitch.copy();
        glitchWorkers.submit(()->{
            Bitmap result=null;
            try{
                // Preview uses an explicitly smaller derivative; export always renders the
                // full original. This bounds preview latency and pixel array allocations.
                int max=Math.max(input.getWidth(),input.getHeight());
                Bitmap previewSource=max>1000
                    ?Bitmap.createScaledBitmap(input,Math.max(1,Math.round(input.getWidth()*1000f/max)),
                        Math.max(1,Math.round(input.getHeight()*1000f/max)),true)
                    :input;
                result=GlitchFx.render(previewSource,snapshot);
            }
            catch(Exception e){android.util.Log.e("BlendCraft","Glitch render failed",e);}
            final Bitmap rendered=result;
            mainHandler.post(()->{
                if(!key.equals(glitchRequestedKey))return;
                glitchWorking=false;
                if(rendered!=null){glitchCache=rendered;glitchCacheKey=key;}
                if(preview!=null)preview.invalidate();
            });
        });
    }
    private Bitmap glitchBitmapForRender(){
        Bitmap input=rotated();
        if(!state.glitch.enabled() || (glitchCompare && !glitchExporting))return input;
        if(glitchSignature().equals(glitchCacheKey) && glitchCache!=null)return glitchCache;
        if(glitchExporting)return GlitchFx.render(input,state.glitch.copy());
        return input;
    }
    private boolean glitchExporting=false;

    private int outputWidth(){
        if(source==null)return 1440;
        Bitmap b=rotated();
        if("Screenshot".equals(mode))return Math.max(1080,Math.min(b.getWidth(),4000));
        return Math.max(1,crop(b,outputRatio()).width());
    }
    private int outputHeight(){
        if(source==null)return 1440;
        if("Screenshot".equals(mode))return outputWidth();
        return Math.max(1,crop(rotated(),outputRatio()).height());
    }
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
        if("Screenshot".equals(mode))c.drawColor(BG);
        else c.drawColor(Color.TRANSPARENT,android.graphics.PorterDuff.Mode.CLEAR);
        Bitmap bm=glitchBitmapForRender();
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
        // Render free-version branding LAST in the central renderer:
        // previews, file exports, and shares all use this exact canvas.
        drawFreeBrandMark(c,w,h);
    }
    private void drawFreeBrandMark(Canvas canvas,int w,int h) {
        canvas.save();
        Paint mark=new Paint(Paint.ANTI_ALIAS_FLAG);
        String brand="BLENDCRAFT";
        float font=Math.max(15f,Math.min(w,h)*.036f);
        mark.setTypeface(android.graphics.Typeface.create("sans-serif-black",1));
        mark.setTextSize(font);
        float gap=font*.36f;
        float dot=font*.27f;
        float horizontal=font*.60f;
        float vertical=font*.50f;
        float textWidth=mark.measureText(brand);
        float pillW=horizontal*2+dot*2+gap+textWidth;
        float pillH=font*1.95f;
        float margin=Math.max(12f,Math.min(w,h)*.018f);
        float right=w-margin;
        float bottom=h-margin;
        float left=right-pillW;
        float top=bottom-pillH;

        // High contrast background, visible against both dark and bright photographs.
        mark.setColor(0xE6151B30);
        canvas.drawRoundRect(left,top,right,bottom,pillH*.35f,pillH*.35f,mark);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(Math.max(1.5f,font*.05f));
        mark.setColor(0xB6E1F7F3);
        canvas.drawRoundRect(left,top,right,bottom,pillH*.35f,pillH*.35f,mark);
        mark.setStyle(Paint.Style.FILL);
        float baseline=top+pillH*.5f-(mark.ascent()+mark.descent())*.5f;
        mark.setColor(0xFF85EBE8);
        canvas.drawCircle(left+horizontal+dot,top+pillH/2,dot,mark);
        mark.setColor(Color.WHITE);
        canvas.drawText(brand,left+horizontal+dot*2+gap,baseline,mark);
        canvas.restore();
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
        Preview(){super(MainActivity.this);/* Allow Android's GPU Canvas compositor to scale the result. */}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w=outputWidth(),h=outputHeight();
            Paint background=new Paint(3);
            background.setShader(new LinearGradient(0,0,getWidth(),getHeight(),
                new int[]{0xFF12162D,0xFF1C1D39,0xFF132D3E},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,getWidth(),getHeight(),background);
            background.setShader(null);
            background.setColor(0x164F70AC);
            for(int x=dp(12);x<getWidth();x+=dp(23)){
                for(int y=dp(8);y<getHeight();y+=dp(23))
                    canvas.drawCircle(x,y,dp(.65f),background);
            }
            float scale=Math.min((getWidth()-dp(32))/(float)w,(getHeight()-dp(30))/(float)h);
            if(scale<=0)return;
            scaledW=w*scale;scaledH=h*scale;
            left=(getWidth()-scaledW)/2;top=(getHeight()-scaledH)/2;
            Paint shadow=new Paint(3);shadow.setColor(0xAA000000);
            shadow.setShadowLayer(dp(18),0,dp(7),0xCC000008);
            canvas.drawRoundRect(left,top,left+scaledW,top+scaledH,dp(4),dp(4),shadow);
            canvas.save();canvas.translate(left,top);canvas.scale(scale,scale);
            artwork(canvas,w,h);canvas.restore();
            Paint border=new Paint(3);border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(dp(1.2f));border.setColor(0x995DEBDC);
            canvas.drawRect(left,top,left+scaledW,top+scaledH,border);
            // Little editor-style alignment handles
            Paint point=new Paint(3);point.setColor(CYAN);
            float z=dp(4);
            canvas.drawCircle(left,top,z,point);
            canvas.drawCircle(left+scaledW,top,z,point);
            canvas.drawCircle(left,top+scaledH,z,point);
            canvas.drawCircle(left+scaledW,top+scaledH,z,point);
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            int a=event.getActionMasked();
            if(a!=MotionEvent.ACTION_DOWN && a!=MotionEvent.ACTION_MOVE && a!=MotionEvent.ACTION_UP)return true;
            if(scaledW<=0 || scaledH<=0)return true;
            float x=Math.max(0,Math.min(1,(event.getX()-left)/scaledW));
            float y=Math.max(0,Math.min(1,(event.getY()-top)/scaledH));
            if(inGlitchLab && glitchBrushMode) {
                if(a==MotionEvent.ACTION_DOWN)push();
                if(a==MotionEvent.ACTION_DOWN || a==MotionEvent.ACTION_MOVE) {
                    if(state.glitch.brush.size()<10000)
                        state.glitch.brush.add(new GlitchFx.Point(x,y));
                    invalidate();
                }
                if(a==MotionEvent.ACTION_UP) {
                    requestGlitchRender();
                    invalidate();
                }
                return true;
            }
            if(drawing){
                if(a==MotionEvent.ACTION_DOWN){
                    push();activeStroke=new Stroke();activeStroke.color=INKS[inkIndex];
                    activeStroke.coords.add(x);activeStroke.coords.add(y);state.strokes.add(activeStroke);
                }else if(a==MotionEvent.ACTION_MOVE && activeStroke!=null){
                    activeStroke.coords.add(x);activeStroke.coords.add(y);
                }else if(a==MotionEvent.ACTION_UP)activeStroke=null;
            }
            invalidate();return true;
        }
    }
    private Bitmap exportBitmap() {
        Bitmap result=Bitmap.createBitmap(outputWidth(),outputHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);artwork(c,result.getWidth(),result.getHeight());return result;
    }
    private void save(boolean share){save(share,"png");}
    private void save(boolean share,String format){
        String ext=format.toLowerCase(Locale.ROOT);
        if(!ext.equals("png") && !ext.equals("jpg") && !ext.equals("webp"))ext="png";
        final String finalExt=ext;
        final String mime=ext.equals("jpg")?"image/jpeg":ext.equals("webp")?"image/webp":"image/png";
        final Bitmap.CompressFormat compression=ext.equals("jpg")?Bitmap.CompressFormat.JPEG:
            ext.equals("webp")?(Build.VERSION.SDK_INT>=30?Bitmap.CompressFormat.WEBP_LOSSLESS:
                Bitmap.CompressFormat.WEBP):Bitmap.CompressFormat.PNG;
        if(ext.equals("webp") && Build.VERSION.SDK_INT<30){
            new AlertDialog.Builder(this)
                .setTitle("WebP export")
                .setMessage("This Android version only supports lossy WebP. Choose PNG for lossless quality.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Export WebP",(dialog,which)->doExport(share,finalExt,mime,compression))
                .show();
        }else doExport(share,finalExt,mime,compression);
    }
    private void doExport(boolean share,String ext,String mime,Bitmap.CompressFormat compression){
        toast("Rendering original-resolution image…");
        // Keep the UI responsive during expensive pixel sorting and high-res compression.
        final int width=outputWidth(),height=outputHeight();
        final Bitmap snapshot=rotated();
        final State exportState=state.copy();
        final boolean wasCompare=glitchCompare;
        // No mutable editor state may be accessed from the export worker.
        // A clone of the drawing state is installed only after the worker returns.
        glitchWorkers.submit(()->{
            try {
                // The existing renderer includes captions, screenshot framing,
                // annotations and the non-negotiable Free watermark.
                // Export on UI for Canvas state consistency after offscreen glitch pixels are ready.
                final Bitmap glitchResult=exportState.glitch.enabled()
                    ?GlitchFx.render(snapshot,exportState.glitch):snapshot;
                mainHandler.post(()->{
                    try {
                        // Copy the existing state and cache just for the one synchronous Canvas composite.
                        final State prevState=state;
                        final Bitmap prevCache=glitchCache;
                        final String prevKey=glitchCacheKey;
                        final boolean prevCompare=glitchCompare;
                        try{
                            state=exportState;
                            glitchCompare=false;
                            glitchCache=glitchResult;
                            glitchCacheKey=glitchSignature();
                            Bitmap bitmap=exportBitmap();
                            saveEncodedBitmap(bitmap,share,ext,mime,compression);
                        }finally{
                            state=prevState;
                            glitchCompare=prevCompare;
                            glitchCache=prevCache;
                            glitchCacheKey=prevKey;
                            if(preview!=null)preview.invalidate();
                        }
                    }catch(Exception err){
                        new AlertDialog.Builder(this).setTitle("Export failed")
                            .setMessage(err.getMessage()).setPositiveButton("OK",null).show();
                    }
                });
            }catch(Exception error){
                mainHandler.post(()->new AlertDialog.Builder(this).setTitle("Export failed")
                    .setMessage(error.getMessage()).setPositiveButton("OK",null).show());
            }
        });
    }
    private void saveEncodedBitmap(Bitmap bitmap,boolean share,String ext,String mime,Bitmap.CompressFormat compression)throws Exception {
        ContentValues cv=new ContentValues();
        String filename="BlendCraft_"+System.currentTimeMillis()+"."+ext;
        cv.put(MediaStore.Images.Media.DISPLAY_NAME,filename);
        cv.put(MediaStore.Images.Media.MIME_TYPE,mime);
        cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/BlendCraft");
        cv.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
        if(uri==null)throw new Exception("Media library unavailable");
        try{
            // JPG has no transparency; explicitly flatten against white.
            Bitmap encoded=bitmap;
            if(ext.equals("jpg")){
                encoded=Bitmap.createBitmap(bitmap.getWidth(),bitmap.getHeight(),Bitmap.Config.ARGB_8888);
                Canvas flat=new Canvas(encoded);
                flat.drawColor(Color.WHITE);
                flat.drawBitmap(bitmap,0,0,new Paint(3));
            }
            try(OutputStream stream=getContentResolver().openOutputStream(uri)){
                if(stream==null || !encoded.compress(compression,ext.equals("jpg")?95:100,stream))
                    throw new Exception("Encoder failed");
            }
            if(encoded!=bitmap)encoded.recycle();
        }catch(Exception ex){
            getContentResolver().delete(uri,null,null);
            bitmap.recycle();
            throw ex;
        }
        bitmap.recycle();
        cv.clear();cv.put(MediaStore.Images.Media.IS_PENDING,0);
        getContentResolver().update(uri,cv,null,null);
        addRecent(uri);
        if(share){
            Intent intent=new Intent(Intent.ACTION_SEND);
            intent.setType(mime);intent.putExtra(Intent.EXTRA_STREAM,uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,"Share BlendCraft creation"));
        }else toast("Saved "+ext.toUpperCase(Locale.ROOT)+" in Pictures/BlendCraft");
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
