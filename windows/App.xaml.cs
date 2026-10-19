using System.Windows;
using System.IO;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Threading;
namespace DayFlow.Widget;
public partial class App : Application {
    protected override void OnStartup(StartupEventArgs e) {
        base.OnStartup(e);
        var window=new MainWindow();MainWindow=window;
        if(e.Args.Length==2&&e.Args[0]=="--preview") {
            window.Left=-10000;window.Top=-10000;window.ShowActivated=false;window.ShowInTaskbar=false;
            window.Show();
            window.Dispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle,()=>{
                window.UpdateLayout();var bitmap=new RenderTargetBitmap((int)window.ActualWidth,(int)window.ActualHeight,96,96,PixelFormats.Pbgra32);bitmap.Render(window);
                var encoder=new PngBitmapEncoder();encoder.Frames.Add(BitmapFrame.Create(bitmap));using(var output=File.Create(e.Args[1]))encoder.Save(output);window.Close();
            });
        } else window.Show();
    }
}
