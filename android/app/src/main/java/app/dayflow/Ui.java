package app.dayflow;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.*;

/** Theme-driven native Material controls; never hard-code light foreground colours. */
public final class Ui {
    public static boolean compact(Context c){return c.getSharedPreferences("settings",0).getBoolean("compact_ui",false);}
    public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public static int color(Context c,int attr){return MaterialColors.getColor(c,attr,0xff386a5c);}
    public static int ink(Context c){return color(c,com.google.android.material.R.attr.colorOnSurface);}
    public static int muted(Context c){return color(c,com.google.android.material.R.attr.colorOnSurfaceVariant);}
    public static int primary(Context c){return color(c,androidx.appcompat.R.attr.colorPrimary);}
    public static int surface(Context c){return color(c,com.google.android.material.R.attr.colorSurface);}
    public static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    public static LinearLayout row(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static TextView text(Context c,String value,int size,boolean strong){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(ink(c));t.setLineSpacing(dp(c,2),1);if(strong)t.setTypeface(null,Typeface.BOLD);return t;}
    public static TextView help(Context c,String value){TextView t=text(c,value,12,false);t.setTextColor(muted(c));t.setPadding(0,dp(c,compact(c)?3:5),0,dp(c,compact(c)?5:9));return t;}
    public static MaterialButton button(Context c,String value,boolean filled,Runnable action){MaterialButton b=new MaterialButton(c,null,filled?com.google.android.material.R.attr.materialButtonStyle:com.google.android.material.R.attr.materialButtonOutlinedStyle);b.setText(value);b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(c,10),0,dp(c,10),0);b.setSingleLine(true);b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setMinHeight(dp(c,48));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,5),0,dp(c,5));b.setLayoutParams(p);b.setOnClickListener(v->action.run());return b;}
    public static MaterialCardView card(Context c){MaterialCardView card=new MaterialCardView(c);card.setRadius(dp(c,compact(c)?16:20));card.setCardElevation(0);card.setStrokeWidth(dp(c,1));card.setStrokeColor(color(c,com.google.android.material.R.attr.colorOutlineVariant));card.setCardBackgroundColor(color(c,com.google.android.material.R.attr.colorSurfaceContainerLow));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,compact(c)?4:6),0,dp(c,compact(c)?4:6));card.setLayoutParams(p);return card;}
    public static TextInputEditText field(Context c,LinearLayout parent,String label,String value,boolean multiline){TextInputLayout wrap=new TextInputLayout(c,null,com.google.android.material.R.attr.textInputStyle);wrap.setHint(label);wrap.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);TextInputEditText e=new TextInputEditText(wrap.getContext());e.setText(value);e.setTextSize(16);e.setSingleLine(!multiline);if(multiline){e.setMinLines(3);e.setGravity(Gravity.TOP);e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}else e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);if(label.contains("地址")||label.contains("URL"))e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(label.equals("标题")?1000:12000)});wrap.addView(e,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(c,7),0,dp(c,7));parent.addView(wrap,p);return e;}
    public static LinearLayout padded(Context c){LinearLayout l=column(c);l.setPadding(dp(c,compact(c)?14:20),dp(c,compact(c)?8:12),dp(c,compact(c)?14:20),dp(c,compact(c)?16:24));return l;}
}
