package app.dayflow;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.widget.Toast;
public final class UpdateInstaller extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){if(!"app.dayflow.INSTALL_RESULT".equals(i.getAction()))return;
        int status=i.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=i.getParcelableExtra(Intent.EXTRA_INTENT);if(confirm!=null)c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        else if(status!=PackageInstaller.STATUS_SUCCESS)Toast.makeText(c,"未完成更新，原版本和数据仍保留。请检查安装授权及签名是否一致。",Toast.LENGTH_LONG).show();
    }
}
