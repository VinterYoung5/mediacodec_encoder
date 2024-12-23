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
import android.provider.ContactsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class graphicsImageDecoder extends AppCompatActivity
{

    public static final int size = 20;

    public Button btnFile,btnEncode,btnPlay;
    public Spinner spQP,spBR;
    public ArrayAdapter<CharSequence> adapterQP = null;
    public ArrayAdapter<CharSequence> adapterBR = null;
    public TextView textView;
    public SurfaceView sfView_nor,sfView_roi,sfView_yuv;
    static final String TAG = "roi_encoder";
    Uri uri = null;

    boolean isRoiSupport = false;
    int qp = -6;
    int bitrate = 20;

    File fileYUV = null;
    String fileName = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnFile = findViewById(R.id.btn_file);
        btnEncode = findViewById(R.id.btn_encode);
        //btnPlay = findViewById(R.id.btn_play);
        btnEncode.setEnabled(false);
        //btnPlay.setEnabled(false);

        spQP = findViewById(R.id.spinner_qp);
        String[] nameQP = {"-6qp", "-5qp", "-4qp", "-3qp", "-2qp", "-1qp", "0qp",
                "1qp", "2qp", "3qp", "4qp", "5qp", "6qp"};
        adapterQP = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nameQP);
        spQP.setAdapter(adapterQP);

        spBR = findViewById(R.id.spinner_br);
        String[] nameBR = {"50Mbps", "20Mbps", "10Mbps", "5Mbps", "2Mbps"};
        adapterBR =  new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, nameBR);
        spBR.setAdapter(adapterBR);

        textView = findViewById(R.id.textView);

        // Code for Encode button
        sfView_nor = findViewById(R.id.sfView_nor);
        sfView_roi = findViewById(R.id.sfView_roi);
        sfView_yuv = findViewById(R.id.sfView_yuv);

        btnFile.setOnClickListener(view -> {
            // check condition
            if (!Environment.isExternalStorageManager()) {
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

        spBR.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectBR = parent.getItemAtPosition(position).toString();
                String regEx="[^-?0-9]";
                Pattern p = Pattern.compile(regEx);
                Matcher m = p.matcher(selectBR);
                bitrate = Integer.parseInt(m.replaceAll("").trim());
                Log.d(TAG, "spinner select bt " + selectBR+" br "+ bitrate);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        spQP.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectQP = parent.getItemAtPosition(position).toString();
                String regEx="[^-?0-9]";
                Pattern p = Pattern.compile(regEx);
                Matcher m = p.matcher(selectQP);
                qp = Integer.parseInt(m.replaceAll("").trim());
                Log.d(TAG, "spinner select qp " + selectQP+" qp "+ qp);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
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
                        return;
                    }

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
            Boolean useQcomRoi = false;
            String mMediaType = MediaFormat.MIMETYPE_VIDEO_HEVC;
            int width = 1088,height = 1920;
            int fillSize = width * height * 3 / 2;
            byte[] bufferYUV = new byte[fillSize];
            final int[] offsize = {0};
            final int[] queueCnt = {0};
            final int[] videoTrackid = {-1};
            final boolean[] inputEos = {false};
            final boolean[] outputEos = {false};
            recIndex = 0;
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
            int bitRateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
            String brm_s = bitRateMode == MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR ? "cbr" :
                    bitRateMode == MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR ? "vbr" : "cq";
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, bitRateMode);
            //bitrate = 20;
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate * 1000 * 1000);
            int frameRate = 30;
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
            int iFrameInterval = 1;
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval);
            // turn on ROI feature at initialized stage
            if (!useQcomRoi) {
                format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_Roi, true);
            } else {
                format.setInteger("vendor.qti-ext-enc-roiinfo.enable", 1);
            }

            FileInputStream inputStream = null;
            try {
                inputStream =  new FileInputStream(fileYUV);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            SimpleDateFormat form = new SimpleDateFormat("yyyyMMdd_HHmmss");
            Date date = new Date(System.currentTimeMillis());
            String name = fileName+"."+brm_s+"."+bitrate+"Mbps"+"."+frameRate+"fps"+"."+"qp("+qp+")"+form.format(date);
            String outPath = "/storage/emulated/0/DCIM/"+name+".mp4";
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
                    if (inputEos[0]) {
                        return;
                    }
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
                    long pts = inputEos[0] ? 0 : 1000/frameRate * queueCnt[0] * 1000;
                    int size = inputEos[0] ? 0 : fillSize;
                    int flag = !inputEos[0] ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM;

                    Bundle param = new Bundle();
                    //String roiRectsInfo = "736,32-1280,256="+qp+";736,256-1280,480=6;";
                    //Rectangle 1 is top: 128, left: 96, bottom: 336, right: 272, delta QP: -3 and
                    //Rectangle 2 is top: 128, left 1000, bottom: 440, right: 1100, delta QP: -4
                    int index = recIndex >= recLocate.length ? recLocate.length-1 : recIndex;
                    recIndex++;
                    String roiRectsInfo = recLocate[index]+"="+qp+";";
                    Log.d(TAG,"roirectinfo "+roiRectsInfo);
                    if (!useQcomRoi) {
                        param.putString(MediaCodec.PARAMETER_KEY_QP_OFFSET_RECTS, roiRectsInfo);
                    } else {
                        //param.putLong("vendor.qti-ext-enc-roiinfo.timestamp",0);
                        param.putString("vendor.qti-ext-enc-roiinfo.type","rect");
                        param.putString("vendor.qti-ext-enc-roiinfo.rect-payload",roiRectsInfo);
                    }
                    encoder.setParameters(param);
                    queueCnt[0]++;
                    encoder.queueInputBuffer(inputBufferId, 0,size, pts,flag);
                    //Log.d(TAG, "onInputBufferAvailable pts: "+pts + " size " + size + " offsize[0] " +offsize[0]+" flag "+ flag +" queueCnt[0] "+queueCnt[0]);
                }

                @Override
                public void onOutputBufferAvailable(MediaCodec mc, int outputBufferId,  MediaCodec.BufferInfo info) {
                    ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferId);
                    MediaFormat bufferFormat = encoder.getOutputFormat(outputBufferId); // option A
                    // bufferFormat is equivalent to mOutputFormat
                    // outputBuffer is ready to be processed or rendered.
                    if (info.size != 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        mMediaMuxer.writeSampleData(videoTrackid[0], outputBuffer, info);
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos[0] = true;
                        Log.d(TAG, "onOutputBufferAvailable pts: "+info.presentationTimeUs + " size " + info.size+ " flags " +info.flags +" outputEos "+ outputEos[0]);
                        Log.d(TAG, "while out outputEos "+outputEos[0]);
                        encoder.stop();
                        encoder.release();
                        Log.d(TAG, "encoder.stop and release");
                        mMediaMuxer.stop();
                        mMediaMuxer.release();
                        Log.d(TAG, "mMediaMuxer.stop and release");
                    } else {
                        try {
                            encoder.releaseOutputBuffer(outputBufferId, false);
                        } catch (IllegalStateException e) {
                            e.printStackTrace();
                        }
                    }
                    //Log.d(TAG, "onOutputBufferAvailable pts: "+info.presentationTimeUs + " size " + info.size+ " flags " +info.flags +" outputEos "+ outputEos[0]);
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

//            while (!outputEos[0]) {
//            }

        }
    }
    public int recIndex = 0;
    public static String[] recLocate = {
            "688,0-1328,464",
            "688,0-1344,464",
            "688,0-1344,464",
            "688,0-1344,464",
            "688,0-1344,464",
            "688,16-1344,464",
            "688,16-1344,480",
            "688,16-1344,480",
            "688,16-1344,480",
            "688,16-1344,480",
            "688,16-1344,480",
            "688,16-1344,480",
            "688,16-1344,480",
            "688,32-1344,480",
            "688,32-1344,480",
            "688,32-1344,480",
            "688,32-1344,496",
            "688,48-1344,496",
            "688,48-1344,496",
            "688,48-1344,512",
            "688,64-1344,512",
            "688,64-1344,512",
            "688,64-1344,528",
            "688,80-1344,528",
            "688,80-1344,544",
            "688,96-1344,544",
            "688,96-1344,544",
            "688,96-1344,560",
            "688,96-1344,560",
            "688,112-1344,560",
            "688,112-1344,576",
            "688,128-1344,576",
            "688,128-1344,576",
            "672,128-1344,592",
            "672,128-1328,592",
            "672,144-1328,592",
            "672,144-1328,592",
            "656,144-1328,592",
            "656,144-1344,608",
            "656,144-1344,608",
            "656,144-1344,608",
            "656,144-1344,608",
            "656,160-1344,608",
            "656,160-1344,624",
            "656,160-1344,624",
            "672,176-1344,640",
            "672,176-1344,640",
            "672,176-1344,640",
            "672,192-1344,656",
            "672,192-1344,656",
            "672,192-1344,656",
            "672,192-1344,656",
            "672,192-1344,656",
            "656,192-1328,656",
            "656,208-1328,672",
            "656,208-1328,672",
            "656,224-1328,672",
            "656,224-1328,688",
            "656,240-1328,704",
            "656,240-1328,704",
            "656,256-1328,720",
            "656,272-1328,736",
            "656,288-1328,752",
            "656,304-1344,768",
            "656,336-1344,800",
            "656,352-1344,816",
            "656,384-1360,848",
            "656,400-1360,880",
            "656,432-1360,912",
            "656,448-1376,944",
            "656,480-1376,976",
            "656,512-1376,1008",
            "640,528-1392,1040",
            "624,560-1392,1072",
            "624,592-1392,1080",
            "624,608-1392,1080",
            "608,624-1392,1080",
            "592,656-1392,1080",
            "592,672-1392,1080",
            "576,688-1392,1080",
            "560,704-1392,1080",
            "544,720-1392,1080",
            "544,720-1392,1080",
            "528,736-1392,1080",
            "512,752-1392,1080",
            "496,768-1392,1080",
            "496,768-1392,1080",
            "480,784-1392,1080",
            "480,800-1360,1080",
            "464,816-1360,1080",
            "464,816-1360,1080",
    };
}
