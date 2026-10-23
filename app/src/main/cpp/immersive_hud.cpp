// OQT's independent experimental renderer. No extracted Meta binary or implementation is used.
#define XR_USE_PLATFORM_ANDROID
#define XR_USE_GRAPHICS_API_OPENGL_ES
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/log.h>
#include <dlfcn.h>
#include "openxr/openxr.h"
#include "openxr/openxr_platform.h"
#include <atomic>
#include <mutex>
#include <vector>
#include <string>
#include <stdexcept>
#include <cstring>
#include <chrono>
#include <thread>
static std::atomic<bool> stopRequested{false};
static std::mutex pixelLock;
static std::vector<unsigned char> pixels;
static uint64_t pixelVersion=0;
// Experimental wire-layout declaration, independently derived from interface inspection.
// This extension is not promised to be supported or grantable to third-party packages.
struct OverlaySessionInfoFB { XrStructureType type; const void* next; XrFlags64 flags; int32_t placement; };
static constexpr XrStructureType OVERLAY_FB_TYPE=static_cast<XrStructureType>(1000074000);
struct Run {
 JNIEnv* env; jobject sink; jmethodID status;
 void* loader=nullptr; PFN_xrGetInstanceProcAddr get=nullptr;
 XrInstance instance=XR_NULL_HANDLE; XrSession session=XR_NULL_HANDLE;
 XrSpace space=XR_NULL_HANDLE; XrSwapchain swapchain=XR_NULL_HANDLE;
 EGLDisplay display=EGL_NO_DISPLAY; EGLContext context=EGL_NO_CONTEXT; EGLSurface surface=EGL_NO_SURFACE;
 bool begun=false;
 #define API(name) PFN_##name name=nullptr;
 API(xrDestroyInstance) API(xrGetSystem) API(xrCreateSession) API(xrDestroySession)
 API(xrPollEvent) API(xrBeginSession) API(xrEndSession) API(xrRequestExitSession)
 API(xrCreateReferenceSpace) API(xrDestroySpace) API(xrEnumerateSwapchainFormats)
 API(xrCreateSwapchain) API(xrDestroySwapchain) API(xrEnumerateSwapchainImages)
 API(xrAcquireSwapchainImage) API(xrWaitSwapchainImage) API(xrReleaseSwapchainImage)
 API(xrWaitFrame) API(xrBeginFrame) API(xrEndFrame)
 #undef API
 Run(JNIEnv* e,jobject s):env(e),sink(s),status(e->GetMethodID(e->GetObjectClass(s),"onNativeStatus","(ILjava/lang/String;)V")){}
 void report(int code,const std::string& message){
  __android_log_print(ANDROID_LOG_INFO,"OqtImmersiveHud","%s",message.c_str());
  jstring t=env->NewStringUTF(message.c_str());env->CallVoidMethod(sink,status,code,t);env->DeleteLocalRef(t);
  if(env->ExceptionCheck())env->ExceptionClear();
 }
 void check(XrResult result,const char* operation){if(XR_FAILED(result))throw std::runtime_error(std::string(operation)+" returned "+std::to_string(result));}
 template<typename T>void proc(const char* name,T& f){check(get(instance,name,reinterpret_cast<PFN_xrVoidFunction*>(&f)),name);if(!f)throw std::runtime_error(std::string(name)+" unavailable");}
 ~Run(){
  if(session && begun && xrRequestExitSession)xrRequestExitSession(session);
  if(swapchain && xrDestroySwapchain)xrDestroySwapchain(swapchain);
  if(space && xrDestroySpace)xrDestroySpace(space);
  if(session && xrDestroySession)xrDestroySession(session);
  if(display!=EGL_NO_DISPLAY){eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);if(surface!=EGL_NO_SURFACE)eglDestroySurface(display,surface);if(context!=EGL_NO_CONTEXT)eglDestroyContext(display,context);eglTerminate(display);}
  if(instance && xrDestroyInstance)xrDestroyInstance(instance);
  if(loader)dlclose(loader);
 }
 void start(jobject appContext,jobject activity,int width,int height){
  report(0,"Checking native runtime access under OQT's own identity");
  loader=dlopen("libopenxr_loader.so",RTLD_NOW|RTLD_LOCAL);
  if(!loader)throw std::runtime_error("Khronos OpenXR loader could not be loaded");
  get=reinterpret_cast<PFN_xrGetInstanceProcAddr>(dlsym(loader,"xrGetInstanceProcAddr"));
  if(!get)throw std::runtime_error("OpenXR entry point missing");
  JavaVM* vm=nullptr;env->GetJavaVM(&vm);
  PFN_xrInitializeLoaderKHR init=nullptr;proc("xrInitializeLoaderKHR",init);
  XrLoaderInitInfoAndroidKHR li{XR_TYPE_LOADER_INIT_INFO_ANDROID_KHR};li.applicationVM=vm;li.applicationContext=appContext;
  check(init(reinterpret_cast<XrLoaderInitInfoBaseHeaderKHR*>(&li)),"xrInitializeLoaderKHR");
  PFN_xrEnumerateInstanceExtensionProperties enumerate=nullptr;proc("xrEnumerateInstanceExtensionProperties",enumerate);
  uint32_t count=0;check(enumerate(nullptr,0,&count,nullptr),"extension count");
  if(count>4096)throw std::runtime_error("Invalid extension count");
  std::vector<XrExtensionProperties> ext(count,{XR_TYPE_EXTENSION_PROPERTIES});
  check(enumerate(nullptr,count,&count,ext.data()),"extension enumeration");
  auto has=[&](const char* n){for(const auto& e:ext)if(!strcmp(e.extensionName,n))return true;return false;};
  void* hzos=dlopen("libhzos.meta.so",RTLD_NOW|RTLD_LOCAL);
  bool gpuApi=hzos && dlsym(hzos,"HzuGpuProfiler_create") && dlsym(hzos,"HzuGpuProfiler_getRealtimeMetrics");
  if(hzos)dlclose(hzos);
  report(0,std::string("XR_FB_overlay=")+(has("XR_FB_overlay")?"available":"unavailable")+"; GPU profiler symbols="+(gpuApi?"present (access and metrics unverified)":"unavailable"));
  if(!has("XR_FB_overlay") || !has(XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME)){
   report(1,"Immersive overlay unavailable: required overlay/GLES extension not exposed to OQT. No Android window fallback.");return;
  }
  std::vector<const char*> enabled={"XR_FB_overlay",XR_KHR_OPENGL_ES_ENABLE_EXTENSION_NAME};
  XrInstanceCreateInfoAndroidKHR androidInfo{XR_TYPE_INSTANCE_CREATE_INFO_ANDROID_KHR};androidInfo.applicationVM=vm;androidInfo.applicationActivity=activity;
  XrInstanceCreateInfo ci{XR_TYPE_INSTANCE_CREATE_INFO};
  if(has(XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME)){enabled.push_back(XR_KHR_ANDROID_CREATE_INSTANCE_EXTENSION_NAME);ci.next=&androidInfo;}
  strcpy(ci.applicationInfo.applicationName,"Open Quest Tuner");ci.applicationInfo.applicationVersion=34;
  strcpy(ci.applicationInfo.engineName,"OQT independent HUD");ci.applicationInfo.apiVersion=XR_MAKE_VERSION(1,0,34);
  ci.enabledExtensionCount=enabled.size();ci.enabledExtensionNames=enabled.data();
  PFN_xrCreateInstance create=nullptr;proc("xrCreateInstance",create);check(create(&ci,&instance),"overlay instance creation");
  #define LOAD(name) proc(#name,name);
  LOAD(xrDestroyInstance) LOAD(xrGetSystem) LOAD(xrCreateSession) LOAD(xrDestroySession)
  LOAD(xrPollEvent) LOAD(xrBeginSession) LOAD(xrEndSession) LOAD(xrRequestExitSession)
  LOAD(xrCreateReferenceSpace) LOAD(xrDestroySpace) LOAD(xrEnumerateSwapchainFormats)
  LOAD(xrCreateSwapchain) LOAD(xrDestroySwapchain) LOAD(xrEnumerateSwapchainImages)
  LOAD(xrAcquireSwapchainImage) LOAD(xrWaitSwapchainImage) LOAD(xrReleaseSwapchainImage)
  LOAD(xrWaitFrame) LOAD(xrBeginFrame) LOAD(xrEndFrame)
  #undef LOAD
  XrSystemGetInfo gi{XR_TYPE_SYSTEM_GET_INFO};gi.formFactor=XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
  XrSystemId system=XR_NULL_SYSTEM_ID;check(xrGetSystem(instance,&gi,&system),"headset system");
  PFN_xrGetOpenGLESGraphicsRequirementsKHR requirements=nullptr;proc("xrGetOpenGLESGraphicsRequirementsKHR",requirements);
  XrGraphicsRequirementsOpenGLESKHR req{XR_TYPE_GRAPHICS_REQUIREMENTS_OPENGL_ES_KHR};check(requirements(instance,system,&req),"GLES requirements");
  display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
  if(display==EGL_NO_DISPLAY || !eglInitialize(display,nullptr,nullptr))throw std::runtime_error("EGL display unavailable");
  EGLint attrs[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE};
  EGLConfig config=nullptr;EGLint matches=0;
  if(!eglChooseConfig(display,attrs,&config,1,&matches) || matches!=1)throw std::runtime_error("EGL config unavailable");
  EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE};context=eglCreateContext(display,config,EGL_NO_CONTEXT,ca);
  EGLint pa[]={EGL_WIDTH,16,EGL_HEIGHT,16,EGL_NONE};surface=eglCreatePbufferSurface(display,config,pa);
  if(context==EGL_NO_CONTEXT || surface==EGL_NO_SURFACE || !eglMakeCurrent(display,surface,surface,context))throw std::runtime_error("EGL context unavailable");
  XrGraphicsBindingOpenGLESAndroidKHR binding{XR_TYPE_GRAPHICS_BINDING_OPENGL_ES_ANDROID_KHR};binding.display=display;binding.config=config;binding.context=context;
  OverlaySessionInfoFB overlay{OVERLAY_FB_TYPE,&binding,2,-1};
  XrSessionCreateInfo si{XR_TYPE_SESSION_CREATE_INFO};si.next=&overlay;si.systemId=system;
  check(xrCreateSession(instance,&si,&session),"native overlay session creation");
  XrReferenceSpaceCreateInfo ri{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};ri.referenceSpaceType=XR_REFERENCE_SPACE_TYPE_VIEW;ri.poseInReferenceSpace.orientation.w=1;
  check(xrCreateReferenceSpace(session,&ri,&space),"HUD view space");
  uint32_t nf=0;check(xrEnumerateSwapchainFormats(session,0,&nf,nullptr),"format count");if(nf>4096)throw std::runtime_error("Invalid format count");
  std::vector<int64_t> formats(nf);check(xrEnumerateSwapchainFormats(session,nf,&nf,formats.data()),"formats");
  int64_t format=0;for(auto f:formats)if(f==GL_RGBA8 || f==GL_SRGB8_ALPHA8){format=f;break;}
  if(!format)throw std::runtime_error("RGBA overlay format unavailable");
  XrSwapchainCreateInfo sc{XR_TYPE_SWAPCHAIN_CREATE_INFO};sc.usageFlags=XR_SWAPCHAIN_USAGE_SAMPLED_BIT|XR_SWAPCHAIN_USAGE_TRANSFER_DST_BIT;sc.format=format;sc.sampleCount=1;sc.width=width;sc.height=height;sc.faceCount=1;sc.arraySize=1;sc.mipCount=1;
  check(xrCreateSwapchain(session,&sc,&swapchain),"HUD swapchain");
  uint32_t ni=0;check(xrEnumerateSwapchainImages(swapchain,0,&ni,nullptr),"image count");if(ni>64 || ni==0)throw std::runtime_error("Invalid image count");
  std::vector<XrSwapchainImageOpenGLESKHR> images(ni,{XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_ES_KHR});
  check(xrEnumerateSwapchainImages(swapchain,ni,&ni,reinterpret_cast<XrSwapchainImageBaseHeader*>(images.data())),"HUD images");
  std::vector<uint64_t> uploaded(ni,UINT64_MAX);
  report(2,"Native overlay session created; waiting for runtime readiness. Gameplay focus and visibility unverified.");
  auto started=std::chrono::steady_clock::now();bool reported=false;uint64_t submitted=0;
  while(!stopRequested){
   XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER};
   while(xrPollEvent(instance,&event)==XR_SUCCESS){
    if(event.type==XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED){auto* e=reinterpret_cast<XrEventDataSessionStateChanged*>(&event);
     if(e->state==XR_SESSION_STATE_FOCUSED)throw std::runtime_error("Overlay acquired XR input focus; closing to protect gameplay");
     if(e->state==XR_SESSION_STATE_READY && !begun){XrSessionBeginInfo bi{XR_TYPE_SESSION_BEGIN_INFO};bi.primaryViewConfigurationType=XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;check(xrBeginSession(session,&bi),"begin overlay session");begun=true;}
     if(e->state==XR_SESSION_STATE_STOPPING){if(begun)xrEndSession(session);begun=false;return;}
     if(e->state==XR_SESSION_STATE_EXITING || e->state==XR_SESSION_STATE_LOSS_PENDING)return;
    }else if(event.type==XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING)return;
    event={XR_TYPE_EVENT_DATA_BUFFER};
   }
   if(!reported && std::chrono::steady_clock::now()-started>std::chrono::seconds(12))throw std::runtime_error("Runtime did not permit overlay frames");
   if(!begun){if(std::chrono::steady_clock::now()-started>std::chrono::seconds(8))throw std::runtime_error("Overlay runtime did not become ready");std::this_thread::sleep_for(std::chrono::milliseconds(20));continue;}
   XrFrameWaitInfo wi{XR_TYPE_FRAME_WAIT_INFO};XrFrameState fs{XR_TYPE_FRAME_STATE};check(xrWaitFrame(session,&wi,&fs),"wait overlay frame");
   XrFrameBeginInfo fi{XR_TYPE_FRAME_BEGIN_INFO};check(xrBeginFrame(session,&fi),"begin overlay frame");
   XrCompositionLayerQuad quad{XR_TYPE_COMPOSITION_LAYER_QUAD};quad.layerFlags=XR_COMPOSITION_LAYER_BLEND_TEXTURE_SOURCE_ALPHA_BIT|XR_COMPOSITION_LAYER_UNPREMULTIPLIED_ALPHA_BIT;quad.space=space;quad.eyeVisibility=XR_EYE_VISIBILITY_BOTH;quad.pose.orientation.w=1;quad.pose.position={.60f,.42f,-1.6f};quad.size={.64f,.32f};quad.subImage.swapchain=swapchain;quad.subImage.imageRect={{0,0},{width,height}};
   const XrCompositionLayerBaseHeader* layer=reinterpret_cast<XrCompositionLayerBaseHeader*>(&quad);
   bool show=fs.shouldRender && !stopRequested;
   if(show){uint32_t index=0;XrSwapchainImageAcquireInfo ai{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};check(xrAcquireSwapchainImage(swapchain,&ai,&index),"acquire image");
    XrSwapchainImageWaitInfo sw{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};sw.timeout=500000000;XrResult waited=xrWaitSwapchainImage(swapchain,&sw);if(waited!=XR_SUCCESS)throw std::runtime_error("Overlay image wait failed or timed out");
    if(index>=images.size())throw std::runtime_error("Runtime returned invalid image index");
    {std::lock_guard<std::mutex> l(pixelLock);if(pixels.size()==static_cast<size_t>(width*height*4) && uploaded[index]!=pixelVersion){glBindTexture(GL_TEXTURE_2D,images[index].image);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels.data());glFlush();if(glGetError()!=GL_NO_ERROR)throw std::runtime_error("HUD texture upload failed");uploaded[index]=pixelVersion;}}
    XrSwapchainImageReleaseInfo release{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};check(xrReleaseSwapchainImage(swapchain,&release),"release image");
   }
   XrFrameEndInfo end{XR_TYPE_FRAME_END_INFO};end.displayTime=fs.predictedDisplayTime;end.environmentBlendMode=XR_ENVIRONMENT_BLEND_MODE_OPAQUE;end.layerCount=show?1:0;end.layers=show?&layer:nullptr;check(xrEndFrame(session,&end),"submit overlay frame");
   if(show && !reported){report(3,"Immersive HUD frames submitted. Verify visibility and game input on the headset.");reported=true;}
   if(++submitted%900==0)__android_log_print(ANDROID_LOG_INFO,"OqtImmersiveHud","Overlay frames submitted: %llu",(unsigned long long)submitted);
  }
 }
};
extern "C" JNIEXPORT void JNICALL Java_io_github_openquesttuner_monitor_NativeHudBridge_run(JNIEnv* e,jclass,jobject context,jobject activity,jobject callback,jint width,jint height){
 if(width!=768 || height!=384)return;
 Run r(e,callback);try{r.start(context,activity,width,height);if(r.session)r.report(4,"Immersive HUD session ended");}catch(const std::exception& ex){r.report(5,std::string("Immersive HUD unavailable: ")+ex.what()+". No panel fallback.");}
}
extern "C" JNIEXPORT void JNICALL Java_io_github_openquesttuner_monitor_NativeHudBridge_reset(JNIEnv*,jclass){std::lock_guard<std::mutex> l(pixelLock);pixels.assign(768*384*4,0);pixelVersion++;stopRequested=false;}
extern "C" JNIEXPORT void JNICALL Java_io_github_openquesttuner_monitor_NativeHudBridge_stop(JNIEnv*,jclass){stopRequested=true;}
extern "C" JNIEXPORT void JNICALL Java_io_github_openquesttuner_monitor_NativeHudBridge_pixels(JNIEnv* e,jclass,jintArray array){
 if(e->GetArrayLength(array)!=768*384)return;
 jint* data=e->GetIntArrayElements(array,nullptr);if(!data)return;
 {std::lock_guard<std::mutex> l(pixelLock);pixels.resize(768*384*4);for(int i=0;i<768*384;i++){unsigned int v=data[i];int out=(383-i/768)*768+i%768;pixels[out*4]=(v>>16)&255;pixels[out*4+1]=(v>>8)&255;pixels[out*4+2]=v&255;pixels[out*4+3]=(v>>24)&255;}pixelVersion++;}
 e->ReleaseIntArrayElements(array,data,JNI_ABORT);
}
