package com.example.myapplication;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.view.Surface;


public class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    public static final String TAG = "Output Surface";
    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    public Object syncObject = new Object();
    private boolean frameAvailable;
    private TextureRenderer textureRenderer;

    public OutputSurface(int width, int height) {
        if(width <= 0 || height <= 0){
            throw new IllegalArgumentException();
        }
        eglSetup(width, height);
        makeCurrent();
        setup();
    }

    public OutputSurface() {
        setup();
    }

    public void draw(){
        textureRenderer.renderTexture();
    }

    public void setup(){
        textureRenderer = new TextureRenderer();
        textureRenderer.surfaceCreated();
        surfaceTexture = new SurfaceTexture(textureRenderer.getTextureID());
        surfaceTexture.setOnFrameAvailableListener(this);
        surface = new Surface(surfaceTexture);
    }

    public void eglSetup(int width, int height){
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(eglDisplay ==EGL14.EGL_NO_DISPLAY){
            throw new RuntimeException("unable to get display");
        }
        int[] version = new int[2];
        if(!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)){
            eglDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if(!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length, numConfigs, 0)){
            throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
        }
        int[] attrib_List = {
                EGL14.EGL_CONTEXT_CLIENT_TYPE, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_List, 0);
        checkEglError("create context");
        if(eglContext == null){
            throw new RuntimeException("null Context");
        }

        int[] surfaceAttrib = {
                EGL14.EGL_WIDTH, width,
                EGL14.EGL_HEIGHT, height,
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttrib, 0);
        checkEglError("create surface");
        if(eglSurface == null){
            throw new RuntimeException("create surface");
        }
    }

    public void release(){
        if(eglDisplay != EGL14.EGL_NO_DISPLAY){
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }
        surface.release();
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;

        textureRenderer = null;
        surface = null;
        surfaceTexture = null;
    }

    public void makeCurrent(){
        if(!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)){
            throw new RuntimeException("eglMakeCurrent failed!");
        }
    }

    public Surface getSurface() {
        return surface;
    }

    public void changeFragmentShader(String fragmentShader){
        textureRenderer.changeFragmentShader(fragmentShader);
    }

    public void awaitNewImage(){
        final int TIME_OUT = 500;

        synchronized (syncObject){
            while(!frameAvailable){
                try{
                    syncObject.wait(TIME_OUT);
                    if(!frameAvailable){
                        throw new RuntimeException("Surface frame wait time out!");
                    }
                }catch (InterruptedException ex){
                    throw new RuntimeException(ex);
                }
            }
            frameAvailable = false;
        }

        textureRenderer.checkGlError("before updateTexImage");
        surfaceTexture.updateTexImage();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (syncObject){
            if(frameAvailable){
                throw new RuntimeException("frame already set! Cannot be dropped!");
            }
            frameAvailable = true;
            syncObject.notifyAll();
        }
    }

    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
