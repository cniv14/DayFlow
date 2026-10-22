package app.dayflow.assistant;
import android.app.Dialog;import android.content.Context;import android.graphics.*;import android.os.Bundle;import android.view.*;import android.widget.*;import java.io.File;
/** In-app full screen image viewer with pinch and drag; no external gallery launch. */
final class PhotoPreview extends Dialog {
 private final File file;PhotoPreview(Context c,File file){super(c,android.R.style.Theme_Material_NoActionBar);this.file=file;}
 static void show(Context c,File file){if(file.isFile())new PhotoPreview(c,file).show();}
 protected void onCreate(Bundle state){super.onCreate(state);FrameLayout frame=new FrameLayout(getContext());frame.setBackgroundColor(Color.BLACK);Zoom image=new Zoom(getContext());image.setImageBitmap(BitmapFactory.decodeFile(file.getPath()));frame.addView(image,new FrameLayout.LayoutParams(-1,-1));ImageButton close=new ImageButton(getContext());close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);close.setContentDescription("关闭图片预览");close.setColorFilter(Color.WHITE);close.setBackgroundColor(Color.TRANSPARENT);int size=Math.round(52*getContext().getResources().getDisplayMetrics().density);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(size,size,Gravity.TOP|Gravity.END);frame.addView(close,p);close.setOnClickListener(v->dismiss());setContentView(frame);getWindow().setLayout(-1,-1);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);}
 static final class Zoom extends androidx.appcompat.widget.AppCompatImageView {
  final Matrix transform=new Matrix();final ScaleGestureDetector scale;float x,y;
  Zoom(Context c){super(c);setScaleType(ScaleType.MATRIX);scale=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float[] values=new float[9];transform.getValues(values);float factor=d.getScaleFactor();if(values[0]*factor<.05f||values[0]*factor>8f)return false;transform.postScale(factor,factor,d.getFocusX(),d.getFocusY());setImageMatrix(transform);return true;}});}
  protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(getDrawable()!=null){transform.setRectToRect(new RectF(0,0,getDrawable().getIntrinsicWidth(),getDrawable().getIntrinsicHeight()),new RectF(0,0,w,h),Matrix.ScaleToFit.CENTER);setImageMatrix(transform);}}
  public boolean onTouchEvent(android.view.MotionEvent e){scale.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();}else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!scale.isInProgress()&&e.getPointerCount()==1){transform.postTranslate(e.getX()-x,e.getY()-y);setImageMatrix(transform);x=e.getX();y=e.getY();}return true;}
 }
}
