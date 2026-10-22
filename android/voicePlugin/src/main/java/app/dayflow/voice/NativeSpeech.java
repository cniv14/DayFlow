package app.dayflow.voice;
/** Stable native ABI; shared from the base loader so hot patches never reload the same .so. */
public final class NativeSpeech {
 public static void initialize(android.content.Context c)throws Exception{Class.forName("app.dayflow.VoiceEngine").getMethod("load",android.content.Context.class).invoke(null,c); }
 public static native String transcribe(String model,float[] samples,int threads);
 public static native void cancel();
}
