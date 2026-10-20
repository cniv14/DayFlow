using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace DayFlow.Widget;

public sealed class LocalStore
{
    public static readonly string Folder = Environment.GetEnvironmentVariable("DAYFLOW_DATA_DIR") ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "DayFlow");
    readonly string file;
    readonly Dictionary<string, JsonObject> records = new();
    public Dictionary<string, long> Versions { get; private set; } = new();
    public HashSet<string> Dirty { get; private set; } = new();
    public IEnumerable<JsonObject> Records => records.Values;
    public LocalStore(string? directory=null)
    {
        string folder=directory??Folder;Directory.CreateDirectory(folder);file=Path.Combine(folder,"records.dayflow");
        if (!File.Exists(file)) return;
        var root = JsonNode.Parse(File.ReadAllText(file))!.AsObject();
        foreach (var node in root["records"]!.AsArray()) { var r = node!.AsObject(); Validate(r); records[S(r,"id")] = r; }
        Versions = root["_versions"]?.Deserialize<Dictionary<string, long>>() ?? new();
        Dirty = root["_dirty"]?.Deserialize<HashSet<string>>() ?? new();
    }
    public static string S(JsonObject r, string key) => r[key]?.GetValue<string>() ?? "";
    public static bool Deleted(JsonObject r) => r["deleted"]?.GetValue<bool>() ?? false;
    public static JsonObject New(string title, string kind = "task") => new()
    {
        ["id"] = Guid.NewGuid().ToString(), ["revision"] = Guid.NewGuid().ToString(), ["ancestors"] = new JsonArray(),
        ["kind"] = kind, ["title"] = title, ["content"] = "", ["start_at"] = "", ["end_at"] = "", ["time_hint"] = "",
        ["completed"] = "", ["deleted"] = false, ["updated_at"] = DateTimeOffset.UtcNow.ToString("O")
    };
    public static void Validate(JsonObject r)
    {
        Guid.Parse(S(r,"id")); Guid.Parse(S(r,"revision"));
        if (!new[]{"task","event","note","inbox","course","project"}.Contains(S(r,"kind"))) throw new Exception("未知事项类型");
        if(S(r,"title").Length>1000 || Encoding.UTF8.GetByteCount(r.ToJsonString())>65536) throw new Exception("事项过大");
        if(r["ancestors"] is not JsonArray a || a.Count>256) throw new Exception("无效历史");
        if(S(r,"start_at")!="") DateTime.Parse(S(r,"start_at"));
        if(S(r,"end_at")!="") DateTime.Parse(S(r,"end_at"));
    }
    public void Save(JsonObject r)
    {
        string id = S(r,"id");
        if(records.TryGetValue(id,out var old))
        {
            var ancestors = old["ancestors"]!.AsArray().Select(n=>n!.GetValue<string>()).TakeLast(254).ToList();
            ancestors.Add(S(old,"revision")); r["ancestors"] = JsonSerializer.SerializeToNode(ancestors);
            r["revision"] = Guid.NewGuid().ToString();
        }
        r["updated_at"]=DateTimeOffset.UtcNow.ToString("O");Validate(r);records[id]=r;Dirty.Add(id);Persist();
    }
    public JsonObject Export() => new() { ["format"]="dayflow", ["schema"]=1, ["exported_at"]=DateTimeOffset.UtcNow.ToString("O"), ["records"]=new JsonArray(records.Values.Select(r=>r.DeepClone()).ToArray()) };
    public void Complete(JsonObject original)
    {
        if(S(original,"activation")=="waiting")throw new Exception("此任务等待前置事项完成");
        var r=(JsonObject)original.DeepClone();r["completed"]=DateTimeOffset.UtcNow.ToString("O");Save(r);
        string rule=S(r,"repeat");
        if(new[]{"daily","weekly","monthly"}.Contains(rule)&&DateTime.TryParse(S(r,"start_at"),out var date))
        {
            string id=Stable(S(r,"id")+":next");
            if(!records.ContainsKey(id)){
                var next=(JsonObject)r.DeepClone();var at=rule=="daily"?date.AddDays(1):rule=="weekly"?date.AddDays(7):date.AddMonths(1);
                next["id"]=id;next["revision"]=Guid.NewGuid().ToString();next["ancestors"]=new JsonArray();next["completed"]="";next["start_at"]=at.ToString("yyyy-MM-dd'T'HH:mm:ss");
                if(DateTime.TryParse(S(r,"end_at"),out var end))next["end_at"]=(at+(end-date)).ToString("yyyy-MM-dd'T'HH:mm:ss");Save(next);
            }
        }
        foreach(var child in records.Values.Where(x=>S(x,"activation")=="waiting"&&S(x,"trigger_after")==S(r,"id")).ToList())
        {
            var next=(JsonObject)child.DeepClone();next["activation"]="active";next["start_at"]=DateTime.Now.AddDays(child["offset_days"]?.GetValue<int>()??0).ToString("yyyy-MM-dd'T'HH:mm:ss");Save(next);
        }
    }
    public void Persist()
    {
        var data=Export();data["_versions"]=JsonSerializer.SerializeToNode(Versions);data["_dirty"]=JsonSerializer.SerializeToNode(Dirty);
        File.WriteAllText(file+".tmp",data.ToJsonString());File.Move(file+".tmp",file,true);
    }
    static bool Ancestor(JsonObject r, string rev) => r["ancestors"]!.AsArray().Any(n=>n!.GetValue<string>()==rev);
    // Java UUID.nameUUIDFromBytes compatibility, used by Android conflict copies.
    static string Stable(string seed)
    {
        byte[] bytes=MD5.HashData(Encoding.UTF8.GetBytes(seed));bytes[6]=(byte)((bytes[6]&15)|48);bytes[8]=(byte)((bytes[8]&63)|128);
        return new Guid(bytes, bigEndian:true).ToString();
    }
    void Conflict(JsonObject r)
    {
        string id=Stable(S(r,"id")+":"+S(r,"revision"));if(records.ContainsKey(id))return;
        var copy=(JsonObject)r.DeepClone();string title=S(r,"title");copy["id"]=id;copy["title"]="[冲突副本] "+title[..Math.Min(title.Length,990)];copy["deleted"]=false;copy["conflict_of"]=S(r,"id");records[id]=copy;Dirty.Add(id);
    }
    public string Merge(JsonObject root)
    {
        if(S(root,"format")!="dayflow" || root["schema"]?.GetValue<int>()!=1)throw new Exception("不支持的文件格式");
        var rows=root["records"]!.AsArray();if(rows.Count>20000)throw new Exception("文件最多20000条");
        foreach(var n in rows)Validate(n!.AsObject()); // Validate before changing local data.
        int added=0,conflicts=0;
        foreach(var n in rows)
        {
            var r=n!.AsObject();string id=S(r,"id");
            if(!records.TryGetValue(id,out var local)){records[id]=(JsonObject)r.DeepClone();Dirty.Add(id);added++;continue;}
            if(S(local,"revision")==S(r,"revision") || Ancestor(local,S(r,"revision")))continue;
            if(Ancestor(r,S(local,"revision"))){records[id]=(JsonObject)r.DeepClone();Dirty.Add(id);added++;}
            else{Conflict(r);conflicts++;}
        }
        Persist();return $"合并 {added} 条，保留冲突副本 {conflicts} 条";
    }
    // Keep even batches of 64 KiB image notes below the server's 2 MiB limit.
    public JsonArray Pending() => new(Dirty.Take(20).Select(id=>(JsonNode)new JsonObject { ["record"]=records[id].DeepClone(), ["base_version"]=Versions.GetValueOrDefault(id) }).ToArray());
    public void Acknowledge(string id,long version,string revision)
    {
        Versions[id]=version;if(records.TryGetValue(id,out var r)&&S(r,"revision")==revision)Dirty.Remove(id);
    }
    public void Remote(JsonObject r,long version)
    {
        Validate(r);string id=S(r,"id");if(version<=Versions.GetValueOrDefault(id))return;
        if(records.TryGetValue(id,out var local)&&Dirty.Contains(id)&&S(local,"revision")!=S(r,"revision"))Conflict(local);
        records[id]=(JsonObject)r.DeepClone();Dirty.Remove(id);Versions[id]=version;
    }
}
