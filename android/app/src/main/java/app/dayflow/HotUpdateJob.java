package app.dayflow;
import android.app.job.*;
import android.content.*;
import java.util.concurrent.*;
/** Opt-in periodic checks, no permanent service or polling loop. */
public final class HotUpdateJob extends JobService {
    private ExecutorService worker;private volatile boolean stopped;
    public static void configure(Context c){((JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE)).cancel(703);}
    public boolean onStartJob(JobParameters params){stopped=false;worker=Executors.newSingleThreadExecutor();worker.submit(()->{try{if(getSharedPreferences("settings",0).getBoolean("hot_enabled",false)&&getSharedPreferences("settings",0).getBoolean("hot_auto",false))getSharedPreferences("settings",0).edit().putString("hot_status",HotUpdates.check(this)).apply();}catch(Exception e){getSharedPreferences("settings",0).edit().putString("hot_status",e.getMessage()==null?"热更新暂未获取，保留本地版本":e.getMessage()).apply();}finally{if(!stopped)jobFinished(params,false);worker.shutdown();}});return true;}
    public boolean onStopJob(JobParameters params){stopped=true;if(worker!=null)worker.shutdownNow();return true;}
}
