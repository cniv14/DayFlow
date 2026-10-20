package app.dayflow;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;

/** Bounded bitmap and reusable paints. Animation only while foreground and visible. */
public final class Backdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF target=new RectF();
    private Bitmap bitmap;private boolean active,motion,powerSave;private long start,wallpaperStamp=-1,powerChecked;private int surface,accent,opacity;private final SharedPreferences prefs;private final PowerManager power;
    public Backdrop(Context c){super(c);prefs=c.getSharedPreferences("settings",0);power=(PowerManager)c.getSystemService(Context.POWER_SERVICE);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
    public void load(){
        surface=Ui.surface(getContext());accent=Ui.primary(getContext());
        java.io.File file=new java.io.File(getContext().getFilesDir(),"wallpaper.jpg");long stamp=file.exists()?file.lastModified()^file.length():-1;
        if(stamp!=wallpaperStamp){if(bitmap!=null){bitmap.recycle();bitmap=null;}if(file.exists()){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getAbsolutePath(),bounds);if(bounds.outWidth>0&&bounds.outHeight>0){BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1400)options.inSampleSize*=2;bitmap=BitmapFactory.decodeFile(file.getAbsolutePath(),options);}}wallpaperStamp=stamp;}
        motion=prefs.getBoolean("motion",false);opacity=prefs.getInt("opacity",86);boolean blur=prefs.getBoolean("blur",true);
        if(Build.VERSION.SDK_INT>=31)setRenderEffect(blur?RenderEffect.createBlurEffect(18,18,Shader.TileMode.CLAMP):null);
        invalidate();
    }
    public void active(boolean value){active=value;start=SystemClock.uptimeMillis();if(value)invalidate();}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);int w=getWidth(),h=getHeight();canvas.drawColor(surface);
        if(bitmap!=null){paint.setShader(null);float scale=Math.max((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;target.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);canvas.drawBitmap(bitmap,null,target,paint);}
        boolean enabled=motion;if(bitmap!=null){paint.setColor(Color.argb(opacity*255/100,Color.red(surface),Color.green(surface),Color.blue(surface)));canvas.drawRect(0,0,w,h,paint);}if(!enabled)return;
        long now=SystemClock.uptimeMillis();if(active&&now-powerChecked>1000){powerSave=power.isPowerSaveMode();powerChecked=now;}boolean moving=active&&!powerSave;
        float t=moving?(SystemClock.uptimeMillis()-start)/12000f:0;
        paint.setColor(Color.argb(18,Color.red(accent),Color.green(accent),Color.blue(accent)));canvas.drawCircle(w*(.18f+.12f*(float)Math.sin(t)),h*.25f,w*.7f,paint);canvas.drawCircle(w*.95f,h*(.7f+.12f*(float)Math.cos(t)),w*.8f,paint);
        if(moving)postInvalidateDelayed(33);
    }
    protected void onDetachedFromWindow(){active=false;super.onDetachedFromWindow();}
    public void release(){active=false;if(Build.VERSION.SDK_INT>=31)setRenderEffect(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
