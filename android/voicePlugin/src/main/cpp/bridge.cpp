#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <mutex>
#include <string>
static std::atomic<bool> cancelled{false};
static std::mutex inference;
static bool abort_inference(void *) { return cancelled.load(); }
extern "C" JNIEXPORT void JNICALL Java_app_dayflow_voice_NativeSpeech_cancel(JNIEnv *,jclass) { cancelled.store(true); }
extern "C" JNIEXPORT jstring JNICALL Java_app_dayflow_voice_NativeSpeech_transcribe(JNIEnv *env,jclass,jstring path,jfloatArray samples,jint threads) {
 std::lock_guard<std::mutex> lock(inference);cancelled.store(false);
 const char *name=env->GetStringUTFChars(path,nullptr);if(!name)return nullptr;
 auto params=whisper_context_default_params();params.use_gpu=false;
 whisper_context *ctx=whisper_init_from_file_with_params(name,params);env->ReleaseStringUTFChars(path,name);
 if(!ctx){env->ThrowNew(env->FindClass("java/io/IOException"),"Cannot load Whisper GGML model; import tiny multilingual .bin");return nullptr;}
 jfloat *pcm=env->GetFloatArrayElements(samples,nullptr);if(!pcm){whisper_free(ctx);return nullptr;}
 auto options=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);options.n_threads=threads;options.language="zh";options.translate=false;options.no_context=true;options.print_realtime=false;options.print_progress=false;options.print_timestamps=false;options.print_special=false;options.abort_callback=abort_inference;
 int result=whisper_full(ctx,options,pcm,env->GetArrayLength(samples));env->ReleaseFloatArrayElements(samples,pcm,JNI_ABORT);
 std::string text;if(result==0&&!cancelled.load())for(int i=0;i<whisper_full_n_segments(ctx);i++)text+=whisper_full_get_segment_text(ctx,i);
 whisper_free(ctx);
 if(result!=0&&!cancelled.load()){env->ThrowNew(env->FindClass("java/io/IOException"),"Whisper recognition failed");return nullptr;}
 return env->NewStringUTF(text.c_str());
}
