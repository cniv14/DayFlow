package app.dayflow.markdown;

import android.graphics.Typeface;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.appcompat.widget.AppCompatEditText;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.yahiaangelo.markdownedittext.MarkdownEditText;

import org.json.*;

/** Embedded native open-source editor, with a lossless Markdown source mode. */
public final class NoteEditor {
    private final MainActivity a;private JSONObject original;
    private final TextInputEditText title;private final MarkdownEditText visual,serializer;private final AppCompatEditText source;
    private final FrameLayout area;private final TextView preview,status;
    private String mode,raw;private boolean loading,visualChanged,sourceChanged,capturing;private final NoteHistory history=new NoteHistory();private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());private android.net.Uri externalUri;private boolean externalWrite;private final Runnable keepDraftLater=()->keepDraft();private final Runnable remember=()->{if(!loading)history.capture(text());};
    public NoteEditor(MainActivity activity,JSONObject record){a=activity;history.attach(a.getCacheDir());original=record;raw=record.optString("content");
        LinearLayout page=a.editorPage("Markdown 笔记",()->{if(save(false))a.home();});
        LinearLayout heading=Ui.column(a);heading.setPadding(Ui.dp(a,16),0,Ui.dp(a,16),0);page.addView(heading);LinearLayout actions=Ui.row(a);actions.addView(Ui.button(a,"保存",true,()->save(true)),new LinearLayout.LayoutParams(0,-2,1));actions.addView(Ui.button(a,"导出",false,()->a.exportMarkdown(draft())),new LinearLayout.LayoutParams(0,-2,1));actions.addView(smallAction("↶","撤销",()->restoreHistory(false)),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));actions.addView(smallAction("↷","重做",()->restoreHistory(true)),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));actions.addView(smallAction("Aa","调整字号",this::fontSize),new LinearLayout.LayoutParams(Ui.dp(a,48),Ui.dp(a,48)));heading.addView(actions);title=Ui.field(a,heading,"笔记名称",record.optString("title"),false);title.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        LinearLayout tabs=Ui.row(a);for(String label:new String[]{"编辑","源码","预览"})tabs.addView(Ui.button(a,label,false,()->switchMode(label)),new LinearLayout.LayoutParams(0,-2,1));heading.addView(tabs);HorizontalScrollView shortcuts=new HorizontalScrollView(a);shortcuts.setHorizontalScrollBarEnabled(false);LinearLayout formats=Ui.row(a);String[][] tokens={{"粗体","**","**"},{"斜体","*","*"},{"删除线","~~","~~"},{"待办","- [ ] ",""},{"列表","- ",""},{"编号","1. ",""},{"分隔线","\n\n---\n\n",""},{"标题","# ",""},{"引用","> ",""},{"链接","[", "](https://)"},{"图片","![", "](https://)"},{"表格","\n| 标题 | 标题 |\n| --- | --- |\n| 内容 | 内容 |\n",""},{"代码","\n```\n","\n```\n"},{"脚注","[^1]\n\n[^1]: ",""}};for(String[] token:tokens)formats.addView(Ui.button(a,token[0],false,()->insertMarkup(token[1],token[2])),new LinearLayout.LayoutParams(Ui.dp(a,64),Ui.dp(a,48)));shortcuts.addView(formats);heading.addView(shortcuts);if(NoteDrafts.exists(a,record.optString("id")))heading.addView(Ui.button(a,"恢复本机未保存草稿",false,()->{try{JSONObject recovered=draft().put("content",NoteDrafts.read(a,record.optString("id")));a.edit(recovered);}catch(Exception e){a.message(e.getMessage());}}));
        area=new FrameLayout(a);page.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        visual=new MarkdownEditText(a,null);visual.setSaveEnabled(false);visual.setGravity(Gravity.TOP|Gravity.START);visual.setTextSize(a.prefs.getInt("note_font_size",17));visual.setTextColor(Ui.ink(a));visual.setHintTextColor(Ui.muted(a));visual.setHint("开始写笔记…");visual.setBackgroundColor(android.graphics.Color.TRANSPARENT);visual.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));area.addView(visual,new FrameLayout.LayoutParams(-1,-1));
        serializer=new MarkdownEditText(a,null);
        source=new AppCompatEditText(a);source.setSaveEnabled(false);source.setGravity(Gravity.TOP|Gravity.START);source.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);source.setTextSize(a.prefs.getInt("note_font_size",17));source.setTypeface(Typeface.MONOSPACE);source.setTextColor(Ui.ink(a));source.setHintTextColor(Ui.muted(a));source.setHint("# 标题\n\n直接编写 Markdown");source.setBackgroundColor(android.graphics.Color.TRANSPARENT);source.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));area.addView(source,new FrameLayout.LayoutParams(-1,-1));source.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){history.close();}});
        ScrollView scroll=new ScrollView(a);preview=Ui.text(a,"",a.prefs.getInt("note_font_size",17),false);preview.setTextIsSelectable(true);preview.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,24));scroll.addView(preview);scroll.setTag("preview");area.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        status=Ui.help(a,"UTF-8 · .md · 保存后同步");status.setPadding(Ui.dp(a,16),Ui.dp(a,3),Ui.dp(a,16),Ui.dp(a,3));page.addView(status);

        visual.addTextChangedListener(watch(true));source.addTextChangedListener(watch(false));
        // Tables, fenced code and headings stay in lossless source mode by default.
        switchMode(complex(raw)?"源码":"编辑");history.capture(raw);
    }
    private void insertMarkup(String before,String after){switchMode("源码");int start=Math.max(0,source.getSelectionStart()),end=Math.max(start,source.getSelectionEnd());String selected=source.getText().subSequence(start,end).toString();source.getText().replace(start,end,before+selected+after);source.setSelection(Math.min(source.length(),start+before.length()+selected.length()));source.requestFocus();}
    private android.view.View smallAction(String label,String description,Runnable action){MaterialButton button=Ui.button(a,label,false,action);button.setMinWidth(0);button.setPadding(0,0,0,0);button.setContentDescription(description);return button;}
    private TextWatcher watch(boolean rich){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){if(!loading&&!capturing){queueHistory();if(rich)visualChanged=true;else sourceChanged=true;status.setText("未保存 · .md · "+s.length()+" 字符");}}public void afterTextChanged(Editable e){if(rich&&e.getSpans(0,e.length(),FormatWatcher.class).length==0)e.setSpan(new FormatWatcher(),0,e.length(),Spanned.SPAN_INCLUSIVE_INCLUSIVE);}};}
    private final class FormatWatcher implements SpanWatcher {
        private void changed(Object what){if(!loading&&!capturing&&(what instanceof android.text.style.CharacterStyle||what instanceof android.text.style.ParagraphStyle)){visualChanged=true;queueHistory();status.setText("未保存 · .md");}}
        public void onSpanAdded(Spannable text,Object what,int start,int end){changed(what);}public void onSpanRemoved(Spannable text,Object what,int start,int end){changed(what);}public void onSpanChanged(Spannable text,Object what,int oldStart,int oldEnd,int newStart,int newEnd){changed(what);}
    }
    private void queueHistory(){handler.removeCallbacks(remember);handler.postDelayed(remember,600);handler.removeCallbacks(keepDraftLater);handler.postDelayed(keepDraftLater,1600);}
    private String text(){if("编辑".equals(mode)&&visualChanged){capturing=true;try{
        // Upstream getMD removes list watchers and normalises spans. Serialize a copy so
        // periodic undo snapshots do not change the live editor's formatting behavior.
        SpannableStringBuilder copy=new SpannableStringBuilder(visual.getText());for(Object watcher:copy.getSpans(0,copy.length(),Object.class))if(watcher instanceof TextWatcher||watcher instanceof SpanWatcher)copy.removeSpan(watcher);
        serializer.setText(copy);return serializer.getMD();
    }finally{capturing=false;}}if("源码".equals(mode))return source.getText().toString();return raw;}
    private void restoreHistory(boolean redo){handler.removeCallbacks(remember);history.capture(text());String value=redo?history.redo():history.undo();if(value==null){a.message(redo?"没有可重做的修改":"没有可撤销的修改");return;}loading=true;raw=value;source.setText(value);source.setSelection(source.length());for(int i=0;i<area.getChildCount();i++)area.getChildAt(i).setVisibility(View.GONE);source.setVisibility(View.VISIBLE);mode="源码";visualChanged=false;loading=false;status.setText("未保存 · .md");}
    private void fontSize(){LinearLayout page=a.fullPage("笔记字号",()->a.edit(draft()));page.addView(Ui.help(a,"调整编辑、源码和预览字号，Markdown 原文保持不变。"));SeekBar size=new SeekBar(a);size.setMax(20);size.setProgress(a.prefs.getInt("note_font_size",17)-12);TextView sample=Ui.text(a,"字号示例 · 日栖笔记",a.prefs.getInt("note_font_size",17),false);page.addView(sample);page.addView(size);size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){sample.setTextSize(12+n);sample.setText("字号 "+(12+n)+" sp · 日栖笔记");a.prefs.edit().putInt("note_font_size",12+n).apply();}});page.addView(Ui.button(a,"返回笔记",true,()->a.edit(draft())));}

    public void close(){handler.removeCallbacks(remember);handler.removeCallbacks(keepDraftLater);history.close();}
    public void keepDraft(){if(a.noteState!=this)return;try{JSONObject r=draft();if(r.optString("content").isEmpty()&&r.optString("title").isEmpty())return;if(r.optString("content").equals(original.optString("content"))&&r.optString("title").equals(original.optString("title")))return;NoteDrafts.queue(a,r,value->a.runOnUiThread(()->{if(!a.isDestroyed()&&a.noteState==this)status.setText(value+" · 手动保存后同步");}));}catch(Exception e){status.setText("请手动保存或导出笔记");}}
    public void bindExternal(android.net.Uri uri,boolean writable){externalUri=uri;externalWrite=writable;status.setText(writable?"已打开原文件 · 保存会写回原文件":"文件只读 · 保存到笔记，可导出 .md");}
    public android.net.Uri externalUri(){return externalUri;}public boolean externalWritable(){return externalWrite;}
    public boolean persistForNavigation(){return save(false);}
    private void switchMode(String next){if(next.equals(mode))return;boolean fromSource="源码".equals(mode);raw=text();if("编辑".equals(next)&&complex(raw)){next="源码";status.setText("源码编辑 · 完整保留Markdown，预览查看效果");}loading=true;
        for(int i=0;i<area.getChildCount();i++)area.getChildAt(i).setVisibility(View.GONE);
        if("编辑".equals(next)){visual.renderMD(raw);visualChanged=false;visual.setVisibility(View.VISIBLE);}else if("源码".equals(next)){source.setText(raw);sourceChanged=false;source.setVisibility(View.VISIBLE);}else{NoteTools.render(preview,raw);area.findViewWithTag("preview").setVisibility(View.VISIBLE);((android.view.inputmethod.InputMethodManager)a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(source.getWindowToken(),0);}
        mode=next;loading=false;
    }
    private static boolean complex(String value){if(value.contains("[^")||value.contains("](")||value.contains("$")||value.contains("`")||value.contains("![")||value.contains("<")||value.contains("\n---"))return true;return value.matches("(?s).*(?m:^\\s*(#{1,6} |```|~~~|\\|)).*");}
    public JSONObject draft(){try{return new JSONObject(original.toString()).put("kind","note").put("title",title.getText().toString().trim()).put("content",text()).put("content_format","markdown");}catch(JSONException e){throw new IllegalStateException(e);}}
    private boolean save(boolean feedback){try{JSONObject r=draft();if(!feedback&&(r.optString("title").isEmpty()||r.optString("title").equals(original.optString("title")))&&r.optString("content").isEmpty()&&r.optString("image_base64").isEmpty()&&Data.get(a).find(r.getString("id"))==null)return true;String name=r.optString("title");if(name.isEmpty()){name=java.time.LocalDate.now().toString();r.put("title",name);title.setText(name);}

        JSONObject current=Data.get(a).find(r.getString("id"));if(current!=null&&!current.optString("revision").equals(original.optString("revision")))throw new Exception("笔记已从其他设备更新，请先导出当前内容，再重新打开笔记");
        Data.validate(r);if(current==null||!current.optString("content").equals(r.optString("content"))||!current.optString("title").equals(name)){Data.get(a).save(r);try{NoteTasks.fromNote(a,r);}catch(Exception e){a.message("笔记已保存，部分关联任务未更新："+e.getMessage());}original=r;a.changed();}else{original=current;MarkdownFiles.update(a,current);}raw=r.optString("content");handler.removeCallbacks(keepDraftLater);NoteDrafts.clear(a,r.getString("id"));if(externalUri!=null&&externalWrite){try(java.io.OutputStream output=a.getContentResolver().openOutputStream(externalUri,"wt")){if(output==null)throw new java.io.IOException("无法写入文件");output.write(MarkdownFiles.markdown(r).getBytes(java.nio.charset.StandardCharsets.UTF_8));MarkdownDocuments.wrote(a,externalUri,r);}catch(Exception e){status.setText("笔记已保存，原文件未写入，可导出副本");a.message("笔记已保存，但无法写回原文件："+e.getMessage());return true;}}status.setText("已保存为 .md · "+raw.length()+" 字符");if(feedback)a.message("已保存 Markdown 笔记");return true;
        }catch(Exception e){a.message(e.getMessage());return false;}}
}
