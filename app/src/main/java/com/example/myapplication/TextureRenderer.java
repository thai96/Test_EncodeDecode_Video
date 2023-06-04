package com.example.myapplication;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class TextureRenderer {
    String TAG = "TextureRenderer";
    private int program;
    private int texSamplerHandle;
    private int texCoordHandle;
    private int posCoordHandle;
    private int textureID;
    private FloatBuffer mTexVertices;
    private FloatBuffer mPosVertices;
   // private FloatBuff
    private int mViewWidth;
    private int mViewHeight;
    private int mTexWidth;
    private int mTexHeight;

    String VERTEX_SHADER = "" +
            "attribute vec4 v_Position;" +
            "attribute vec2 v_texPosition;" +
            "varying vec2 s_texPosition;" +
            "void main(){" +
            "   s_texPosition = v_texPosition;" +
            "   gl_Position = v_Position;" +
            "}";
    String FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;" +
            "varying vec2 s_texPosition;" +
            "uniform samplerExternalOES sTexture;" +
            "void main(){" +
            "   gl_FragColor = texture2D(sTexture, s_texPosition);" +
            "}";

    private static final float[] TEX_VERTICES = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };
    private static final float[] POS_VERTICES = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };
    private static final float[] COLOR_VERTICES = {
            1.0f, 0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f
    };
    private static final int FLOAT_SIZE_BYTES = 4;

    public TextureRenderer() {
        mTexVertices = ByteBuffer.allocateDirect(TEX_VERTICES.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mTexVertices.put(TEX_VERTICES).position(0);

        mPosVertices = ByteBuffer.allocateDirect(POS_VERTICES.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        mPosVertices.put(POS_VERTICES).position(0);
    }

    public void surfaceCreated(){
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        if(program == 0){
            throw new RuntimeException("failed creating program!");
        }
        posCoordHandle = GLES20.glGetAttribLocation(program, "v_Position");
        checkGlError("get position handler");
        texCoordHandle = GLES20.glGetAttribLocation(program, "v_texPosition");
        checkGlError("get texture handler");

        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        checkGlError("generate texture");

        textureID = texture[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
        checkGlError("bind texture id");

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameter");
    }

    public int getTextureID() {
        return textureID;
    }

    public void renderTexture(){
        checkGlError("onDrawFrame start");

        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);
        checkGlError("Create program");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);

        mTexVertices.position(0);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false,
                2 * 4, mTexVertices);
        checkGlError("update attribute pointer mTexVertices");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        checkGlError("enable pointer mTexVertices");

        mPosVertices.position(0);
        GLES20.glVertexAttribPointer(posCoordHandle, 2, GLES20.GL_FLOAT, false,
                2*4, mPosVertices);
        checkGlError("update attribute pointer mPosVertices");
        GLES20.glEnableVertexAttribArray(posCoordHandle);
        checkGlError("enable mPosVertices");

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("draw array");
        GLES20.glFinish();
    }

    public void changeFragmentShader(String fragmentShader) {
        GLES20.glDeleteProgram(program);
        program = createProgram(VERTEX_SHADER, fragmentShader);
        if (program == 0) {
            throw new RuntimeException("failed creating program");
        }
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }


    public void checkGlError(String op) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}
