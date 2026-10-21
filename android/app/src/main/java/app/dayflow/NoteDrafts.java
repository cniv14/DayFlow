package app.dayflow;
import android.content.Context;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Private recovery snapshots; never writes an externally opened file automatically. */
final class NoteDrafts {
    private static final Object lock=new Object();private static final Map<String,Long> generations=new LinkedHashMap<>(){protected boolean removeEldestEntry(Map.Entry<String,Long> entry){return size()>128;}};
    private static final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),new ThreadPoolExecutor.DiscardOldestPolicy());static{worker.allowCoreThreadTimeOut(true);}
    private static File folder(Context c){return new File(c.getFilesDir(),"note-drafts");}
    private static File file(Context c,String id){UUID.fromString(id);return new File(folder(c),id+".json");}
    static int count(Context c){File[] files=folder(c).listFiles((dir,name)->name.endsWith(".json"));return files==null?0:files.length;}
    static void queue(Context context,JSONObject snapshot,Consumer<String> result){Context c=context.getApplicationContext();String id=snapshot.optString("id");final long generation;synchronized(lock){generation=generations.getOrDefault(id,0L)+1;generations.put(id,generation);}String text=snapshot.toString();worker.execute(()->{File temp=null;try{byte[] bytes=text.getBytes(StandardCharsets.UTF_8);File target=file(c,id),directory=folder(c);if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建草稿目录");temp=new File(directory,id+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}synchronized(lock){if(generation!=generations.getOrDefault(id,0L)){temp.delete();return;}Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}if(result!=null)result.accept("草稿已在本机保留");}catch(Exception e){if(temp!=null)temp.delete();if(result!=null)result.accept("草稿未保存："+e.getMessage());}});}
    static void clear(Context c,String id){synchronized(lock){generations.put(id,generations.getOrDefault(id,0L)+1);file(c,id).delete();}}
    static void show(MainActivity a){LinearLayout page=a.fullPage("恢复笔记草稿",a::home);page.addView(Ui.text(a,"本机笔记草稿",23,true));page.addView(Ui.help(a,"草稿只保留在本机；手动保存后清除。原笔记已被其他设备更新时，恢复为新副本。"));File[] files=folder(a).listFiles((dir,name)->name.endsWith(".json"));if(files==null||files.length==0){page.addView(Ui.help(a,"没有未保存草稿"));return;}Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(File file:files){try{JSONObject snapshot=read(file);String title=snapshot.optString("title","未命名笔记"),id=snapshot.getString("id");LinearLayout row=Ui.column(a);row.addView(Ui.button(a,title.isEmpty()?"未命名草稿":title,false,()->{try{JSONObject draft=read(file),current=Data.get(a).find(id);if(current!=null&&!current.optString("revision").equals(draft.optString("revision"))){draft.put("id",UUID.randomUUID().toString()).put("revision",UUID.randomUUID().toString()).put("ancestors",new JSONArray()).put("title","恢复草稿 · "+draft.optString("title")).put("deleted",false);draft.remove("tf_id");draft.remove("tf_table");draft.remove("tf_raw");a.message("原笔记已更新，草稿恢复为独立副本");}a.edit(draft);}catch(Exception e){a.message(e.getMessage());}}));row.addView(Ui.button(a,"删除这份草稿",false,()->{clear(a,id);show(a);}));page.addView(row);}catch(Exception e){page.addView(Ui.help(a,"有一份草稿暂时无法读取，原文件保留"));}}
    }
    private static JSONObject read(File file)throws Exception{try(InputStream in=new FileInputStream(file)){return new JSONObject(MarkdownFiles.read(in));}}
}
