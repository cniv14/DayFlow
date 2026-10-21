package app.dayflow;
import biweekly.*;
import biweekly.component.VEvent;
import biweekly.util.ICalDate;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import org.json.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** biweekly handles folding, timezone definitions and EXDATE/RDATE; expansion stays bounded. */
final class IcsImports {
 static JSONArray parse(String text)throws Exception{List<ICalendar> calendars=Biweekly.parse(text).all();JSONArray out=new JSONArray();Set<String> seen=new HashSet<>();int events=0;for(ICalendar calendar:calendars)for(VEvent event:calendar.getEvents()){
  if(++events>1000)throw new Exception("ICS原始事项超过1000项，请分卷导入");if(event.getDateStart()==null)continue;if(event.getRecurrenceId()!=null)throw new Exception("ICS含单次覆盖，请先导出展开的日程，避免丢失修改");ICalDate initial=event.getDateStart().getValue();boolean allDay=!initial.hasTime(),repeats=event.getRecurrenceRule()!=null||!event.getRecurrenceDates().isEmpty();long duration=0;boolean ended=event.getDateEnd()!=null||event.getDuration()!=null;if(event.getDateEnd()!=null)duration=event.getDateEnd().getValue().getTime()-initial.getTime();else if(event.getDuration()!=null)duration=event.getDuration().getValue().toMillis();else if(allDay){duration=86400000;ended=true;}if(duration<0)throw new Exception("ICS结束早于开始");String title=event.getSummary()==null?"未命名日程":event.getSummary().getValue(),uid=event.getUid()==null?title+":"+initial.getTime():event.getUid().getValue();DateIterator dates=event.getDateIterator(TimeZone.getDefault());Instant window=LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant(),until=LocalDate.now().plusDays(367).atStartOfDay(ZoneId.systemDefault()).toInstant();if(repeats)dates.advanceTo(Date.from(window));int occurrences=0;
  while(dates.hasNext()){if(++occurrences>4000)throw new Exception("ICS重复展开超过4000项");Date date=dates.next();if(repeats&&date.toInstant().isAfter(until))break;LocalDateTime start=date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();if(allDay)start=LocalDateTime.of(start.toLocalDate(),LocalTime.MIDNIGHT);String stable=UUID.nameUUIDFromBytes(("ics:"+uid+":"+start).getBytes(StandardCharsets.UTF_8)).toString();if(!seen.add(stable))continue;JSONObject r=Data.make("event",title).put("id",stable).put("start_at",start.toString()).put("end_at",ended?start.plusSeconds(duration/1000).toString():"").put("all_day",allDay).put("source","ICS").put("ics_uid",uid).put("content",event.getDescription()==null?"":event.getDescription().getValue()).put("location",event.getLocation()==null?"":event.getLocation().getValue());if(event.getStatus()!=null&&"CANCELLED".equalsIgnoreCase(event.getStatus().getValue()))r.put("status","cancelled");Data.validate(r);out.put(r);if(out.length()>4000)throw new Exception("单次最多导入4000项");if(!repeats)break;}
 }if(out.length()==0)throw new Exception("ICS在导入范围内没有事项");return out;}
}
