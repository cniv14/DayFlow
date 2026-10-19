using Microsoft.Win32;
using System.IO;
using System.Text.Json.Nodes;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Effects;
using System.Windows.Media.Imaging;

namespace DayFlow.Widget;

public partial class MainWindow : Window
{
    readonly LocalStore store;
    readonly WidgetSettings settings;
    bool busy,far;
    readonly System.Windows.Threading.DispatcherTimer updateTimer=new(){Interval=TimeSpan.FromHours(6)};
    static SolidColorBrush Ink=new(Color.FromRgb(29,58,50));
    public MainWindow()
    {
        InitializeComponent();
        try{store=new();settings=WidgetSettings.Load();}catch(Exception e){MessageBox.Show("本地数据无法读取，请先备份数据目录后检查："+e.Message);Application.Current.Shutdown();throw;}
        Left=Math.Max(0,SystemParameters.WorkArea.Right-Width-24);Top=40;ApplyStyle();Render();
        Activated+=(_,_)=>Animate(true);Deactivated+=(_,_)=>Animate(false);StateChanged+=(_,_)=>Animate(WindowState!=WindowState.Minimized&&IsActive);
        updateTimer.Tick+=async(_,_)=>await CheckUpdates(true);if(settings.AutoUpdates)updateTimer.Start();
        Closed+=(_,_)=>{Animate(false);updateTimer.Stop();};
    }
    void DragHeader(object sender,MouseButtonEventArgs e){if(e.OriginalSource is not Button)try{DragMove();}catch(InvalidOperationException){}}
    void CloseClick(object sender,RoutedEventArgs e)=>Close();
    void MinimizeClick(object sender,RoutedEventArgs e)=>WindowState=WindowState.Minimized;
    TextBlock Label(string value,double size=14)=>new(){Text=value,FontSize=size,Foreground=Ink,TextWrapping=TextWrapping.Wrap,Margin=new(0,3,0,3)};
    Button Action(string title,Action action){var b=new Button{Content=title};b.Click+=(_,_)=>action();return b;}
    void Render()
    {
        DateLabel.Text=DateTime.Now.ToString("M月d日 dddd")+"  ·  本地优先";Items.Children.Clear();
        Items.Children.Add(Label("把今天，安排得刚刚好。",19));
        var all=store.Records.Where(r=>!LocalStore.Deleted(r)&&LocalStore.S(r,"completed")=="").OrderBy(r=>LocalStore.S(r,"start_at")==""?"9999":LocalStore.S(r,"start_at")).ToList();
        int future=all.Count(r=>DateTime.TryParse(LocalStore.S(r,"start_at"),out var d)&&d.Date>DateTime.Today.AddDays(7));int shown=0;
        foreach(var r in all.Take(500))
        {
            bool distant=DateTime.TryParse(LocalStore.S(r,"start_at"),out var date)&&date.Date>DateTime.Today.AddDays(7);
            if(distant&&!far)continue;
            var panel=new StackPanel();panel.Children.Add(Label(LocalStore.S(r,"title"),16));
            string when=LocalStore.S(r,"start_at");panel.Children.Add(Label((when==""?LocalStore.S(r,"time_hint"):date.ToString("MM月dd日 HH:mm"))+"  ·  "+LocalStore.S(r,"kind"),11));
            string content=LocalStore.S(r,"content");if(content!="")panel.Children.Add(Label(content.Length>100?content[..100]+"…":content,12));
            var actions=new WrapPanel();actions.Children.Add(Action("编辑",()=>Edit(r)));actions.Children.Add(Action("完成",()=>{try{store.Complete(r);Render();}catch(Exception e){MessageBox.Show(e.Message);}}));
            actions.Children.Add(Action("删除",()=>{if(MessageBox.Show("删除这条记录？","日栖",MessageBoxButton.OKCancel)!=MessageBoxResult.OK)return;var copy=(JsonObject)r.DeepClone();copy["deleted"]=true;store.Save(copy);Render();}));panel.Children.Add(actions);
            Items.Children.Add(new Border{Background=new SolidColorBrush(Color.FromArgb((byte)(settings.Opacity*255),255,255,255)),CornerRadius=new(16),Padding=new(15),Margin=new(0,7,0,3),Child=panel});shown++;
        }
        if(shown==0)Items.Children.Add(Label("今天暂时没有安排。\n记下想法，或导入手机导出的文件。",14));
        Items.Children.Add(Action((far?"▾":"▸")+$" 远期安排 · {future} 项",()=>{far=!far;Render();}));
    }
    void ApplyStyle()
    {
        Topmost=settings.Topmost;
        Glass.Background=new SolidColorBrush(Color.FromArgb((byte)(Math.Clamp(settings.Opacity,.65,1)*255),246,248,242));
        if(File.Exists(settings.Wallpaper))
        {
            var image=new BitmapImage();image.BeginInit();image.CacheOption=BitmapCacheOption.OnLoad;image.DecodePixelWidth=1000;image.UriSource=new Uri(settings.Wallpaper);image.EndInit();image.Freeze();Wallpaper.Background=new ImageBrush(image){Stretch=Stretch.UniformToFill};
        }
        else{var brush=new LinearGradientBrush(Color.FromRgb(144,196,169),Color.FromRgb(239,212,158),35);Wallpaper.Background=brush;}
        Wallpaper.Effect=settings.Blur?new BlurEffect{Radius=16,RenderingBias=RenderingBias.Performance}:null;
        Animate(IsActive);Render();
    }
    void Animate(bool active)
    {
        Wallpaper.BeginAnimation(OpacityProperty,null);Wallpaper.Opacity=1;
        if(settings.Motion&&active&&SystemParameters.ClientAreaAnimation)
        {
            var animation=new DoubleAnimation(.55,1,TimeSpan.FromSeconds(7)){AutoReverse=true,RepeatBehavior=RepeatBehavior.Forever};
            Timeline.SetDesiredFrameRate(animation,24);Wallpaper.BeginAnimation(OpacityProperty,animation);
        }
    }
    Window Dialog(string title,StackPanel content,double height=500)=>new(){Title=title,Width=430,Height=height,Owner=this,WindowStartupLocation=WindowStartupLocation.CenterOwner,Content=new ScrollViewer{Content=content,VerticalScrollBarVisibility=ScrollBarVisibility.Auto},Background=new SolidColorBrush(Color.FromRgb(246,248,242))};
    TextBox Field(StackPanel panel,string name,string value=""){panel.Children.Add(Label(name,12));var input=new TextBox{Text=value,MaxLength=12000};panel.Children.Add(input);return input;}
    StackPanel Panel()=>new(){Margin=new(20)};
    void AddClick(object sender,RoutedEventArgs e)=>Edit(null);
    void Edit(JsonObject? original)
    {
        var panel=Panel();var r=original==null?LocalStore.New(""):(JsonObject)original.DeepClone();
        var title=Field(panel,"标题",LocalStore.S(r,"title"));var content=Field(panel,"正文",LocalStore.S(r,"content"));content.AcceptsReturn=true;content.MinHeight=80;
        var start=Field(panel,"开始：2026-10-20 14:00（可留空）",LocalStore.S(r,"start_at").Replace("T"," "));
        var hint=Field(panel,"模糊时间",LocalStore.S(r,"time_hint"));panel.Children.Add(Label("类型"));
        var kind=new ComboBox{ItemsSource=new[]{"task","event","note","inbox","course","project"},SelectedItem=LocalStore.S(r,"kind"),Margin=new(0,5,0,10)};panel.Children.Add(kind);
        var dialog=Dialog("记录",panel,590);panel.Children.Add(Action("保存",()=>{try{if(title.Text.Trim()=="")throw new Exception("请填写标题");r["title"]=title.Text.Trim();r["content"]=content.Text;r["start_at"]=start.Text.Trim()==""?"":DateTime.Parse(start.Text).ToString("yyyy-MM-dd'T'HH:mm:ss");r["time_hint"]=hint.Text;r["kind"]=kind.SelectedItem!.ToString();store.Save(r);dialog.Close();Render();}catch(Exception ex){MessageBox.Show(ex.Message);}}));dialog.ShowDialog();
    }
    void FilesClick(object sender,RoutedEventArgs e)
    {
        var p=Panel();var dialog=Dialog("文件同步 · 无需账号",p,280);p.Children.Add(Label("事项保留原始 ID。并发修改保留冲突副本，密钥与登录信息不会导出。文件是明文，请妥善保存。",13));
        p.Children.Add(Action("导出 .dayflow 文件",()=>{var picker=new SaveFileDialog{Filter="DayFlow|*.dayflow",FileName=$"dayflow-{DateTime.Today:yyyy-MM-dd}.dayflow"};if(picker.ShowDialog()!=true)return;try{File.WriteAllText(picker.FileName,store.Export().ToJsonString());StatusLabel.Text="文件已导出";dialog.Close();}catch(Exception ex){MessageBox.Show(ex.Message);}}));
        p.Children.Add(Action("导入并合并",()=>{var picker=new OpenFileDialog{Filter="DayFlow|*.dayflow;*.json"};if(picker.ShowDialog()!=true)return;try{if(new FileInfo(picker.FileName).Length>16*1024*1024)throw new Exception("导入文件上限16MB");StatusLabel.Text=store.Merge(JsonNode.Parse(File.ReadAllText(picker.FileName))!.AsObject());dialog.Close();Render();}catch(Exception ex){MessageBox.Show(ex.Message);}}));dialog.ShowDialog();
    }
    async Task Run(Func<Task<string>> work)
    {
        if(busy){MessageBox.Show("正在处理上一项操作");return;}busy=true;StatusLabel.Text="正在处理…";
        try{StatusLabel.Text=await work();Render();}catch(Exception ex){StatusLabel.Text=ex.Message;}finally{busy=false;}
    }
    void SyncClick(object sender,RoutedEventArgs e)
    {
        var p=Panel();var server=Field(p,"服务器地址（含端口）",settings.Server);var user=Field(p,"用户名",settings.Username);p.Children.Add(Label("密码（至少10位）",12));var password=new PasswordBox{Padding=new(8),Margin=new(0,5,0,5)};p.Children.Add(password);var invite=Field(p,"注册邀请码（可选）");var register=new CheckBox{Content="注册新账号",Margin=new(0,10,0,10)};p.Children.Add(register);
        p.Children.Add(Label("本地数据绑定账号后，不能直接切换到其他账号。云端需 HTTPS；局域网可用 HTTP。",12));var dialog=Dialog("可选云端 / 局域网同步",p,590);
        p.Children.Add(Action("连接并登录",async()=>{string s=server.Text,u=user.Text,pw=password.Password,code=invite.Text;bool reg=register.IsChecked==true;
            await Run(async()=>{string endpoint=Api.Endpoint(s),identity=endpoint+"|"+u.Trim().ToLowerInvariant();if(settings.Binding!=""&&settings.Binding!=identity)throw new Exception("本地数据已绑定其他账号。请先导出备份，关闭程序并使用独立本地数据目录。");
                var result=await Api.Post(endpoint+"/v1/auth/"+(reg?"register":"login"),new JsonObject{["username"]=u,["password"]=pw,["invite_code"]=code,["device_id"]=settings.DeviceId,["device_name"]=Environment.MachineName});
                if(settings.UserId!=""&&settings.UserId!=LocalStore.S(result,"user_id"))throw new Exception("服务器账号身份已变化，请先备份数据");
                settings.Server=endpoint;settings.Username=u.Trim().ToLowerInvariant();settings.Binding=identity;settings.UserId=LocalStore.S(result,"user_id");settings.ProtectedToken=Secret.Encrypt(LocalStore.S(result,"token"));settings.Save();dialog.Close();return "已登录；点击立即同步上传并合并本地数据";});}));
        p.Children.Add(Action("立即同步",async()=>{dialog.Close();await Run(Sync);}));
        p.Children.Add(Action("退出登录 · 保留本地数据",async()=>{await Run(async()=>{if(settings.ProtectedToken!="")await Api.Post(settings.Server+"/v1/auth/logout",new(),Secret.Decrypt(settings.ProtectedToken));settings.ProtectedToken="";settings.Save();dialog.Close();return "已退出，本地数据保留";});}));dialog.ShowDialog();
    }
    async Task<string> Sync()
    {
        string endpoint=Api.Endpoint(settings.Server),token=Secret.Decrypt(settings.ProtectedToken);if(token=="")throw new Exception("请先登录，或使用文件同步");int count=0,conflicts=0;
        for(int round=0;round<200;round++)
        {
            var pending=store.Pending();var sent=pending.ToDictionary(n=>LocalStore.S(n!["record"]!.AsObject(),"id"),n=>LocalStore.S(n!["record"]!.AsObject(),"revision"));
            var result=await Api.Post(endpoint+"/v1/sync",new JsonObject{["cursor"]=settings.Cursor,["mutations"]=pending},token);
            foreach(var n in result["accepted"]!.AsArray()){var a=n!.AsObject();string id=LocalStore.S(a,"id");store.Acknowledge(id,a["version"]!.GetValue<long>(),sent[id]);count++;}
            foreach(var n in result["conflicts"]!.AsArray()){var a=n!.AsObject();if(a["record"] is JsonObject record)store.Remote(record,a["version"]!.GetValue<long>());conflicts++;}
            foreach(var n in result["changes"]!.AsArray()){var a=n!.AsObject();store.Remote(a["record"]!.AsObject(),a["version"]!.GetValue<long>());}
            store.Persist();settings.Cursor=result["cursor"]!.GetValue<long>();settings.Save();
            if(result["has_more"]!.GetValue<bool>()==false&&store.Dirty.Count==0)return $"已同步 {count} 条，冲突副本 {conflicts} 条";
        }return "本批已同步，请再次点击继续";
    }
    void SettingsClick(object sender,RoutedEventArgs e)
    {
        var p=Panel();p.Children.Add(Label("壁纸与流光玻璃",22));
        var top=new CheckBox{Content="保持置顶",IsChecked=settings.Topmost,Margin=new(0,10,0,10)};p.Children.Add(top);
        var blur=new CheckBox{Content="背景模糊",IsChecked=settings.Blur,Margin=new(0,5,0,10)};p.Children.Add(blur);
        var motion=new CheckBox{Content="动态流光（窗口失焦时暂停）",IsChecked=settings.Motion,Margin=new(0,5,0,10)};p.Children.Add(motion);
        p.Children.Add(Label("卡片不透明度"));var opacity=new Slider{Minimum=.65,Maximum=1,Value=settings.Opacity,Margin=new(0,8,0,12)};p.Children.Add(opacity);
        p.Children.Add(Action("选择背景图片",()=>{var picker=new OpenFileDialog{Filter="图片|*.jpg;*.jpeg;*.png;*.webp;*.bmp"};if(picker.ShowDialog()!=true)return;try{var target=Path.Combine(LocalStore.Folder,"wallpaper"+Path.GetExtension(picker.FileName));File.Copy(picker.FileName,target,true);settings.Wallpaper=target;settings.Save();ApplyStyle();}catch(Exception ex){MessageBox.Show(ex.Message);}}));
        p.Children.Add(Action("恢复默认背景",()=>{settings.Wallpaper="";settings.Save();ApplyStyle();}));
        var feed=Field(p,"更新清单 URL（可改为自建服务器）",settings.UpdateUrl);var autoUpdates=new CheckBox{Content="每6小时检查并提示更新（默认关闭）",IsChecked=settings.AutoUpdates,Margin=new(0,8,0,8)};p.Children.Add(autoUpdates);
        p.Children.Add(Action("检查并安装更新",async()=>await CheckUpdates(false)));
        p.Children.Add(Label("MIT · DayFlow 0.1\n数据目录："+LocalStore.Folder+"\n更新将验证 RSA 签名和 SHA-256，重启后生效。",11));var dialog=Dialog("外观与设置",p,650);
        p.Children.Add(Action("保存",()=>{try{DayFlow.Updating.UpdateFeed.ValidateUrl(feed.Text);settings.UpdateUrl=feed.Text.Trim();settings.AutoUpdates=autoUpdates.IsChecked==true;updateTimer.Stop();if(settings.AutoUpdates)updateTimer.Start();settings.Topmost=top.IsChecked==true;settings.Blur=blur.IsChecked==true;settings.Motion=motion.IsChecked==true;settings.Opacity=opacity.Value;settings.Save();ApplyStyle();dialog.Close();}catch(Exception ex){MessageBox.Show(ex.Message);}}));dialog.ShowDialog();
    }
    async Task CheckUpdates(bool automatic)
    {
        if(busy)return;busy=true;
        try
        {
            var result=await UpdateClient.Check(settings.UpdateUrl);
            if(result.Info==null){if(!automatic)MessageBox.Show("当前已是最新版本","日栖");return;}
            if(MessageBox.Show("发现日栖 "+result.Info["version"]!.GetValue<string>()+"\n"+result.Info["notes"]!.GetValue<string>()+"\n下载并重启更新？本地数据将保留。","版本更新",MessageBoxButton.YesNo)!=MessageBoxResult.Yes)return;
            StatusLabel.Text="正在下载并验证更新包…";await UpdateClient.Apply(result.Info,result.Envelope);Application.Current.Shutdown();
        }
        catch(Exception error){if(!automatic)MessageBox.Show(error.Message,"更新失败");}
        finally{busy=false;}
    }
}
