package app.dayflow;

import android.graphics.Typeface;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.AppCompatEditText;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.yahiaangelo.markdownedittext.MarkdownEditText;
import com.yahiaangelo.markdownedittext.MarkdownStylesBar;
import org.json.*;

/** Embedded native open-source editor, with a lossless Markdown source mode. */
public final class NoteEditor {
    private final MainActivity a;private JSONObject original;
    private final TextInputEditText title;private final MarkdownEditText visual;private final AppCompatEditText source;
    private final MarkdownStylesBar styles;private final FrameLayout area;private final TextView preview,status;
    private String mode,raw;private boolean loading,visualChanged,sourceChanged;
    public NoteEditor(MainActivity activity,JSONObject record){a=activity;original=record;raw=record.optString("content");
        LinearLayout page=a.editorPage("Markdown 笔记",()->{if(save(false))a.home();});
        LinearLayout heading=Ui.column(a);heading.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),0);page.addView(heading);title=Ui.field(a,heading,"笔记名称",record.optString("title"),false);
        LinearLayout tabs=Ui.row(a);for(String label:new String[]{"编辑","源码","预览"})tabs.addView(Ui.button(a,label,false,()->switchMode(label)),new LinearLayout.LayoutParams(0,-2,1));heading.addView(tabs);
        area=new FrameLayout(a);page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        visual=new MarkdownEditText(a,null);visual.setGravity(Gravity.TOP|Gravity.START);visual.setTextSize(17);visual.setTextColor(Ui.ink(a));visual.setHintTextColor(Ui.muted(a));visual.setHint("开始写笔记…");visual.setBackgroundColor(android.graphics.Color.TRANSPARENT);visual.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));visual.setFilters(new InputFilter[]{new InputFilter.LengthFilter(65536)});area.addView(visual,new FrameLayout.LayoutParams(-1,-1));
        source=new AppCompatEditText(a);source.setGravity(Gravity.TOP|Gravity.START);source.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);source.setTextSize(15);source.setTypeface(Typeface.MONOSPACE);source.setTextColor(Ui.ink(a));source.setHintTextColor(Ui.muted(a));source.setHint("# 标题\n\n直接编写 Markdown");source.setBackgroundColor(android.graphics.Color.TRANSPARENT);source.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));source.setFilters(new InputFilter[]{new InputFilter.LengthFilter(65536)});area.addView(source,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(a);preview=Ui.text(a,"",17,false);preview.setTextIsSelectable(true);preview.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));scroll.addView(preview);scroll.setTag("preview");area.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        styles=new MarkdownStylesBar(a,null,0);styles.setBackgroundColor(Ui.surface(a));styles.setStylesList(new MarkdownEditText.TextStyle[]{MarkdownEditText.TextStyle.BOLD,MarkdownEditText.TextStyle.ITALIC,MarkdownEditText.TextStyle.STRIKE,MarkdownEditText.TextStyle.UNORDERED_LIST,MarkdownEditText.TextStyle.ORDERED_LIST,MarkdownEditText.TextStyle.TASKS_LIST});visual.setStylesBar(styles);page.addView(styles,new LinearLayout.LayoutParams(-1,Ui.dp(a,52)));
        status=Ui.help(a,"UTF-8 · .md · 保存后同步");status.setPadding(Ui.dp(a,16),Ui.dp(a,3),Ui.dp(a,16),Ui.dp(a,3));page.addView(status);
        LinearLayout actions=Ui.row(a);actions.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),0);actions.addView(Ui.button(a,"保存",true,()->save(true)),new LinearLayout.LayoutParams(0,-2,1));actions.addView(Ui.button(a,"导出 .md",false,()->a.exportMarkdown(draft())),new LinearLayout.LayoutParams(0,-2,1));page.addView(actions);a.toolbarAction("保存",()->save(true));
        visual.addTextChangedListener(watch(true));source.addTextChangedListener(watch(false));
        // Tables, fenced code and headings stay in lossless source mode by default.
        switchMode(complex(raw)?"源码":"编辑");
    }
    private TextWatcher watch(boolean rich){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!loading){if(rich)visualChanged=true;else sourceChanged=true;status.setText("未保存 · .md · "+s.length()+" 字符");}}public void afterTextChanged(Editable e){if(rich&&e.getSpans(0,e.length(),FormatWatcher.class).length==0)e.setSpan(new FormatWatcher(),0,e.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);}};}
    private final class FormatWatcher implements SpanWatcher {
        private void changed(Object what){if(!loading&&(what instanceof android.text.style.CharacterStyle||what instanceof android.text.style.ParagraphStyle)){visualChanged=true;status.setText("未保存 · .md");}}
        public void onSpanAdded(Spannable text,Object what,int start,int end){changed(what);}public void onSpanRemoved(Spannable text,Object what,int start,int end){changed(what);}public void onSpanChanged(Spannable text,Object what,int oldStart,int oldEnd,int newStart,int newEnd){changed(what);}
    }
    private String text(){if("编辑".equals(mode)&&visualChanged)return visual.getMD();if("源码".equals(mode))return source.getText().toString();return raw;}
    private void switchMode(String next){if(next.equals(mode))return;boolean fromSource="源码".equals(mode);raw=text();if("编辑".equals(next)&&complex(raw)){a.message("此笔记包含复杂 Markdown，请用源码编辑并在预览中查看，避免格式丢失");return;}loading=true;
        for(int i=0;i<area.getChildCount();i++)area.getChildAt(i).setVisibility(View.GONE);styles.setVisibility(View.GONE);
        if("编辑".equals(next)){visual.renderMD(raw);visualChanged=false;visual.setVisibility(View.VISIBLE);styles.setVisibility(View.VISIBLE);}else if("源码".equals(next)){source.setText(raw);sourceChanged=false;source.setVisibility(View.VISIBLE);}else{NoteTools.render(preview,raw);area.findViewWithTag("preview").setVisibility(View.VISIBLE);((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(source.getWindowToken(),0);}
        mode=next;loading=false;
    }
    private static boolean complex(String value){if(value.contains("`")||value.contains("![")||value.contains("<")||value.contains("\n---"))return true;return value.matches("(?s).*(?m:^\\s*(#{1,6} |```|~~~|\\|)).*");}
    public JSONObject draft(){try{return new JSONObject(original.toString()).put("kind","note").put("title",title.getText().toString().trim()).put("content",text()).put("content_format","markdown");}catch(JSONException e){throw new IllegalStateException(e);}}
    private boolean save(boolean feedback){try{JSONObject r=draft();if(!feedback&&r.optString("title").isEmpty()&&r.optString("content").isEmpty()&&r.optString("image_base64").isEmpty()&&Data.get(a).find(r.getString("id"))==null)return true;String name=r.optString("title");if(name.isEmpty()){String first=r.optString("content").split("\\R",2)[0].replaceAll("^[#>*`\\s-]+","").trim();name=first.isEmpty()?"未命名笔记":first.substring(0,Math.min(80,first.length()));r.put("title",name);title.setText(name);}
        JSONObject current=Data.get(a).find(r.getString("id"));if(current!=null&&!current.optString("revision").equals(original.optString("revision")))throw new Exception("笔记已从其他设备更新，请先导出当前内容，再重新打开笔记");
        Data.validate(r);if(current==null||!current.optString("content").equals(r.optString("content"))||!current.optString("title").equals(name)){Data.get(a).save(r);original=r;a.changed();}else{original=current;MarkdownFiles.update(a,current);}raw=r.optString("content");status.setText("已保存为 .md · "+raw.length()+" 字符");if(feedback)a.message("已保存 Markdown 笔记");return true;
        }catch(Exception e){a.message(e.getMessage());return false;}}
}
