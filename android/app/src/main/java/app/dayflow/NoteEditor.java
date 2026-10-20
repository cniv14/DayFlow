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
    private final TextInputEditText title;private final MarkdownEditText visual,serializer;private final AppCompatEditText source;
    private final MarkdownStylesBar styles;private final FrameLayout area;private final TextView preview,status;
    private String mode,raw;private boolean loading,visualChanged,sourceChanged,capturing;private final NoteHistory history=new NoteHistory();private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());private android.net.Uri externalUri;private boolean externalWrite;private final Runnable remember=()->{if(!loading)history.capture(text());};
    public NoteEditor(MainActivity activity,JSONObject record){a=activity;original=record;raw=record.optString("content");
        LinearLayout page=a.editorPage("Markdown 笔记",()->{if(save(false))a.home();});
        LinearLayout heading=Ui.column(a);heading.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),0);page.addView(heading);LinearLayout actions=Ui.row(a);actions.addView(Ui.button(a,"保存",true,()->save(true)),new LinearLayout.LayoutParams(Ui.dp(a,68),Ui.dp(a,48)));actions.addView(Ui.button(a,"导出",false,()->a.exportMarkdown(draft())),new LinearLayout.LayoutParams(Ui.dp(a,68),Ui.dp(a,48)));actions.addView(smallAction("↶","撤销",()->restoreHistory(false)),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));actions.addView(smallAction("↷","重做",()->restoreHistory(true)),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));actions.addView(smallAction("ƒx","插入公式",this::insertFormula),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));heading.addView(actions);title=Ui.field(a,heading,"笔记名称",record.optString("title"),false);
        LinearLayout tabs=Ui.row(a);for(String label:new String[]{"编辑","源码","预览"})tabs.addView(Ui.button(a,label,false,()->switchMode(label)),new LinearLayout.LayoutParams(0,-2,1));heading.addView(tabs);
        area=new FrameLayout(a);page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        visual=new MarkdownEditText(a,null);visual.setGravity(Gravity.TOP|Gravity.START);visual.setTextSize(17);visual.setTextColor(Ui.ink(a));visual.setHintTextColor(Ui.muted(a));visual.setHint("开始写笔记…");visual.setBackgroundColor(android.graphics.Color.TRANSPARENT);visual.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));visual.setFilters(new InputFilter[]{new InputFilter.LengthFilter(65536)});area.addView(visual,new FrameLayout.LayoutParams(-1,-1));
        serializer=new MarkdownEditText(a,null);
        source=new AppCompatEditText(a);source.setGravity(Gravity.TOP|Gravity.START);source.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);source.setTextSize(15);source.setTypeface(Typeface.MONOSPACE);source.setTextColor(Ui.ink(a));source.setHintTextColor(Ui.muted(a));source.setHint("# 标题\n\n直接编写 Markdown");source.setBackgroundColor(android.graphics.Color.TRANSPARENT);source.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));source.setFilters(new InputFilter[]{new InputFilter.LengthFilter(65536)});area.addView(source,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(a);preview=Ui.text(a,"",17,false);preview.setTextIsSelectable(true);preview.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));scroll.addView(preview);scroll.setTag("preview");area.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        styles=new MarkdownStylesBar(a,null,0);styles.setBackgroundColor(Ui.surface(a));styles.setStylesList(new MarkdownEditText.TextStyle[]{MarkdownEditText.TextStyle.BOLD,MarkdownEditText.TextStyle.ITALIC,MarkdownEditText.TextStyle.STRIKE,MarkdownEditText.TextStyle.UNORDERED_LIST,MarkdownEditText.TextStyle.ORDERED_LIST,MarkdownEditText.TextStyle.TASKS_LIST});visual.setStylesBar(styles);page.addView(styles,new LinearLayout.LayoutParams(-1,Ui.dp(a,52)));
        status=Ui.help(a,"UTF-8 · .md · 保存后同步");status.setPadding(Ui.dp(a,16),Ui.dp(a,3),Ui.dp(a,16),Ui.dp(a,3));page.addView(status);

        visual.addTextChangedListener(watch(true));source.addTextChangedListener(watch(false));
        // Tables, fenced code and headings stay in lossless source mode by default.
        switchMode(complex(raw)?"源码":"编辑");history.capture(raw);
    }
    private android.view.View smallAction(String label,String description,Runnable action){MaterialButton button=Ui.button(a,label,false,action);button.setMinWidth(0);button.setPadding(0,0,0,0);button.setContentDescription(description);return button;}
    private TextWatcher watch(boolean rich){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!loading&&!capturing){queueHistory();if(rich)visualChanged=true;else sourceChanged=true;status.setText("未保存 · .md · "+s.length()+" 字符");}}public void afterTextChanged(Editable e){if(rich&&e.getSpans(0,e.length(),FormatWatcher.class).length==0)e.setSpan(new FormatWatcher(),0,e.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);}};}
    private final class FormatWatcher implements SpanWatcher {
        private void changed(Object what){if(!loading&&!capturing&&(what instanceof android.text.style.CharacterStyle||what instanceof android.text.style.ParagraphStyle)){visualChanged=true;queueHistory();status.setText("未保存 · .md");}}
        public void onSpanAdded(Spannable text,Object what,int start,int end){changed(what);}public void onSpanRemoved(Spannable text,Object what,int start,int end){changed(what);}public void onSpanChanged(Spannable text,Object what,int oldStart,int oldEnd,int newStart,int newEnd){changed(what);}
    }
    private void queueHistory(){handler.removeCallbacks(remember);handler.postDelayed(remember,600);}
    private String text(){if("编辑".equals(mode)&&visualChanged){capturing=true;try{
        // Upstream getMD removes list watchers and normalises spans. Serialize a copy so
        // periodic undo snapshots do not change the live editor's formatting behavior.
        SpannableStringBuilder copy=new SpannableStringBuilder(visual.getText());for(Object watcher:copy.getSpans(0,copy.length(),Object.class))if(watcher instanceof TextWatcher||watcher instanceof SpanWatcher)copy.removeSpan(watcher);
        serializer.setText(copy);return serializer.getMD();
    }finally{capturing=false;}}if("源码".equals(mode))return source.getText().toString();return raw;}
    private void restoreHistory(boolean redo){handler.removeCallbacks(remember);history.capture(text());String value=redo?history.redo():history.undo();if(value==null){a.message(redo?"没有可重做的修改":"没有可撤销的修改");return;}loading=true;raw=value;source.setText(value);source.setSelection(source.length());for(int i=0;i<area.getChildCount();i++)area.getChildAt(i).setVisibility(View.GONE);source.setVisibility(View.VISIBLE);styles.setVisibility(View.GONE);mode="源码";visualChanged=false;loading=false;status.setText("未保存 · .md");}
    private void insertFormula(){switchMode("源码");int at=Math.max(0,source.getSelectionStart());source.getText().insert(at,"$E=mc^2$");source.setSelection(at+1,at+7);source.requestFocus();}
    public void bindExternal(android.net.Uri uri,boolean writable){externalUri=uri;externalWrite=writable;status.setText(writable?"已打开原文件 · 保存会写回原文件":"文件只读 · 保存到笔记，可导出 .md");}
    public android.net.Uri externalUri(){return externalUri;}public boolean externalWritable(){return externalWrite;}
    public boolean persistForNavigation(){return save(false);}
    private void switchMode(String next){if(next.equals(mode))return;boolean fromSource="源码".equals(mode);raw=text();if("编辑".equals(next)&&complex(raw)){a.message("此笔记包含复杂 Markdown，请用源码编辑并在预览中查看，避免格式丢失");return;}loading=true;
        for(int i=0;i<area.getChildCount();i++)area.getChildAt(i).setVisibility(View.GONE);styles.setVisibility(View.GONE);
        if("编辑".equals(next)){visual.renderMD(raw);visualChanged=false;visual.setVisibility(View.VISIBLE);styles.setVisibility(View.VISIBLE);}else if("源码".equals(next)){source.setText(raw);sourceChanged=false;source.setVisibility(View.VISIBLE);}else{NoteTools.render(preview,raw);area.findViewWithTag("preview").setVisibility(View.VISIBLE);((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(source.getWindowToken(),0);}
        mode=next;loading=false;
    }
    private static boolean complex(String value){if(value.contains("$")||value.contains("`")||value.contains("![")||value.contains("<")||value.contains("\n---"))return true;return value.matches("(?s).*(?m:^\\s*(#{1,6} |```|~~~|\\|)).*");}
    public JSONObject draft(){try{return new JSONObject(original.toString()).put("kind","note").put("title",title.getText().toString().trim()).put("content",text()).put("content_format","markdown");}catch(JSONException e){throw new IllegalStateException(e);}}
    private boolean save(boolean feedback){try{JSONObject r=draft();if(!feedback&&r.optString("title").isEmpty()&&r.optString("content").isEmpty()&&r.optString("image_base64").isEmpty()&&Data.get(a).find(r.getString("id"))==null)return true;String name=r.optString("title");if(name.isEmpty()){String first=r.optString("content").split("\\R",2)[0].replaceAll("^[#>*`\\s-]+","").trim();name=first.isEmpty()?"未命名笔记":first.substring(0,Math.min(80,first.length()));r.put("title",name);title.setText(name);}
        JSONObject current=Data.get(a).find(r.getString("id"));if(current!=null&&!current.optString("revision").equals(original.optString("revision")))throw new Exception("笔记已从其他设备更新，请先导出当前内容，再重新打开笔记");
        Data.validate(r);if(current==null||!current.optString("content").equals(r.optString("content"))||!current.optString("title").equals(name)){Data.get(a).save(r);original=r;a.changed();}else{original=current;MarkdownFiles.update(a,current);}raw=r.optString("content");if(externalUri!=null&&externalWrite){try(java.io.OutputStream output=a.getContentResolver().openOutputStream(externalUri,"wt")){if(output==null)throw new java.io.IOException("无法写入文件");output.write(MarkdownFiles.markdown(r).getBytes(java.nio.charset.StandardCharsets.UTF_8));MarkdownDocuments.wrote(a,externalUri,r);}catch(Exception e){status.setText("笔记已保存，原文件未写入，可导出副本");a.message("笔记已保存，但无法写回原文件："+e.getMessage());return true;}}status.setText("已保存为 .md · "+raw.length()+" 字符");if(feedback)a.message("已保存 Markdown 笔记");return true;
        }catch(Exception e){a.message(e.getMessage());return false;}}
}
