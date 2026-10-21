package app.dayflow;
import org.json.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Read-time expansion: no unbounded future rows or completion-dependent calendar holes. */
public final class Recurrences {
    public static boolean series(JSONObject r){return !r.optString("recurrence").isEmpty()&&!r.optString("recurrence").equals("none")&&r.optString("series_id").isEmpty();}
    public static boolean matches(JSONObject r,LocalDate day){if(r.optBoolean("workdays_only")&&!Holidays.working(day))return false;
        LocalDateTime start=PlanTime.start(r);if(start==null||day.isBefore(start.toLocalDate()))return false;
        String until=r.optString("recurrence_until");if(!until.isEmpty()&&day.isAfter(LocalDate.parse(until)))return false;
        switch(r.optString("recurrence")){
            case "daily":return true;
            case "weekly":JSONArray days=r.optJSONArray("weekdays");if(days==null||days.length()==0)return day.getDayOfWeek()==start.getDayOfWeek();for(int i=0;i<days.length();i++)if(days.optInt(i)==day.getDayOfWeek().getValue())return true;return false;
            case "monthly":return day.getDayOfMonth()==start.getDayOfMonth();
            default:return false;
        }
    }
    public static JSONObject instance(JSONObject series,LocalDate day)throws JSONException{
        JSONObject r=new JSONObject(series.toString());String source=series.optString("id");LocalDateTime before=PlanTime.start(series),after=day.atTime(before.toLocalTime());
        String id=UUID.nameUUIDFromBytes(("dayflow:occurrence:"+source+":"+day).getBytes(StandardCharsets.UTF_8)).toString();
        r.put("id",id).put("series_id",source).put("occurrence_date",day.toString()).put("completed","").put("status","planned").put("ancestors",new JSONArray());
        r.put("revision",UUID.nameUUIDFromBytes((series.optString("revision")+":"+day).getBytes(StandardCharsets.UTF_8)).toString());
        if(series.optString("start_at").isEmpty())r.put("due_date",day.toString());else{r.put("start_at",after.toString());if(!series.optString("end_at").isEmpty())r.put("end_at",after.plus(Duration.between(before,PlanTime.end(series))).toString());}
        r.remove("recurrence");return r;
    }
    public static void expand(Data db,JSONObject series,LocalDate from,LocalDate to,List<JSONObject> output,int limit)throws JSONException{
        LocalDateTime first=PlanTime.start(series);if(first==null||PlanTime.cancelled(series)||!series.optString("completed").isEmpty())return;
        LocalDate beginning=from.isBefore(first.toLocalDate())?first.toLocalDate():from;
        // Include an occurrence that starts before the visible range but overlaps it.
        long duration=Math.max(1,Duration.between(first,PlanTime.end(series)).toDays()+1);beginning=beginning.minusDays(Math.min(duration,366));if(beginning.isBefore(first.toLocalDate()))beginning=first.toLocalDate();
        LocalDate bounded=beginning.plusDays(732);if(to.isAfter(bounded))to=bounded;
        for(LocalDate day=beginning;day.isBefore(to)&&output.size()<limit;day=day.plusDays(1))if(matches(series,day)){
            JSONObject candidate=instance(series,day),saved=db.find(candidate.optString("id"));if(saved!=null)continue;
            if(PlanTime.end(candidate).isAfter(from.atStartOfDay()))output.add(candidate);
        }
    }
}
