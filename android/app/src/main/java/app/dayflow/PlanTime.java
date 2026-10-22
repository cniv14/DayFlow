package app.dayflow;
import org.json.*;
import java.time.*;

/** Date-only deadlines do not become fabricated appointments. */
public final class PlanTime {
    public static LocalDateTime start(JSONObject r){String value=r.optString("start_at");if(!value.isEmpty())return LocalDateTime.parse(value);value=r.optString("due_date");return value.isEmpty()?null:LocalDate.parse(value).atTime(9,0);}
    public static LocalDateTime end(JSONObject r){String value=r.optString("end_at");if(!value.isEmpty())return LocalDateTime.parse(value);LocalDateTime start=start(r);return start==null?null:r.optString("start_at").isEmpty()?start.toLocalDate().plusDays(1).atStartOfDay():start.plusHours(1);}
    public static String label(JSONObject r){if(r.optString("start_at").isEmpty())return r.optString("due_date").isEmpty()?r.optString("time_hint","时间待定"):"截止 "+r.optString("due_date");return r.optBoolean("all_day")?r.optString("start_at").substring(0,10)+(r.optString("end_at").isEmpty()||!LocalDateTime.parse(r.optString("end_at")).toLocalDate().isAfter(LocalDateTime.parse(r.optString("start_at")).toLocalDate().plusDays(1))?"":" 至 "+LocalDateTime.parse(r.optString("end_at")).toLocalDate().minusDays(1))+" · 全天":r.optString("start_at").replace('T',' ');}
    public static String dailyLabel(JSONObject r,LocalDate day){
        String label;
        if(r.optString("start_at").isEmpty()){label=r.optString("due_date").isEmpty()?r.optString("time_hint"):"截止 "+r.optString("due_date");if(label.isEmpty())label="时间待定";}
        else {LocalDateTime start=start(r);label=r.optBoolean("all_day")?"全天":start.format(java.time.format.DateTimeFormatter.ofPattern(start.toLocalDate().equals(day)?"HH:mm":"M/d HH:mm"));if(!r.optBoolean("all_day")&&!r.optString("end_at").isEmpty()){LocalDateTime end=LocalDateTime.parse(r.optString("end_at"));label+=" – "+end.format(java.time.format.DateTimeFormatter.ofPattern(end.toLocalDate().equals(day)?"HH:mm":"M/d HH:mm"));}}
        return ("project".equals(r.optString("kind"))?"长任务 · ":"course".equals(r.optString("kind"))?"课程 · ":"")+label;
    }
    public static boolean cancelled(JSONObject r){return "cancelled".equals(r.optString("status"));}
}
