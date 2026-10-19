package app.dayflow;
import android.content.Context;
import org.json.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class TaskActions {
    public static void complete(Context c,JSONObject record)throws Exception{
        Data db=Data.get(c);JSONObject r=new JSONObject(record.toString());if("waiting".equals(r.optString("activation")))throw new Exception("请先完成前置任务");boolean completed=r.optString("completed").isEmpty();r.put("completed",completed?Instant.now().toString():"");db.save(r);if(!completed)return;
        String rule=r.optString("repeat");if(Arrays.asList("daily","weekly","monthly").contains(rule)&&!r.optString("start_at").isEmpty()){
            String id=UUID.nameUUIDFromBytes((r.getString("id")+":next").getBytes(StandardCharsets.UTF_8)).toString();if(db.find(id)==null){LocalDateTime before=LocalDateTime.parse(r.getString("start_at")),after=rule.equals("daily")?before.plusDays(1):rule.equals("weekly")?before.plusWeeks(1):before.plusMonths(1);
                JSONObject next=new JSONObject(r.toString()).put("id",id).put("revision",UUID.randomUUID().toString()).put("ancestors",new JSONArray()).put("completed","").put("start_at",after.toString());if(!r.optString("end_at").isEmpty())next.put("end_at",after.plus(Duration.between(before,LocalDateTime.parse(r.getString("end_at")))).toString());db.save(next);
            }
        }
        JSONArray all=db.undated();for(int i=0;i<all.length();i++){JSONObject child=all.getJSONObject(i);if("waiting".equals(child.optString("activation"))&&r.getString("id").equals(child.optString("trigger_after"))){child.put("activation","active").put("start_at",LocalDateTime.now().plusDays(child.optInt("offset_days")).withSecond(0).withNano(0).toString());db.save(child);}}
    }
}
