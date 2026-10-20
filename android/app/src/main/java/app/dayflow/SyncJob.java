package app.dayflow;

import android.app.job.*;
import android.content.*;
import java.time.Instant;
import java.util.concurrent.*;

/** Short system-scheduled sync jobs; no resident service or socket connection. */
public final class SyncJob extends JobService {
    private static final int ON_CHANGE=3101,PERIODIC=3102;
    private static boolean enabled(Context c){SharedPreferences p=c.getSharedPreferences("settings",0);return p.getBoolean("sync_auto",false)&&!p.getString(p.getString("sync_backend","dayflow").equals("taskflow")?"tf_address":"server","").isEmpty();}
    public static void request(Context c){if(!enabled(c))return;JobScheduler manager=c.getSystemService(JobScheduler.class);if(manager.getPendingJob(ON_CHANGE)==null)manager.schedule(new JobInfo.Builder(ON_CHANGE,new ComponentName(c,SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(8000).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());}
    public static void configure(Context c){JobScheduler manager=c.getSystemService(JobScheduler.class);if(!enabled(c)){manager.cancel(ON_CHANGE);manager.cancel(PERIODIC);return;}if(manager.getPendingJob(PERIODIC)==null)manager.schedule(new JobInfo.Builder(PERIODIC,new ComponentName(c,SyncJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());request(c);}
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private volatile JobParameters active;private volatile Future<?> running;private volatile java.util.concurrent.atomic.AtomicBoolean stopSignal;
    public boolean onStartJob(JobParameters params){if(active!=null)return false;if(!enabled(this)){jobFinished(params,false);return false;}active=params;java.util.concurrent.atomic.AtomicBoolean signal=new java.util.concurrent.atomic.AtomicBoolean();stopSignal=signal;running=worker.submit(()->{boolean retry=false;try{if(!Secrets.get(this,getSharedPreferences("settings",0).getString("sync_backend","dayflow").equals("taskflow")?"tf_token":"token").isEmpty()){String message=Network.sync(this);getSharedPreferences("settings",0).edit().putString("sync_last",Instant.now().toString()).putString("sync_message",message).apply();ReminderReceiver.refresh(this);}}catch(Exception e){retry=true;getSharedPreferences("settings",0).edit().putString("sync_message","等待重试："+(e.getMessage()==null?"同步失败":e.getMessage())).apply();}finally{if(active==params)active=null;if(!signal.get())jobFinished(params,retry);}});return true;}
    public boolean onStopJob(JobParameters params){if(active!=null&&active.getJobId()==params.getJobId()){if(stopSignal!=null)stopSignal.set(true);if(running!=null)running.cancel(true);active=null;}return true;}
    public void onDestroy(){if(stopSignal!=null)stopSignal.set(true);worker.shutdownNow();super.onDestroy();}
}
