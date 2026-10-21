package app.dayflow;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.LruCache;
import io.noties.markwon.image.*;
import java.util.*;
import java.io.*;
import java.net.*;

/** Bounded memory-only image cache. Never forwards AI keys, cookies or sync credentials. */
final class NoteImages extends SchemeHandler {
 private final Context context;private static final LruCache<String,Bitmap> cache=new LruCache<>(8*1024*1024){protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount()+256;}};
 NoteImages(Context c){context=c.getApplicationContext();}
 public Collection<String> supportedSchemes(){return Arrays.asList("data","https","http","content","file");}
 public ImageItem handle(String raw,Uri uri){try{String key=android.util.Base64.encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)),2);Bitmap bitmap=cache.get(key);if(bitmap==null){byte[] bytes;if(uri.getScheme().equals("data")){int split=raw.indexOf(',');if(split<0||split>200||raw.length()>6*1024*1024)throw new IOException("图片数据过大");bytes=raw.substring(0,split).contains(";base64")?android.util.Base64.decode(raw.substring(split+1),android.util.Base64.DEFAULT):Uri.decode(raw.substring(split+1)).getBytes(java.nio.charset.StandardCharsets.UTF_8);}else if(uri.getScheme().equals("https")||uri.getScheme().equals("http")){URL url=new URL(Network.endpoint(raw,true));bytes=download(url);}else{try(InputStream in=context.getContentResolver().openInputStream(uri)){bytes=read(in);}}if(bytes.length>4*1024*1024)throw new IOException("图片超过4MiB");BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<=0||options.outHeight<=0)throw new IOException("不支持的图片格式");options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1400||(long)options.outWidth*options.outHeight/options.inSampleSize/options.inSampleSize>2000000)options.inSampleSize*=2;options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(bitmap==null)throw new IOException("图片解码失败");cache.put(key,bitmap);}BitmapDrawable drawable=new BitmapDrawable(context.getResources(),bitmap);drawable.setBounds(0,0,bitmap.getWidth(),bitmap.getHeight());return ImageItem.withResult(drawable);}catch(Exception e){throw new IllegalArgumentException("图片未加载："+e.getMessage(),e);}}
 private byte[] download(URL url)throws Exception{for(int redirects=0;redirects<4;redirects++){HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);try{int status=connection.getResponseCode();if(status>=300&&status<400){URL next=new URL(url,connection.getHeaderField("Location"));Network.endpoint(next.toString(),true);if(!next.getProtocol().equals("https"))throw new IOException("图片重定向需HTTPS");url=next;continue;}if(status!=200)throw new IOException("图片HTTP "+status);try(InputStream in=connection.getInputStream()){return read(in);}}finally{connection.disconnect();}}throw new IOException("图片重定向过多");}
 private static byte[] read(InputStream in)throws IOException{if(in==null)throw new IOException("无法读取图片");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>4*1024*1024)throw new IOException("图片超过4MiB");out.write(buffer,0,count);}return out.toByteArray();}
 static void clearCache(){cache.evictAll();}
}
