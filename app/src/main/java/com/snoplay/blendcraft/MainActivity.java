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
            int sample=1;
            while(options.outWidth/sample>2600 || options.outHeight/sample>2600) sample*=2;
            options.inSampleSize=sample; options.inJustDecodeBounds=false;
            try(InputStream actual=getContentResolver().openInputStream(uri)) {
                Bitmap bm=BitmapFactory.decodeStream(actual,null,options);
                if(bm==null)throw new Exception("Not a supported image.");
                source=bm; rotatedCache=null; cacheAngle=-1;
                state=new State(); undo.clear(); redo.clear(); mode=studio;
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
    private void showStudio() {
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

        FrameLayout canvasHolder=new FrameLayout(this);
        canvasHolder.setBackgroundColor(0xFF101429);
        preview=new Preview();
        canvasHolder.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        root.addView(canvasHolder,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout canvasStatus=row();canvasStatus.setPadding(dp(16),dp(7),dp(16),dp(7));
        TextView pre=text("✦   LIVE PREVIEW",10,CYAN,true);pre.setLetterSpacing(.12f);
        canvasStatus.addView(pre,new LinearLayout.LayoutParams(0,-2,1));
        canvasStatus.addView(text(drawing?"● DRAWING ENABLED":state.waterOn?"DRAG TO MOVE WATERMARK":"UNDO / REDO BELOW",10,MUTED,false));
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
            addFlexButton(extras,"✦ Watermark",()->watermarkDialog(),false);
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
