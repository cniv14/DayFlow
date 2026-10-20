package app.dayflow;

import java.util.*;

/** Bounded Markdown snapshots; cursor/span state is reconstructed by the editor. */
final class NoteHistory {
    private final ArrayList<String> states=new ArrayList<>();private int position=-1;
    void capture(String value){if(position>=0&&states.get(position).equals(value))return;while(states.size()>position+1)states.remove(states.size()-1);states.add(value);position=states.size()-1;int total=0;for(String s:states)total+=s.length()*2;while(states.size()>1&&(states.size()>30||total>512*1024)){total-=states.remove(0).length()*2;position--;}}
    String undo(){return position>0?states.get(--position):null;}
    String redo(){return position+1<states.size()?states.get(++position):null;}
}
