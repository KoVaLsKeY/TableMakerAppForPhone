package ua.komirka.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

/** Small native design system; all interactive controls have at least a 48 dp touch target. */
public final class Design {
    public static final int BG=0xFFF6F8F7, WHITE=0xFFFFFFFF, INK=0xFF203934, MUTED=0xFF647771,
        GREEN=0xFF16796D, PALE=0xFFE6F3ED, BORDER=0xFFDDE6E1, RED=0xFFAC4141,
        RED_PALE=0xFFFBEDEA, GOLD=0xFF8D6223, GOLD_PALE=0xFFFBF2DC;
    public final Context context;
    public Design(Context context) { this.context=context; }
    public int dp(float n) { return Math.round(n*context.getResources().getDisplayMetrics().density); }
    public LinearLayout column() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.VERTICAL); return l; }
    public LinearLayout row() { LinearLayout l=new LinearLayout(context); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    public TextView text(String text,float size,int color,boolean bold) {
        TextView t=new TextView(context); t.setText(text); t.setTextSize(size); t.setTextColor(color);
        t.setFontFeatureSettings("kern"); t.setLineSpacing(dp(2),1); t.setIncludeFontPadding(false);
        if(bold) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t;
    }
    public TextView title(String value) { return text(value,30,INK,true); }
    public TextView label(String value) { return text(value,16,INK,true); }
    public TextView muted(String value) { return text(value,14,MUTED,false); }
    public GradientDrawable shape(int color,int radius,int stroke) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if(stroke!=0) d.setStroke(dp(1),stroke); return d;
    }
    public void background(View view,int color,int radius,int stroke) { view.setBackground(shape(color,radius,stroke)); }
    public void clickable(View view,Runnable action) {
        view.setClickable(true); view.setFocusable(true);
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(0x2216796D),null,shape(WHITE,18,0)));
        view.setOnClickListener(v->action.run());
    }
    public LinearLayout card() {
        LinearLayout l=column(); l.setPadding(dp(18),dp(18),dp(18),dp(18)); background(l,WHITE,20,BORDER); return l;
    }
    public Button button(String value,boolean primary,Runnable action) {
        Button b=new Button(context); b.setText(value); b.setAllCaps(false); b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setTextColor(primary?WHITE:GREEN);
        b.setMinHeight(dp(54)); b.setMinimumHeight(dp(54)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setGravity(Gravity.CENTER); b.setPadding(dp(16),dp(12),dp(16),dp(12));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22647771),shape(primary?GREEN:WHITE,16,primary?0:BORDER),null));
        b.setStateListAnimator(null); b.setOnClickListener(v->action.run()); return b;
    }
    public Button quiet(String value,Runnable action) {
        Button b=button(value,false,action); b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22647771),shape(BG,14,0),null)); return b;
    }
    public Button danger(String value,Runnable action) {
        Button b=button(value,false,action); b.setTextColor(RED); background(b,RED_PALE,16,0); return b;
    }
    public Button icon(String mark,String description,Runnable action) {
        Button b=quiet(mark,action); b.setTextSize(22); b.setPadding(dp(8),dp(8),dp(8),dp(8));
        b.setContentDescription(description); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); return b;
    }
    public EditText input(String hint,String value,int type) {
        EditText e=new EditText(context); e.setTextSize(17); e.setTextColor(INK); e.setHintTextColor(MUTED);
        e.setHint(hint); e.setContentDescription(hint); e.setInputType(type); e.setPadding(dp(16),dp(14),dp(16),dp(14));
        e.setMinHeight(dp(56)); background(e,WHITE,14,BORDER); e.setSelectAllOnFocus(false);
        e.setImeOptions(EditorInfo.IME_ACTION_NEXT|EditorInfo.IME_FLAG_NO_EXTRACT_UI); e.setText(value);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); return e;
    }
    public void watch(EditText e,java.util.function.Consumer<String> changed) {
        e.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { changed.accept(s.toString()); }
            @Override public void afterTextChanged(Editable value) {}
        });
    }
    public TextView chip(String label,int color,int bg) {
        TextView t=text(label,12,color,true); t.setPadding(dp(10),dp(6),dp(10),dp(6)); background(t,bg,8,0); return t;
    }
    public void add(LinearLayout parent,View child) { add(parent,child,0); }
    public void add(LinearLayout parent,View child,int top) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(top); parent.addView(child,lp);
    }
    public void weight(LinearLayout row,View child) { row.addView(child,new LinearLayout.LayoutParams(0,-2,1)); }
    public void gap(LinearLayout row,int width) { View v=new View(context); row.addView(v,new LinearLayout.LayoutParams(dp(width),1)); }
    public void space(LinearLayout parent,int height) { View v=new View(context); parent.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    public void rule(LinearLayout parent) { View v=new View(context); v.setBackgroundColor(BORDER); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1)); p.topMargin=dp(14); p.bottomMargin=dp(14); parent.addView(v,p); }
    public void section(LinearLayout parent,String title,String subtitle) {
        add(parent,label(title),22); if(subtitle!=null&&!subtitle.isEmpty()) add(parent,muted(subtitle),7);
    }
    public TextView info(LinearLayout parent,String content) {
        TextView v=text(content,14,GREEN,false); v.setPadding(dp(16),dp(14),dp(16),dp(14)); background(v,PALE,14,0); add(parent,v,16); return v;
    }
    public TextView error(LinearLayout parent) {
        TextView v=text("",14,RED,false); v.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); add(parent,v,10); return v;
    }
    public TextView badge(String mark,int index) {
        int[] colors={GREEN,0xFF5566A7,0xFF9A642A,0xFF98627D};
        int[] shades={PALE,0xFFEBEEF9,0xFFFBF0DF,0xFFF7EAF1};
        int n=Math.floorMod(index,4); TextView v=text(mark,21,colors[n],true); v.setGravity(Gravity.CENTER); background(v,shades[n],14,0); return v;
    }
}
