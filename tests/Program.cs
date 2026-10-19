using System.Text.Json.Nodes;
using DayFlow.Widget;

// Do not run against a real profile: tests use injectable local storage below.
var dir=Path.Combine(Path.GetTempPath(),"dayflow-tests-"+Guid.NewGuid());Directory.CreateDirectory(dir);
try
{
    var a=new LocalStore(Path.Combine(dir,"a"));var b=new LocalStore(Path.Combine(dir,"b"));
    var first=LocalStore.New("Offline first");a.Save(first);b.Merge(a.Export());
    Check(b.Records.Count()==1,"first import");b.Merge(a.Export());Check(b.Records.Count()==1,"repeated import deduplicates");
    var edit=(JsonObject)a.Records.First().DeepClone();edit["title"]="Updated";a.Save(edit);b.Merge(a.Export());Check(LocalStore.S(b.Records.First(),"title")=="Updated","descendant update");
    var local=(JsonObject)b.Records.First().DeepClone();local["title"]="B change";b.Save(local);
    var other=(JsonObject)a.Records.First().DeepClone();other["title"]="A change";a.Save(other);
    b.Merge(a.Export());Check(b.Records.Count()==2,"concurrent changes preserve both");b.Merge(a.Export());Check(b.Records.Count()==2,"conflict copy is stable");
    var third=new LocalStore(Path.Combine(dir,"c"));third.Merge(a.Export());
    var deleted=(JsonObject)a.Records.First().DeepClone();deleted["deleted"]=true;a.Save(deleted);third.Merge(a.Export());Check(LocalStore.Deleted(third.Records.First()),"tombstone propagation");
    var corrupted=a.Export();corrupted["records"]!.AsArray().Add(new JsonObject{["id"]="bad"});int before=b.Records.Count();
    try{b.Merge(corrupted);throw new Exception("Invalid file accepted");}catch(FormatException){}Check(b.Records.Count()==before,"invalid import is atomic");
    var remote=new LocalStore(Path.Combine(dir,"remote"));remote.Merge(b.Export());var batch=remote.Pending();var r=batch[0]!["record"]!.AsObject();string id=LocalStore.S(r,"id"),rev=LocalStore.S(r,"revision");
    var newer=(JsonObject)remote.Records.First(x=>LocalStore.S(x,"id")==id).DeepClone();newer["title"]="Edited while request in flight";remote.Save(newer);remote.Acknowledge(id,1,rev);Check(remote.Dirty.Contains(id),"in-flight edit stays dirty");
    remote.Persist();var reopened=new LocalStore(Path.Combine(dir,"remote"));Check(reopened.Dirty.Contains(id)&&reopened.Versions[id]==1,"restart preserves sync state");
    var recurring=LocalStore.New("Daily");recurring["repeat"]="daily";recurring["start_at"]="2026-10-20T09:00:00";recurring["end_at"]="2026-10-20T10:00:00";a.Save(recurring);a.Complete(recurring);
    Check(a.Records.Any(x=>LocalStore.S(x,"title")=="Daily"&&LocalStore.S(x,"start_at")=="2026-10-21T09:00:00"&&LocalStore.S(x,"end_at")=="2026-10-21T10:00:00"),"repeat preserves next duration");
    Console.WriteLine("All 10 local merge and persistence checks passed.");
    string signed=File.ReadAllText(Path.Combine(AppContext.BaseDirectory,"fixtures/windows-update.json"));
    var verified=DayFlow.Updating.UpdateFeed.Verify(signed,"windows");Check(verified["version_code"]!.GetValue<int>()==2,"valid signed update accepted");
    var tampered=JsonNode.Parse(signed)!.AsObject();tampered["payload"]=Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes("{}"));
    try{DayFlow.Updating.UpdateFeed.Verify(tampered.ToJsonString(),"windows");throw new Exception("Tampered update accepted");}catch(Exception e) when(e.Message.Contains("签名")){}Console.WriteLine("PASS tampered update rejected");
    try{DayFlow.Updating.UpdateFeed.Verify(signed,"android");throw new Exception("Wrong platform accepted");}catch(Exception e) when(e.Message.Contains("清单")){}Console.WriteLine("PASS wrong update platform rejected");
    string package=Path.Combine(dir,"invalid.zip");File.WriteAllText(package,"not-the-package");
    try{DayFlow.Updating.UpdateFeed.CheckFile(package,verified);throw new Exception("Wrong hash accepted");}catch(Exception e) when(e.Message.Contains("SHA-256")){}Console.WriteLine("PASS altered package rejected");
}
finally{Directory.Delete(dir,true);}
static void Check(bool value,string message){if(!value)throw new Exception(message);Console.WriteLine("PASS "+message);}
