package app.dayflow;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Imports {
    public static List<List<String>> csv(String text) throws Exception {
        List<List<String>> result=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean quote=false;
        text=text.replace("\uFEFF","");
        for(int i=0;i<text.length();i++){char ch=text.charAt(i);
            if(ch=='"'){if(quote&&i+1<text.length()&&text.charAt(i+1)=='"'){cell.append('"');i++;}else quote=!quote;}
            else if(!quote&&(ch==','||ch=='\n')){row.add(cell.toString().trim());cell.setLength(0);if(ch=='\n'){if(row.stream().anyMatch(s->!s.trim().isEmpty()))result.add(row);row=new ArrayList<>();}}
            else if(ch!='\r'||quote)cell.append(ch);
        }if(quote)throw new Exception("CSV 引号未闭合");
        row.add(cell.toString().trim());if(row.stream().anyMatch(s->!s.trim().isEmpty()))result.add(row);return result;
    }
    public static JSONArray timetable(String text) throws Exception {
        if(text.trim().startsWith("BEGIN:VCALENDAR"))return ics(text);
        List<List<String>> rows=csv(text);if(rows.size()<2)throw new Exception("课表没有数据行");
        List<String> header=rows.get(0);String[] required={"title","weekday","start_time","end_time","start_date","end_date","weeks","location"};
        for(String key:required)if(!header.contains(key))throw new Exception("缺少课表列："+key);
        JSONArray records=new JSONArray();
        for(int i=1;i<rows.size();i++){
            List<String> values=rows.get(i);if(values.size()!=header.size())throw new Exception("第"+(i+1)+"行列数不匹配");
            Map<String,String> r=new HashMap<>();for(int j=0;j<header.size();j++)r.put(header.get(j),values.get(j));
            LocalDate from=LocalDate.parse(r.get("start_date")),to=LocalDate.parse(r.get("end_date"));
            if(to.isBefore(from)||to.isAfter(from.plusDays(370)))throw new Exception("课表日期范围需在一年以内");
            int weekday=Integer.parseInt(r.get("weekday"));if(weekday<1||weekday>7)throw new Exception("weekday 必须为1–7");
            LocalTime start=LocalTime.parse(r.get("start_time")),end=LocalTime.parse(r.get("end_time"));if(!end.isAfter(start))throw new Exception("下课时间须晚于上课时间");
            String weeks=r.get("weeks");if(!Arrays.asList("all","odd","even","全部","单周","双周").contains(weeks))throw new Exception("weeks 需为 all/odd/even");
            LocalDate monday=from.minusDays(from.getDayOfWeek().getValue()-1);
            for(LocalDate d=from;!d.isAfter(to);d=d.plusDays(1)){
                int week=(int)(java.time.temporal.ChronoUnit.DAYS.between(monday,d)/7)+1;
                if(d.getDayOfWeek().getValue()!=weekday||((weeks.equals("odd")||weeks.equals("单周"))&&week%2==0)||((weeks.equals("even")||weeks.equals("双周"))&&week%2!=0))continue;
                JSONObject event=Data.make("course",r.get("title")).put("start_at",d.atTime(start).toString()).put("end_at",d.atTime(end).toString()).put("location",r.get("location")).put("source","timetable");
                stable(event,"csv:"+r.get("title")+":"+d+":"+start+":"+r.get("location"));records.put(event);
                if(records.length()>4000)throw new Exception("单次课表最多4000节");
            }
        }return records;
    }
    private static void stable(JSONObject r,String seed) throws Exception {
        r.put("id",UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString());
    }
    private static LocalDateTime icalTime(String key,String value) throws Exception {
        if(value.matches("\\d{8}"))return LocalDate.parse(value,DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay();
        DateTimeFormatter f=DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        if(value.endsWith("Z"))return LocalDateTime.parse(value.substring(0,value.length()-1),f).atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime result=LocalDateTime.parse(value,f);
        int i=key.indexOf("TZID=");if(i>=0){String zone=key.substring(i+5).split(";")[0];result=result.atZone(ZoneId.of(zone)).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();}return result;
    }
    public static JSONArray ics(String text) throws Exception {
        JSONArray result=new JSONArray();Map<String,String> event=null;String startKey="",endKey="";
        for(String line:text.replace("\r\n", "\n").replaceAll("\n[ \\t]","").split("\n")){
            if(line.equals("BEGIN:VEVENT")){event=new HashMap<>();startKey="";endKey="";continue;}
            if(event==null)continue;
            if(line.equals("END:VEVENT")){
                if(event.containsKey("EXDATE")||event.containsKey("RDATE")||event.containsKey("RECURRENCE-ID"))throw new Exception("暂不支持带例外日期的 ICS，请先导出展开后的日程");
                LocalDateTime start=icalTime(startKey,event.get("DTSTART")),end=event.containsKey("DTEND")?icalTime(endKey,event.get("DTEND")):start.plusHours(1);
                String rule=event.getOrDefault("RRULE","");int max=1,interval=1;String freq="";LocalDateTime until=start.plusDays(180);
                if(!rule.isEmpty()){
                    Map<String,String> props=new HashMap<>();for(String part:rule.split(";")){String[] pair=part.split("=",2);props.put(pair[0],pair[1]);}
                    for(String key:props.keySet())if(!Arrays.asList("FREQ","INTERVAL","COUNT","UNTIL").contains(key))throw new Exception("暂不支持 ICS 重复规则 "+key+"，请使用 CSV 课表");
                    freq=props.getOrDefault("FREQ","");if(!freq.equals("DAILY")&&!freq.equals("WEEKLY"))throw new Exception("ICS 仅支持每日／每周重复");
                    interval=Integer.parseInt(props.getOrDefault("INTERVAL","1"));max=Integer.parseInt(props.getOrDefault("COUNT","400"));
                    if(interval<1||max<1||max>400)throw new Exception("ICS 重复次数需为1–400");
                    if(props.containsKey("UNTIL"))until=icalTime(startKey,props.get("UNTIL"));
                }
                for(int i=0;i<max;i++){
                    long days=(long)i*interval*(freq.equals("WEEKLY")?7:1);LocalDateTime at=start.plusDays(days);if(at.isAfter(until))break;
                    JSONObject r=Data.make("event",event.getOrDefault("SUMMARY","未命名日程")).put("start_at",at.toString()).put("end_at",end.plusDays(days).toString())
                        .put("content",event.getOrDefault("DESCRIPTION","")).put("location",event.getOrDefault("LOCATION","")).put("source","ics");
                    stable(r,"ics:"+event.getOrDefault("UID",r.getString("title"))+":"+at);result.put(r);
                    if(result.length()>4000)throw new Exception("单次最多4000条日程");
                }event=null;continue;
            }
            int split=line.indexOf(':');if(split<0)continue;String key=line.substring(0,split),name=key.split(";")[0];
            event.put(name,line.substring(split+1).replace("\\n","\n").replace("\\,",",").replace("\\;",";"));
            if(name.equals("DTSTART"))startKey=key;if(name.equals("DTEND"))endKey=key;
        }if(result.length()==0)throw new Exception("ICS 没有可导入日程");return result;
    }
}
