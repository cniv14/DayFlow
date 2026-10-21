package app.dayflow;
import android.net.Uri;
import android.graphics.Bitmap;
import android.widget.*;
import java.io.File;
import java.util.function.Consumer;
import com.canhub.cropper.CropImageView;

/** Native async crop/rotation before either cloud or offline recognition. */
final class ImageTools {
 static void crop(MainActivity a,Uri input,Consumer<Uri> next){LinearLayout page=a.editorPage("裁剪截图",a::home);TextView help=Ui.help(a,"裁剪需要识别的区域，可旋转；裁剪后再决定是否发送AI。");help.setPadding(Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16),0);page.addView(help);CropImageView crop=new CropImageView(a);page.addView(crop,new LinearLayout.LayoutParams(-1,0,1));LinearLayout buttons=Ui.row(a);page.addView(buttons);boolean[] working={false};buttons.addView(Ui.button(a,"旋转",false,()->crop.rotateImage(90)),new LinearLayout.LayoutParams(0,-2,1));buttons.addView(Ui.button(a,"使用整图",false,()->{if(!working[0]){crop.clearImage();next.accept(input);}}),new LinearLayout.LayoutParams(0,-2,1));buttons.addView(Ui.button(a,"完成裁剪",true,()->{if(working[0])return;try{File dir=new File(a.getCacheDir(),"shared-images");if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("无法创建截图缓存");File[] stale=dir.listFiles((parent,name)->name.startsWith("crop-")&&name.endsWith(".jpg"));if(stale!=null)for(File old:stale)if(System.currentTimeMillis()-old.lastModified()>24*60*60*1000L)old.delete();File file=new File(dir,"crop-"+java.util.UUID.randomUUID()+".jpg");Uri output=Uri.fromFile(file);working[0]=true;crop.croppedImageAsync(Bitmap.CompressFormat.JPEG,90,2400,2400,CropImageView.RequestSizeOptions.RESIZE_INSIDE,output);}catch(Exception e){working[0]=false;a.message(e.getMessage());}}),new LinearLayout.LayoutParams(0,-2,1));crop.setOnCropImageCompleteListener((view,result)->{working[0]=false;if(!crop.isAttachedToWindow())return;if(result.getError()!=null||result.getUriContent()==null){a.message("无法裁剪，请重试或使用整图");return;}Uri output=result.getUriContent();crop.clearImage();next.accept(output);});crop.setOnSetImageUriCompleteListener((view,uri,error)->{if(error!=null)a.message("无法读取截图："+error.getMessage());});crop.setImageUriAsync(input);}
}
