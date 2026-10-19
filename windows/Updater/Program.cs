using System.Diagnostics;
using System.IO.Compression;
using DayFlow.Updating;

// Runs from a private staging directory after the widget exits. Never touches the user database.
try
{
    if(args.Length!=4)throw new Exception("Invalid updater arguments");
    string target=Path.GetFullPath(args[0]),zip=Path.GetFullPath(args[1]),manifest=Path.GetFullPath(args[2]);int pid=int.Parse(args[3]);
    if(!File.Exists(Path.Combine(target,"DayFlow.Widget.exe")))throw new Exception("Target is not a DayFlow installation");
    var info=UpdateFeed.Verify(File.ReadAllText(manifest),"windows");UpdateFeed.CheckFile(zip,info);
    try{using var parent=Process.GetProcessById(pid);if(!parent.WaitForExit(60000))throw new Exception("Widget did not exit");}catch(ArgumentException){}
    string stage=Path.Combine(Path.GetDirectoryName(zip)!,"stage-"+Guid.NewGuid());Directory.CreateDirectory(stage);
    using(var archive=ZipFile.OpenRead(zip))
    {
        long total=0;
        foreach(var entry in archive.Entries)
        {
            string name=entry.FullName;
            if(name!=Path.GetFileName(name)||!Allowed(name))throw new Exception("Unexpected file in update archive");
            total+=entry.Length;if(total>100L*1024*1024)throw new Exception("Expanded update exceeds size limit");
            entry.ExtractToFile(Path.Combine(stage,name));
        }
    }
    if(!File.Exists(Path.Combine(stage,"DayFlow.Widget.exe")))throw new Exception("Missing application executable");
    string rollback=Path.Combine(Path.GetDirectoryName(zip)!,"rollback-"+Guid.NewGuid());Directory.CreateDirectory(rollback);
    var replaced=new List<string>();
    try
    {
        foreach(string source in Directory.GetFiles(stage))
        {
            string name=Path.GetFileName(source),destination=Path.Combine(target,name);
            if(File.Exists(destination))File.Copy(destination,Path.Combine(rollback,name));
            File.Copy(source,destination,true);replaced.Add(name);
        }
    }
    catch
    {
        foreach(var name in replaced){string backup=Path.Combine(rollback,name);if(File.Exists(backup))File.Copy(backup,Path.Combine(target,name),true);}
        throw;
    }
    Process.Start(new ProcessStartInfo(Path.Combine(target,"DayFlow.Widget.exe")){UseShellExecute=true,WorkingDirectory=target});
}
catch(Exception error)
{
    string folder=Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"DayFlow");Directory.CreateDirectory(folder);File.WriteAllText(Path.Combine(folder,"update-error.txt"),error.Message);
}
static bool Allowed(string name)=>name.StartsWith("DayFlow.",StringComparison.Ordinal)&&(name.EndsWith(".exe")||name.EndsWith(".dll")||name.EndsWith(".json")||name.EndsWith(".pdb"))||name=="LICENSE"||name=="README.txt";
