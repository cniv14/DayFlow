using System.Net;
using System.IO;
using System.Security.Cryptography;
using System.Text.Json.Nodes;

namespace DayFlow.Updating;

public static class UpdateFeed
{
    public const int VersionCode=1;
    public const string DefaultUrl="https://github.com/cniv14/DayFlow/releases/latest/download/windows-update.json";
    public static JsonObject Verify(string envelope,string platform)
    {
        var root=JsonNode.Parse(envelope)!.AsObject();byte[] payload=Convert.FromBase64String(root["payload"]!.GetValue<string>()),signature=Convert.FromBase64String(root["signature"]!.GetValue<string>());
        using var rsa=RSA.Create();using var input=typeof(UpdateFeed).Assembly.GetManifestResourceStream("DayFlow.UpdateKey")!;using var reader=new StreamReader(input);rsa.ImportFromPem(reader.ReadToEnd());
        if(!rsa.VerifyData(payload,signature,HashAlgorithmName.SHA256,RSASignaturePadding.Pkcs1))throw new Exception("更新清单签名无效");
        var info=JsonNode.Parse(payload)!.AsObject();long size=info["size"]!.GetValue<long>();
        if(info["schema"]!.GetValue<int>()!=1||info["platform"]!.GetValue<string>()!=platform||size<=0||size>200L*1024*1024||!System.Text.RegularExpressions.Regex.IsMatch(info["sha256"]!.GetValue<string>(),"^[a-f0-9]{64}$"))throw new Exception("无效更新清单");
        ValidateUrl(info["url"]!.GetValue<string>());return info;
    }
    public static void ValidateUrl(string value)
    {
        if(!Uri.TryCreate(value,UriKind.Absolute,out var uri)||uri.UserInfo!=""||uri.Fragment!="")throw new Exception("无效更新地址");
        bool local=uri.Host=="localhost";
        if(IPAddress.TryParse(uri.Host,out var ip)){byte[] b=ip.GetAddressBytes();local=IPAddress.IsLoopback(ip)||(b.Length==4&&(b[0]==10||(b[0]==192&&b[1]==168)||(b[0]==172&&b[1]>=16&&b[1]<=31)));}
        if(uri.Scheme!="https"&&!(uri.Scheme=="http"&&local))throw new Exception("云端更新需要 HTTPS");
    }
    public static void CheckFile(string path,JsonObject info)
    {
        using var input=File.OpenRead(path);if(input.Length!=info["size"]!.GetValue<long>()||Convert.ToHexStringLower(SHA256.HashData(input))!=info["sha256"]!.GetValue<string>())throw new Exception("更新包 SHA-256 校验失败");
    }
}
