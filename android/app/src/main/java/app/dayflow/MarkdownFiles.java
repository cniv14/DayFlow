package app.dayflow;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Portable UTF-8 Markdown files; SQLite retains sync IDs, revisions and flags. */
public final class MarkdownFiles {
    public static File file(Context c,String id){UUID.fromString(id);return new File(new File(c.getFilesDir(),"notes"),id+".md");}
    public static String markdown(JSONObject r){String text=r.optString("content");String image=r.optString("image_base64");if(!image.isEmpty())text+="\n\n![笔记附图](data:image/jpeg;base64,"+image+")\n";return text;}
    public static String read(InputStream input)throws Exception{if(input==null)throw new IOException("无法读取文件");Reader reader=new InputStreamReader(input,StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT));try{StringBuilder out=new StringBuilder();char[] buffer=new char[8192];int count;while((count=reader.read(buffer))!=-1)out.append(buffer,0,count);return out.toString();}catch(OutOfMemoryError e){throw new IOException("设备可用内存不足，原文件未修改");}}
    public static void update(Context c,JSONObject r)throws IOException,JSONException{
        File target=file(c,r.getString("id"));AtomicFile atomic=new AtomicFile(target);
        if(!"note".equals(r.optString("kind"))||r.optBoolean("deleted")){atomic.delete();return;}
        if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("无法创建笔记目录");
        FileOutputStream out=null;
        try{out=atomic.startWrite();Writer writer=new OutputStreamWriter(out,StandardCharsets.UTF_8);writer.write(markdown(r));writer.flush();atomic.finishWrite(out);}catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}
    }
    public static String filename(JSONObject r){String title=r.optString("title",java.time.LocalDate.now().toString()).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();if(title.isEmpty())title=java.time.LocalDate.now().toString();if(title.length()>80)title=title.substring(0,80);return title.toLowerCase(java.util.Locale.ROOT).endsWith(".md")?title:title+".md";}
}
