package app.dayflow;

import android.content.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** System .md associations map private document URIs to stable local note IDs. */
final class MarkdownDocuments {
    static String name(Context c,Uri uri){String name=null;try(android.database.Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(Exception ignored){}if(name==null||name.isEmpty())name=uri.getLastPathSegment();return name==null?"笔记.md":name;}
    static boolean isMarkdown(Context c,Uri uri){String mime=c.getContentResolver().getType(uri);return "text/markdown".equals(mime)||"text/x-markdown".equals(mime)||"text/x-web-markdown".equals(mime)||name(c,uri).toLowerCase(java.util.Locale.ROOT).endsWith(".md");}
    private static String id(Uri uri){return UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8)).toString();}
    static JSONObject open(Context c,Uri uri)throws Exception{
        if(!java.util.Arrays.asList("content","file").contains(uri.getScheme()))throw new IOException("仅支持打开本机 Markdown 文件");if(!isMarkdown(c,uri))throw new IOException("请选择 .md 文件");String text;try(InputStream in=c.getContentResolver().openInputStream(uri)){text=MarkdownFiles.read(in);}if(text.startsWith("\uFEFF"))text=text.substring(1);String filename=name(c,uri);if(filename.toLowerCase(java.util.Locale.ROOT).endsWith(".md"))filename=filename.substring(0,filename.length()-3);
        SharedPreferences refs=c.getSharedPreferences("md_documents",0);Data db=Data.get(c);String key=id(uri);JSONObject existing=db.find(refs.getString(key,""));String hash=UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8)).toString();
        if(existing!=null&&!existing.optBoolean("deleted")){if(existing.optString("content").equals(text))return existing;String localHash=UUID.nameUUIDFromBytes(existing.optString("content").getBytes(StandardCharsets.UTF_8)).toString();if(!localHash.equals(refs.getString(key+"_hash","")))existing=null;}
        JSONObject record=existing==null?Data.make("note",filename):new JSONObject(existing.toString());record.put("content",text).put("content_format","markdown");Data.validate(record);db.save(record);refs.edit().putString(key,record.getString("id")).putString(key+"_hash",hash).apply();return record;
    }
    static void wrote(Context c,Uri uri,JSONObject record)throws JSONException{c.getSharedPreferences("md_documents",0).edit().putString(id(uri),record.getString("id")).putString(id(uri)+"_hash",UUID.nameUUIDFromBytes(record.optString("content").getBytes(StandardCharsets.UTF_8)).toString()).apply();}
}
