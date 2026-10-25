package app.dayflow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.json.JSONObject;

/** Confirm the complete current record, never a truncated list preview or a stale revision. */
final class InboxReview {
    static JSONObject confirm(Data db,JSONObject preview)throws Exception{
        synchronized(db){
            JSONObject current=db.find(preview.getString("id"));
            if(current==null||current.optBoolean("deleted")||!"inbox".equals(current.optString("kind")))
                throw new Exception("这条信息已处理，请核验列表中的其他信息");
            if(!current.optString("revision").equals(preview.optString("revision")))
                throw new Exception("信息已更新，请核对最新内容后再确认");
            JSONObject record=new JSONObject(current.toString());
            String kind=record.optString("proposed_kind","event");
            record.put("kind",Arrays.asList("event","task","project","course").contains(kind)?kind:"event");
            record.put("confirmation_status","confirmed");
            if(record.optString("title").trim().isEmpty())throw new Exception("请点修改，填写标题后再确认");
            if(!record.optString("end_at").isEmpty()&&(record.optString("start_at").isEmpty()||
                    !LocalDateTime.parse(record.getString("end_at")).isAfter(LocalDateTime.parse(record.getString("start_at")))))
                throw new Exception("请点修改，核对开始和结束时间");
            if(record.optBoolean("reminder"))PlanOptions.offsets(record);
            if(Recurrences.series(record)&&PlanTime.start(record)==null)throw new Exception("请点修改，为重复事项设置日期");
            if(!record.optString("recurrence_until").isEmpty()&&PlanTime.start(record)!=null&&
                    LocalDate.parse(record.getString("recurrence_until")).isBefore(PlanTime.start(record).toLocalDate()))
                throw new Exception("请点修改，核对重复结束日期");
            Data.validate(record);
            db.save(record);
            return record;
        }
    }
}
