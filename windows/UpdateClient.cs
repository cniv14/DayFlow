using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json.Nodes;
using DayFlow.Updating;

namespace DayFlow.Widget;

public static class UpdateClient
{
    static readonly HttpClient Client=new(new HttpClientHandler{AllowAutoRedirect=false}){Timeout=TimeSpan.FromMinutes(5)};
    static async Task<HttpResponseMessage> Open(string url)
    {
        for(int i=0;i<6;i++)
        {
            UpdateFeed.ValidateUrl(url);using var request=new HttpRequestMessage(HttpMethod.Get,url);request.Headers.UserAgent.ParseAdd("DayFlow/0.1");
            var response=await Client.SendAsync(request,HttpCompletionOption.ResponseHeadersRead);
            if((int)response.StatusCode>=300&&(int)response.StatusCode<400){var next=new Uri(new Uri(url),response.Headers.Location??throw new Exception("无效更新跳转")).ToString();response.Dispose();if(url.StartsWith("https:")&&!next.StartsWith("https:"))throw new Exception("更新地址不允许降级 HTTP");url=next;continue;}
            if(!response.IsSuccessStatusCode){int status=(int)response.StatusCode;response.Dispose();throw new Exception(status==404?"尚未发布更新清单":"更新服务 HTTP "+status);}return response;
        }throw new Exception("更新跳转次数过多");
    }
    public static async Task<(JsonObject? Info,string Envelope)> Check(string url)
    {
        using var response=await Open(url);using var input=await response.Content.ReadAsStreamAsync();using var output=new MemoryStream();byte[] buffer=new byte[8192];int n;
        while((n=await input.ReadAsync(buffer))!=0){if(output.Length+n>65536)throw new Exception("更新清单过大");output.Write(buffer,0,n);}
        string text=System.Text.Encoding.UTF8.GetString(output.ToArray());var info=UpdateFeed.Verify(text,"windows");return (info["version_code"]!.GetValue<int>()>UpdateFeed.VersionCode?info:null,text);
    }
    public static async Task Apply(JsonObject info,string envelope)
    {
        string folder=Path.Combine(LocalStore.Folder,"updates",Guid.NewGuid().ToString());Directory.CreateDirectory(folder);string zip=Path.Combine(folder,"update.zip"),manifest=Path.Combine(folder,"manifest.json");
        using(var response=await Open(info["url"]!.GetValue<string>()))
        using(var input=await response.Content.ReadAsStreamAsync())
        using(var output=File.Create(zip))
        {
            byte[] buffer=new byte[32768];long size=0;int n;while((n=await input.ReadAsync(buffer))!=0){size+=n;if(size>info["size"]!.GetValue<long>())throw new Exception("更新包长度不符");await output.WriteAsync(buffer.AsMemory(0,n));}
        }
        UpdateFeed.CheckFile(zip,info);await File.WriteAllTextAsync(manifest,envelope);
        foreach(string name in new[]{"DayFlow.Updater.exe","DayFlow.Updater.dll","DayFlow.Updater.deps.json","DayFlow.Updater.runtimeconfig.json"})File.Copy(Path.Combine(AppContext.BaseDirectory,name),Path.Combine(folder,name));
        var start=new ProcessStartInfo(Path.Combine(folder,"DayFlow.Updater.exe")){UseShellExecute=false,CreateNoWindow=true,WorkingDirectory=folder};
        foreach(string argument in new[]{AppContext.BaseDirectory,zip,manifest,Environment.ProcessId.ToString()})start.ArgumentList.Add(argument);
        _ = Process.Start(start)??throw new Exception("无法启动更新程序");
    }
}
