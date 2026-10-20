package app.dayflow;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.os.*;
import android.print.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;

/** System print/save-to-PDF integration, created only on explicit request. */
public final class CalendarPrint {
    public static void show(MainActivity a,LocalDate from,LocalDate to){try{JSONArray records=Data.get(a).scheduled(from,to,false);List<String> lines=new ArrayList<>();lines.add("日栖 · "+from+" 至 "+to.minusDays(1));for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);lines.add(PlanTime.label(r));lines.add((r.optString("completed").isEmpty()?"○ ":"✓ ")+r.optString("title"));if(!r.optString("location").isEmpty())lines.add("地点："+r.optString("location"));lines.add("");}if(records.length()==0)lines.add("此时间范围内暂无安排");PrintManager manager=(PrintManager)a.getSystemService(Context.PRINT_SERVICE);manager.print("日栖日程-"+from,new PrintDocumentAdapter(){int pages;List<String> wrapped;
        public void onLayout(PrintAttributes old,PrintAttributes next,CancellationSignal signal,LayoutResultCallback callback,Bundle extra){if(signal.isCanceled()){callback.onLayoutCancelled();return;}Paint p=new Paint(3);p.setTextSize(12);wrapped=new ArrayList<>();for(String line:lines){if(line.isEmpty()){wrapped.add("");continue;}int pos=0;while(pos<line.length()){int count=p.breakText(line,pos,line.length(),true,515,null);if(count<=0)count=1;wrapped.add(line.substring(pos,pos+count));pos+=count;}}pages=Math.max(1,(wrapped.size()+34)/35);callback.onLayoutFinished(new PrintDocumentInfo.Builder("日栖日程.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(),true);}
        public void onWrite(PageRange[] ranges,ParcelFileDescriptor target,CancellationSignal signal,WriteResultCallback callback){PdfDocument pdf=new PdfDocument();try(OutputStream output=new FileOutputStream(target.getFileDescriptor())){List<PageRange> written=new ArrayList<>();for(int page=0;page<pages;page++){if(signal.isCanceled()){callback.onWriteCancelled();return;}boolean wanted=false;for(PageRange range:ranges)if(page>=range.getStart()&&page<=range.getEnd())wanted=true;if(!wanted)continue;PdfDocument.Page sheet=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,page+1).create());Canvas canvas=sheet.getCanvas();Paint paint=new Paint(3);paint.setColor(Color.BLACK);paint.setTextSize(12);for(int line=page*35;line<Math.min(wrapped.size(),(page+1)*35);line++)canvas.drawText(wrapped.get(line),40,50+(line-page*35)*21,paint);paint.setTextSize(10);canvas.drawText((page+1)+" / "+pages,280,810,paint);pdf.finishPage(sheet);written.add(new PageRange(page,page));}pdf.writeTo(output);callback.onWriteFinished(written.toArray(new PageRange[0]));}catch(Exception e){callback.onWriteFailed(e.getMessage());}finally{pdf.close();}}
    },new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());}catch(Exception e){a.message(e.getMessage());}}
}
