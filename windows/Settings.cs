using System.IO;
using System.Net;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace DayFlow.Widget;

public sealed class WidgetSettings
{
    public string DeviceId { get; set; } = Guid.NewGuid().ToString();
    public string Server { get; set; } = "";
    public string Username { get; set; } = "";
    public string Binding { get; set; } = "";
    public string UserId { get; set; } = "";
    public string ProtectedToken { get; set; } = "";
    public long Cursor { get; set; }
    public bool Topmost { get; set; } = true;
    public bool Motion { get; set; }
    public bool Blur { get; set; } = true;
    public double Opacity { get; set; } = .86;
    public string Wallpaper { get; set; } = "";
    public string UpdateUrl { get; set; } = DayFlow.Updating.UpdateFeed.DefaultUrl;
    public bool AutoUpdates { get; set; }
    public static WidgetSettings Load()
    {
        var file=Path.Combine(LocalStore.Folder,"settings.json");return File.Exists(file)?JsonSerializer.Deserialize<WidgetSettings>(File.ReadAllText(file))!:new();
    }
    public void Save()
    {
        Directory.CreateDirectory(LocalStore.Folder);var file=Path.Combine(LocalStore.Folder,"settings.json");File.WriteAllText(file+".tmp",JsonSerializer.Serialize(this));File.Move(file+".tmp",file,true);
    }
}

internal static class Secret
{
    [StructLayout(LayoutKind.Sequential)] struct Blob {public int Length;public IntPtr Data;}
    [DllImport("crypt32.dll",SetLastError=true,CharSet=CharSet.Unicode)] static extern bool CryptProtectData(ref Blob input,string description,IntPtr entropy,IntPtr reserved,IntPtr prompt,int flags,out Blob output);
    [DllImport("crypt32.dll",SetLastError=true,CharSet=CharSet.Unicode)] static extern bool CryptUnprotectData(ref Blob input,IntPtr description,IntPtr entropy,IntPtr reserved,IntPtr prompt,int flags,out Blob output);
    [DllImport("kernel32.dll")] static extern IntPtr LocalFree(IntPtr pointer);
    static byte[] Transform(byte[] input,bool encrypt)
    {
        Blob source=new(){Length=input.Length,Data=Marshal.AllocHGlobal(input.Length)};Blob dest=default;
        try{Marshal.Copy(input,0,source.Data,input.Length);bool ok=encrypt?CryptProtectData(ref source,"DayFlow",IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,1,out dest):CryptUnprotectData(ref source,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,IntPtr.Zero,1,out dest);
            if(!ok)throw new Exception("无法访问此 Windows 用户的加密凭据");byte[] result=new byte[dest.Length];Marshal.Copy(dest.Data,result,0,dest.Length);return result;
        }finally{Marshal.FreeHGlobal(source.Data);if(dest.Data!=IntPtr.Zero)LocalFree(dest.Data);}
    }
    public static string Encrypt(string value)=>Convert.ToBase64String(Transform(Encoding.UTF8.GetBytes(value),true));
    public static string Decrypt(string value)=>value==""?"":Encoding.UTF8.GetString(Transform(Convert.FromBase64String(value),false));
}

internal static class Api
{
    static readonly HttpClient Client=new(new HttpClientHandler {AllowAutoRedirect=false}) {Timeout=TimeSpan.FromSeconds(30),MaxResponseContentBufferSize=4*1024*1024};
    public static string Endpoint(string input)
    {
        if(!Uri.TryCreate(input.Trim(),UriKind.Absolute,out var uri)||uri.UserInfo!=""||uri.Query!=""||uri.Fragment!="")throw new Exception("无效服务器地址");
        bool local=uri.Host=="localhost";
        if(IPAddress.TryParse(uri.Host,out var ip)){byte[] b=ip.GetAddressBytes();local=IPAddress.IsLoopback(ip)||(b.Length==4&&(b[0]==10||(b[0]==192&&b[1]==168)||(b[0]==172&&b[1]>=16&&b[1]<=31)));}
        if(uri.Scheme!="https"&&!(uri.Scheme=="http"&&local))throw new Exception("云端必须使用 HTTPS，HTTP 仅允许局域网 IP");return input.Trim().TrimEnd('/');
    }
    public static async Task<JsonObject> Post(string url,JsonObject body,string token="")
    {
        using var request=new HttpRequestMessage(HttpMethod.Post,url){Content=new StringContent(body.ToJsonString(),Encoding.UTF8,"application/json")};
        if(token!="")request.Headers.Authorization=new("Bearer",token);
        using var response=await Client.SendAsync(request);string text=await response.Content.ReadAsStringAsync();
        if(!response.IsSuccessStatusCode){string error="HTTP "+(int)response.StatusCode;try{error=JsonNode.Parse(text)?["error"]?.GetValue<string>()??error;}catch(JsonException){}throw new Exception(error);}
        return JsonNode.Parse(text)!.AsObject();
    }
}
