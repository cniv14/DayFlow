package app.dayflow;
import android.app.Activity;import android.content.Intent;
/** Manifest components remain fixed in the base; optional patches supply implementation classes. */
public final class ModuleFactory extends androidx.core.app.CoreComponentFactory {
 public Activity instantiateActivity(ClassLoader cl,String name,Intent intent)throws InstantiationException,IllegalAccessException,ClassNotFoundException{
  ClassLoader module=ModuleUpdates.loader(name);if(module!=null)try{return super.instantiateActivity(module,name,intent);}catch(Throwable e){String id=name.substring("app.dayflow.".length(),name.lastIndexOf('.'));ModuleUpdates.disable(id,"启动异常，已使用内置版本");}
  return super.instantiateActivity(cl,name,intent);
 }
}
