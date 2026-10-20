package app.dayflow;

import android.view.*;
import android.widget.*;
import android.text.*;
import com.google.android.material.textfield.TextInputEditText;
import io.noties.markwon.Markwon;

/** Markwon renders directly into TextView; notes need no WebView or remote assets. */
public final class NoteTools {
    public static void render(TextView view,String markdown){Markwon.create(view.getContext()).setMarkdown(view,markdown);}
    public static View toolbar(MainActivity a,TextInputEditText input){HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=Ui.row(a);scroll.addView(row);String[] labels={"标题","加粗","列表","引用","代码"},prefixes={"## ","**","- ","> ","`"},suffixes={"","**","","","`"};for(int i=0;i<labels.length;i++){int index=i;row.addView(Ui.button(a,labels[i],false,()->{Editable text=input.getText();int start=Math.max(0,input.getSelectionStart()),end=Math.max(start,input.getSelectionEnd());String selected=text.subSequence(start,end).toString();String insert=prefixes[index]+selected+suffixes[index];text.replace(start,end,insert);input.requestFocus();input.setSelection(start+prefixes[index].length()+(selected.isEmpty()?0:selected.length()));}),new LinearLayout.LayoutParams(-2,-2));}return scroll;}
}
