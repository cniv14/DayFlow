package app.dayflow;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.View;

/** Bounded bitmap and reusable paints. Animation only while foreground and visible. */
public final class Backdrop extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF target=new RectF();
    private Bitmap bitmap;private boolean active;private long start;
    public Backdrop(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
    public void load(){
        if(bitmap!=null){bitmap.recycle();bitmap=null;}
        java.io.File file=new java.io.File(getContext().getFilesDir(),"wallpaper.jpg");
        if(file.exists())bitmap=BitmapFactory.decodeFile(file.getAbsolutePath());
        boolean blur=getContext().getSharedPreferences("settings",0).getBoolean("blur",true);
        if(Build.VERSION.SDK_INT>=31)setRenderEffect(blur?RenderEffect.createBlurEffect(18,18,Shader.TileMode.CLAMP):null);
        invalidate();
    }
    public void active(boolean value){active=value;start=SystemClock.uptimeMillis();if(value)invalidate();}
    protected void onDraw(Canvas canvas){super.onDraw(canvas);int w=getWidth(),h=getHeight();canvas.drawColor(Color.rgb(229,238,229));
        if(bitmap!=null){paint.setShader(null);float scale=Math.max((float)w/bitmap.getWidth(),(float)h/bitmap.getHeight());float bw=bitmap.getWidth()*scale,bh=bitmap.getHeight()*scale;target.set((w-bw)/2,(h-bh)/2,(w+bw)/2,(h+bh)/2);canvas.drawBitmap(bitmap,null,target,paint);}
        boolean moving=active&&getContext().getSharedPreferences("settings",0).getBoolean("motion",false)&&!((PowerManager)getContext().getSystemService(Context.POWER_SERVICE)).isPowerSaveMode();
        float t=moving?(SystemClock.uptimeMillis()-start)/12000f:0;
        paint.setColor(0x556CBFA8);canvas.drawCircle(w*(.18f+.12f*(float)Math.sin(t)),h*.25f,w*.7f,paint);
        paint.setColor(0x55E6C47A);canvas.drawCircle(w*.95f,h*(.7f+.12f*(float)Math.cos(t)),w*.8f,paint);
        paint.setColor(0x33FFFFFF);canvas.drawCircle(w*.5f,h*.5f,w*.6f,paint);
        if(moving)postInvalidateDelayed(33);
    }
    protected void onDetachedFromWindow(){active=false;super.onDetachedFromWindow();}
    public void release(){active=false;if(Build.VERSION.SDK_INT>=31)setRenderEffect(null);if(bitmap!=null){bitmap.recycle();bitmap=null;}}
}
