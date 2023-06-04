package com.example.myapplication;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;


public class InputSurface {
    public static final String TAG = "InputSurface";
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;

    private Surface surface;

    public InputSurface(Surface surface){
        if(surface == null){
            throw new IllegalArgumentException();
        }
        this.surface = surface;
        eglSetup();
    }

    public void eglSetup(){
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEglError("cannot get egl display");
        if(eglDisplay == EGL14.EGL_NO_DISPLAY){
            throw new RuntimeException("cannot get egl display");
        }

        int[] version = new int[2];
        if(!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)){
            throw new RuntimeException("unable to initialize EGL14");
        }

        int[] attribList = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] eglConfigs = new EGLConfig[1];
        int[] numConfig = new int[1];
        if(!EGL14.eglChooseConfig(eglDisplay, attribList, 0, eglConfigs, 0, eglConfigs.length, numConfig, 0)){
            throw new RuntimeException("unalble to get RGB888+ GL ES config");
        }
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfigs[0], EGL14.EGL_NO_CONTEXT,
                attrib_list, 0);
        checkEglError("eglCreator context");
        if(eglContext == null){
            throw new RuntimeException("null context");
        }

        int[] surfaceAttrib = {EGL14.EGL_NONE};
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfigs[0], surface, surfaceAttrib, 0);
        checkEglError("eglCreateWindowSurface");
        if(eglSurface == null){
            throw new RuntimeException("surface was null");
        }
    }

    public void release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface);
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }
        surface.release();
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
        surface = null;
    }

    public void makeCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }
    public void makeUnCurrent() {
        if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    public boolean swapBuffers() {
        return EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    }

    public Surface getSurface() {
        return surface;
    }

    public int getWidth() {
        int[] value = new int[1];
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, value, 0);
        return value[0];
    }

    public int getHeight() {
        int[] value = new int[1];
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, value, 0);
        return value[0];
    }

    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs);
    }



    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }
}
