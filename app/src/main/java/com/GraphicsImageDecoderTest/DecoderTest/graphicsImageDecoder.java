package com.GraphicsImageDecoderTest.DecoderTest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileUtils;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

public class graphicsImageDecoder extends AppCompatActivity
{

    public static final int size = 20;

    Button btnFile,btnEncode,btnPlay,btnPause;
    TextView textView;
    SurfaceView sfView_nor,sfView_roi,sfView_yuv;
    static final String TAG = "roi_encoder";
    Uri uri = null;

    boolean isRoiSupport = false;

    File fileYUV = null;
    File fileES = null;
    String fileName = null;
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnFile = findViewById(R.id.btn_file);
        btnEncode = findViewById(R.id.btn_encode);
        btnPlay = findViewById(R.id.btn_play);
        btnPause = findViewById(R.id.btn_pause);
        btnEncode.setEnabled(false);
        btnPlay.setEnabled(false);
        btnPause.setEnabled(false);

        textView = findViewById(R.id.textView);

        // Code for Encode button
        sfView_nor = findViewById(R.id.sfView_nor);
        sfView_roi = findViewById(R.id.sfView_roi);
        sfView_yuv = findViewById(R.id.sfView_yuv);

        btnFile.setOnClickListener(view -> {
            // check condition
            if (!Environment.isExternalStorageManager())
            {

                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + graphicsImageDecoder.this.getPackageName()));
                graphicsImageDecoder.this.startActivity(intent);

            } else {
                // when permission is granted create method
                selectFile();
            }
        });

        // Code for Decode button
        btnEncode.setOnClickListener(view -> {
            Run run = new Run();
            Thread thd = new Thread(run);
            thd.start();
        });
    }

    public void selectFile()
    {
        // clear previous data

        // Initialize intent
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        Intent chooserIntent = Intent.createChooser(intent,"Select yuv");
        // start activity result
        startActivityForResult(chooserIntent,100);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // check condition
        if (requestCode==100 && grantResults.length > 0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)
        {
            selectFile();
        } else {
            // when permission is denied
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        // check condition
        if (requestCode==100 && resultCode==RESULT_OK && data!=null)
        {
            // when result is ok
            // initialize uri
            uri = data.getData();
            fileYUV = null;
            if (uri.getScheme().equals(graphicsImageDecoder.this.getContentResolver().SCHEME_FILE)) {
                fileYUV = new File(uri.getPath());
            } else if (uri.getScheme().equals(graphicsImageDecoder.this.getContentResolver().SCHEME_CONTENT)) {
                //把文件复制到沙盒目录
                Cursor cursor = graphicsImageDecoder.this.getContentResolver().query(uri, null, null, null, null);
                if (cursor.moveToFirst()) {
                    String displayName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    fileName = displayName;
                    if (!fileName.contains("yuv")){
                        uri = null;
                        fileName = null;
                        fileYUV = null;
                        fileES = null;
                        return;
                    }
                    fileES = new File("/storage/emulated/0/DCIM/Video/"+fileName+".es");
                    try {
                        fileES.createNewFile();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    Log.v(TAG, "fileES exists " + fileES.exists());
                    try {
                        InputStream is = graphicsImageDecoder.this.getContentResolver().openInputStream(uri);
                        File cache = new File( graphicsImageDecoder.this.getExternalCacheDir().getAbsolutePath(), displayName);
                        Log.v(TAG, "cache file : " + cache);
                        if (!cache.exists()) {
                            FileOutputStream fos = new FileOutputStream(cache);
                            FileUtils.copy(is, fos);
                            fos.close();
                        }
                        fileYUV = cache;

                        is.close();
                        Log.v(TAG, "name file : " + fileName);
                        Log.v(TAG, "select file : " + uri.getPath());
                        Log.d(TAG, "getAbsolutePath " + fileYUV.getAbsolutePath());

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            textView.setText(fileYUV.getAbsolutePath());
            btnEncode.setEnabled(true);
        } else if (data == null) {
            uri = null;
            fileYUV = null;
            btnEncode.setEnabled(false);
            textView.setText("");
        }
    }

    public class Run implements Runnable{
        @Override
        public void run() {

            String mMediaType = MediaFormat.MIMETYPE_VIDEO_HEVC;
            int width = 1088,height = 1920;
            int fillSize = width * height * 3 / 2;
            byte[] bufferYUV = new byte[fillSize];
            final int[] offsize = {0};
            final int[] queueCnt = {0};
            final int[] videoTrackid = {-1};
            final boolean[] inputEos = {false};
            final boolean[] outputEos = {false};
            MediaCodec encoder;
            try {
                encoder = MediaCodec.createEncoderByType(mMediaType);

            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            MediaCodecInfo.CodecCapabilities cp = encoder.getCodecInfo().getCapabilitiesForType(mMediaType);
            isRoiSupport = cp.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_Roi);
            String feature = "region_of_interest_support";
            Log.d(TAG, "isRoiSupport: "+isRoiSupport + " feature " + feature + " " + cp.isFeatureSupported(feature));

            MediaFormat format = MediaFormat.createVideoFormat(mMediaType,width,height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);//COLOR_FormatYUV420Flexible
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 20 * 1000 * 1000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            // turn on ROI feature at initialized stage
            format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_Roi, true);
            //format.setString(""vendor.qti-ext-extradata-enable.types", "roiinfo");

            FileInputStream inputStream = null;
            try {
                inputStream =  new FileInputStream(fileYUV);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            String outPath = "/storage/emulated/0/DCIM/Video/"+fileName+".mp4";
            MediaMuxer mMediaMuxer;
            try {
                mMediaMuxer= new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            FileInputStream finalInputStream = inputStream;
            encoder.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec mc, int inputBufferId) {
                    ByteBuffer inputBuffer = encoder.getInputBuffer(inputBufferId);
                    // fill inputBuffer with valid data
                    //Log.d(TAG, "inputBufferId  "+inputBufferId + " size " + inputBuffer.get());
                    inputBuffer.clear();
                    try {
                        //finalInputStream.seek
                        if (finalInputStream.read(bufferYUV, 0,fillSize) > 0 ) {
                            inputBuffer.put(bufferYUV);
                            offsize[0] += fillSize;
                        } else {
                            inputEos[0] = true;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (IndexOutOfBoundsException e){
                        e.printStackTrace();
                    }

                    Bundle param = new Bundle();
                    String roIRects = "640,0-1408,256=-20";
                    param.putString(MediaCodec.PARAMETER_KEY_QP_OFFSET_RECTS,roIRects);
                    encoder.setParameters(param);

                    long pts = inputEos[0] ? 0 : queueCnt[0] * 30 * 1000;
                    int size = inputEos[0] ? 0 : fillSize;
                    int flag = !inputEos[0] ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    queueCnt[0]++;
                    encoder.queueInputBuffer(inputBufferId, 0,size, pts,flag);
                    Log.d(TAG, "onInputBufferAvailable pts: "+pts + " size " + size + " offsize[0] " +offsize[0]+" flag "+ flag +" queueCnt[0] "+queueCnt[0]);
                }

                @Override
                public void onOutputBufferAvailable(MediaCodec mc, int outputBufferId,  MediaCodec.BufferInfo info) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Simply ignore codec config buffers.
                    }
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                    MediaFormat bufferFormat = encoder.getOutputFormat(outputBufferId); // option A
                    // bufferFormat is equivalent to mOutputFormat
                    // outputBuffer is ready to be processed or rendered.
                    if (info.size != 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        mMediaMuxer.writeSampleData(videoTrackid[0], outputBuffer, info);
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos[0] = true;
                        Log.d(TAG, "outputEos fileES.length() "+ fileES.length());
                    }
                    Log.d(TAG, "onOutputBufferAvailable pts: "+info.presentationTimeUs + " size " + info.size+ " flags " +info.flags +" fileES.length() "+ fileES.length());
                    try {
                        encoder.releaseOutputBuffer(outputBufferId,false);
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onOutputFormatChanged(MediaCodec mc, MediaFormat format) {
                    // Subsequent data will conform to new format.
                    // Can ignore if using getOutputFormat(outputBufferId)

                    videoTrackid[0] = mMediaMuxer.addTrack(format);
                    mMediaMuxer.start();

                    Log.d(TAG, "encoder.onOutputFormatChanged"+ format.toString());
                }
                @Override
                public void onError(MediaCodec mc, MediaCodec.CodecException e) {
                    Log.d(TAG, "encoder.onError");
                }
            });

            encoder.configure(format,null,null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            Log.d(TAG, "encoder.configure");
            encoder.start();
            Log.d(TAG, "encoder.start");


            while (!outputEos[0]) {
                // configure roi as rect and offset
            }

            encoder.stop();
            encoder.release();
            Log.d(TAG, "encoder.stop and release");
            mMediaMuxer.stop();
            mMediaMuxer.release();
            Log.d(TAG, "mMediaMuxer.stop and release");
        }
    }
}
