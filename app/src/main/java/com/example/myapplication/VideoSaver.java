package com.example.myapplication;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoSaver {
    private static final String TAG = "DecodeEditEncode";
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding

    private MediaExtractor mediaExtractor;

    private MediaFormat decodedFormat;

    private int width = -1;
    private int height = -1;
    private int bitRate = -1;
    private int largestColorDelta;
    String FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;" +
            "varying vec2 s_texPosition;" +
            "uniform samplerExternalOES sTexture;" +
            "void main(){" +
            "   gl_FragColor = texture2D(sTexture, s_texPosition);" +
            "}";


    public VideoSaver() {
    }

    public void init(Uri filePath, Context context) {
        mediaExtractor = new MediaExtractor();
        ParcelFileDescriptor pacelFileDescriptor = null;
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            pacelFileDescriptor = context.getContentResolver().openFileDescriptor(filePath, "r");
            FileDescriptor fileDescriptor = pacelFileDescriptor.getFileDescriptor();
            mediaExtractor.setDataSource(fileDescriptor);
            mediaMetadataRetriever.setDataSource(fileDescriptor);
            MediaFormat format = null;
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                format = mediaExtractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                    mediaExtractor.selectTrack(i);
                    this.decodedFormat = format;
                    break;
                }
            }
            if (decodedFormat == null) {
                throw new RuntimeException("File format not valid;");
            }
            width = format.getInteger(MediaFormat.KEY_WIDTH);
            height = format.getInteger(MediaFormat.KEY_HEIGHT);
            bitRate = Integer.parseInt(mediaMetadataRetriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
        } catch (IOException e) {
            Log.d(TAG, "init: media extractor may not get data source");
            throw new RuntimeException(e);
        }finally {
            try {
                pacelFileDescriptor.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void saveVideoToFile(Context context) {
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        OutputSurface outputSurface = null;
        InputSurface inputSurface = null;
        ParcelFileDescriptor fileOutputStream = null;

        try {
            MediaFormat outputFormat = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            outputFormat.setInteger(MediaFormat.KEY_BIT_RATE,
                    bitRate);
            outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE,
                    decodedFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
            outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,
                    10);

            MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String encoderName = mediaCodecList.findEncoderForFormat(outputFormat);
            String decoderName = mediaCodecList.findDecoderForFormat(decodedFormat);
            encoder = MediaCodec.createByCodecName(encoderName);
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface());
            inputSurface.makeCurrent();
            encoder.start();

            decoder = MediaCodec.createByCodecName(decoderName);
            outputSurface = new OutputSurface();
            outputSurface.changeFragmentShader(FRAGMENT_SHADER);
            decoder.configure(decodedFormat, outputSurface.getSurface(), null, 0);
            decoder.start();
            String fileName = System.currentTimeMillis() + ".mp4";
            String description = "test file";
            Uri saveDest = createMediaFile(
                    context,
                    outputFormat.getString(MIME_TYPE),
                    fileName,
                    description
            );
            fileOutputStream = context.getContentResolver().openFileDescriptor(saveDest, "rw");
            editVideoData(decoder, outputSurface, inputSurface, encoder, fileOutputStream.getFileDescriptor());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Log.d(TAG, "shutting down encoder, decoder");
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void editVideoData(MediaCodec decoder, OutputSurface outputSurface, InputSurface inputSurface, MediaCodec encoder, FileDescriptor destDescription) {
        if (destDescription == null) {
            throw new RuntimeException("not receive save file");
        }
        final int TIME_OUT = 10000;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputChunk = 0;
        boolean isFirst = false;

        boolean inputDone = false;
        boolean outputDone = false;
        boolean decoderDone = false;
        MediaMuxer mediaMuxer = null;
        int trackIndex = 0;
        try {
            mediaMuxer = new MediaMuxer(destDescription, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            while (!outputDone) {
                Log.d(TAG, "editVideoData: BEGIN EDIT VIDEO");

                if (!inputDone) {
                    int inputBufferId = decoder.dequeueInputBuffer(TIME_OUT);
                    if (inputBufferId >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputBufferId);
                        inputBuffer.clear();
                        int chunkSize = mediaExtractor.readSampleData(inputBuffer, 0);
                        if (chunkSize < 0) {
                            decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inputBufferId, 0, chunkSize, mediaExtractor.getSampleTime(), mediaExtractor.getSampleFlags());
                            Log.d(TAG, "submitted frame " + inputChunk + " to dec, size=" + chunkSize + " sample time " + mediaExtractor.getSampleTime());
                            inputChunk++;
                            mediaExtractor.advance();
                        }
                    } else {
                        Log.d(TAG, "Input buffer not available");
                    }
                }
                boolean decoderOutputAvailable = !decoderDone;
                boolean encoderOutputAvailable = true;
                while (decoderOutputAvailable || encoderOutputAvailable) {
                    int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIME_OUT);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        Log.d(TAG, "no output from encoder available");
                        encoderOutputAvailable = false;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if(isFirst) {
                            throw new RuntimeException("Fomat changed");
                        }
                        MediaFormat newFormat = encoder.getOutputFormat();
                        trackIndex = mediaMuxer.addTrack(newFormat);
                        mediaMuxer.start();
                        isFirst = true;
                        Log.d(TAG, "encoder output format changed: " + newFormat);
                    } else if (encoderStatus < 0) {
                        throw new RuntimeException("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                    } else {
                        ByteBuffer encodedData = encoder.getOutputBuffer(encoderStatus);
                        if (encodedData == null) {
                            throw new RuntimeException("encoded data was null");
                        }
                        if (bufferInfo.size != 0) {
                            if (!isFirst) {
                                throw new RuntimeException("muxer hasn't started");
                            }
                            encodedData.position(bufferInfo.offset);
                            encodedData.limit(bufferInfo.size + bufferInfo.offset);
                            //TODO write byte here
                            mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
                            outputChunk++;
                            Log.d(TAG, "encoded output size " + bufferInfo.size + "bytes");
                        }
                        outputDone = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        encoder.releaseOutputBuffer(encoderStatus, false);
                    }
                    if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // Continue attempts to drain output.
                        continue;
                    }
                    if (!decoderDone) {
                        int decoderStatus = decoder.dequeueOutputBuffer(bufferInfo, TIME_OUT);
                        if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            // no output available yet
                            Log.d(TAG, "no output from decoder available");
                            decoderOutputAvailable = false;
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            // expected before first buffer of data
                            MediaFormat newFormat = decoder.getOutputFormat();
                            Log.d(TAG, "decoder output format changed: " + newFormat);
                        } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            continue;
                        } else if (decoderStatus < 0) {
                            throw new RuntimeException("unexpected result from decoder.dequeueOutputBuffer: " + decoderStatus);
                        } else {
                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.d(TAG, "output EOS");
                                outputDone = true;
                                encoder.signalEndOfInputStream();
                            }
                            boolean doRender = (bufferInfo.size != 0);
                            decoder.releaseOutputBuffer(decoderStatus, doRender);
                            if (doRender) {
                                Log.d(TAG, "awaiting decode of frame ");
                                outputSurface.awaitNewImage();
                                outputSurface.draw();
                                inputSurface.setPresentationTime(bufferInfo.presentationTimeUs * 1000);
                                inputSurface.swapBuffers();
                            }
                        }
                    }
                }
            }

            if (inputChunk != outputChunk) {
                Log.d(TAG, "frame lost" + inputChunk + " in " + outputChunk + " out");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaMuxer != null) {
                mediaMuxer.stop();
                mediaMuxer.release();
            }
        }
    }

    private Uri createMediaFile(Context context, String mimeType, String title, String description) {
        File saveFile = null;
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.TITLE, title);
        values.put(MediaStore.Video.Media.DISPLAY_NAME, title);
        values.put(MediaStore.Video.Media.DESCRIPTION, description);
        values.put(MediaStore.Video.Media.MIME_TYPE, mimeType);
        // Add the date meta data to ensure the image is added at the front of the gallery
        values.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis());
        values.put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis());
        Uri url = null;
        ContentResolver cr = context.getContentResolver();
        try {
            url = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            saveFile = new File(context.getExternalFilesDir(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.getPath()), title);
            if (!saveFile.exists()) {
                boolean createNewFile = saveFile.createNewFile();
                if (!createNewFile) {
                    createNewFile = saveFile.createNewFile();
                }
                if (!createNewFile) {
                    throw new RuntimeException("cannot create file");
                }
            }
        } catch (Exception e) {
            if (url != null) {
                cr.delete(url, null, null);
                saveFile.delete();
            }
        }
        return url;
    }
}
