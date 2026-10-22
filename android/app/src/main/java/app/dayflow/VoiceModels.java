package app.dayflow;
import android.content.Context;import org.json.*;import java.io.*;import java.net.HttpURLConnection;import java.nio.file.*;import java.security.MessageDigest;
/** Stream an optional signed model from the same authenticated/public source as all other updates. */
public final class VoiceModels {
 public static String download(Context c)throws Exception{
  JSONObject p=Updates.signedPayload(c,UpdateSources.json(c,"voice-model.json"));long size=p.getLong("size");if(p.getInt("schema")!=1||!"android-voice-model".equals(p.getString("platform"))||size<1024||size>512L*1024*1024||!p.getString("sha256").matches("[a-f0-9]{64}"))throw new IOException("语音模型签名清单无效");
  File target=new File(c.getFilesDir(),"voice/ggml-model.bin"),temp=new File(c.getFilesDir(),"voice/download.part");target.getParentFile().mkdirs();if(target.getParentFile().getUsableSpace()<size)throw new IOException("存储空间不足，保留原模型");HttpURLConnection connection=UpdateSources.open(c,"voice-model.bin");MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;
  try(InputStream in=connection.getInputStream();OutputStream out=new FileOutputStream(temp)){byte[] block=new byte[32768];int n;while((n=in.read(block))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("下载已取消");total+=n;if(total>size)throw new IOException("语音模型长度不符");hash.update(block,0,n);out.write(block,0,n);}}catch(Exception e){temp.delete();throw e;}finally{connection.disconnect();}
  StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(total!=size||!digest.toString().equals(p.getString("sha256"))){temp.delete();throw new IOException("语音模型完整性校验失败，保留原模型");}
  try{try(RandomAccessFile in=new RandomAccessFile(temp,"r")){if(in.readInt()!=0x6c6d6767)throw new IOException("不是支持的Whisper GGML模型");}Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){temp.delete();throw e;}return "语音模型已下载并校验，无需安装";
 }
}
