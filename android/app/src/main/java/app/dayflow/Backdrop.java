package app.dayflow;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;

/** Bounded bitmap and reusable paints. Animation only while foreground and visible. */
public final class Backdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF target=new RectF();
    private Bitmap bitmap;private boolean active;private long start;private int surface,accent;
    public Backdrop(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
    public void load(){
        surface=Ui.surface(getContext());accent=Ui.primary(getContext());
        if(bitmap!=null){bitmap.recycle();bitmap=null;}
        java.io.File file=new java.io.File(getContext().getFilesDir(),"wallpaper.jpg");
        if(file.exists())bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
        boolean blur=getContext().getSharedPreferences("settings",0).getBoolean("blur",true);
        if(Build.VERSION.SDK_INT>=31)setRenderEffect(blur?RenderEffect.createBlurEffect(18,18,Shader.TileMode.CLAMP):null);
        invalidate();
    }
    public void active(boolean value){active=value;start=SystemClock.uptimeMillis();if(value)invalidate();}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);int w=getWidth(),h=getHeight();canvas.drawColor(surface);
        if(bitmap!=null){paint.setShader(null);float scale=Math.max((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;target.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);canvas.drawBitmap(bitmap,null,target,paint);}
        boolean enabled=getContext().getSharedPreferences("settings",0).getBoolean("motion",false);if(bitmap!=null){paint.setColor(Color.argb(getContext().getSharedPreferences("settings",0).getInt("opacity",86)*255/100,Color.red(surface),Color.green(surface),Color.blue(surface)));canvas.drawRect(0,0,w,h,paint);}if(!enabled)return;
        boolean moving=active&&!((PowerManager)getContext().getSystemService(Context.POWER_SERVICE)).isPowerSaveMode();
        float t=moving?(SystemClock.uptimeMillis()-start)/12000f:0;
        paint.setColor(Color.argb(18,Color.red(accent),Color.green(accent),Color.blue(accent)));canvas.drawCircle(w*(.18f+.12f*(float)Math.sin(t)),h*.25f,w*.7f,paint);canvas.drawCircle(w*.95f,h*(.7f+.12f*(float)Math.cos(t)),w*.8f,paint);
        if(moving)postInvalidateDelayed(33);
    }
    protected void onDetachedFromWindow(){active=false;super.onDetachedFromWindow();}
    public void release(){active=false;if(Build.VERSION.SDK_INT>=31)setRenderEffect(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
