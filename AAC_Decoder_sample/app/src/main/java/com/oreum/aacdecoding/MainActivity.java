package com.oreum.aacdecoding;

import androidx.appcompat.app.AppCompatActivity;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    String TAG = "say";

    Button m_start_btn = null;
    Button m_stop_btn = null;

    MediaCodec m_decoder = null;
    MediaFormat m_format = null;
    MediaFormat m_format2 = null;
    MediaExtractor m_extractor = null;

    //String m_path = "/mnt/sdcard/aac_dump.aac";
    String m_path = "/mnt/sdcard/dump_aac.aac";

    ByteBuffer m_codecInputBuffer;
    ByteBuffer m_codecOutputBuffer;

    int audio_profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;
    int m_samplerate = 44100;
    int m_ch = 2;

    boolean m_bstop = false;
    boolean m_low_ver = false;

    Thread m_audio_thread = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
            m_low_ver = false;
        } else{
            m_low_ver = true;
        }

        m_start_btn = (Button)findViewById(R.id.start_btn);
        m_stop_btn = (Button)findViewById(R.id.stop_btn);

        m_start_btn.setOnClickListener(this);
        m_stop_btn.setOnClickListener(this);
        m_audio_thread =  new Thread(AACDecoderAndPlayRunnable);
        _decoder_init();

    }

    public int _decoder_init(){
        m_format = makeAACCodecSpecificData( audio_profile, 44100, 2 );

        try{
            m_decoder = MediaCodec.createDecoderByType("audio/mp4a-latm");
            m_extractor = new MediaExtractor();
            m_extractor.setDataSource( m_path );
            m_extractor.selectTrack(0);

            m_format2 = m_extractor.getTrackFormat(0);
            long duration = m_format2.getLong(MediaFormat.KEY_DURATION);
            int totalSec = (int) (duration / 1000 / 1000);
            int min = totalSec / 60;
            int sec = totalSec % 60;
            Log.d(TAG, "info. mime m_format2 [ " + m_format2.getString(MediaFormat.KEY_MIME) + " ] duration[ " + totalSec + " ] min[ " + min + " ], sec[ " + sec + " ] ");

            /*
            duration = m_format.getLong(MediaFormat.KEY_DURATION);
            totalSec = (int) (duration / 1000 / 1000);
            min = totalSec / 60;
            sec = totalSec % 60;
            Log.d(TAG, "info. mime m_format[ " + m_format.getString(MediaFormat.KEY_MIME) + " ] duration[ " + totalSec + " ] min[ " + min + " ], sec[ " + sec + " ] ");
             */
            m_decoder.configure( m_format, null, null, 0 );

//            m_codecInputBuffer = m_codec.getInputBuffer(m_current_index);
//            m_codecOutputBuffer = m_codec.getOutputBuffer(m_current_index);
            m_decoder.start();
        }catch (Exception e){
            Log.d(TAG, "----- test error. decoder create failed");
            e.printStackTrace();
        }



        return 0;
    }

    public void _play()
    {
        Log.d( TAG, "----- test _play start" );
        m_audio_thread.start();

        return;
    }

    public void _stop()
    {
        Log.d( TAG, "----- test _stop stop" );
        m_bstop = true;

        return;
    }


    private MediaFormat makeAACCodecSpecificData(int audioProfile, int sampleRate, int channelConfig) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm"); // AAC의 Decoder type
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate); // sample rate 정의
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig); // channel 정의
        format.setInteger(MediaFormat.KEY_IS_ADTS, 0); // ADTS_Header USE?1:0

        int samplingFreq[] = { // Android 참고 코드상 아래와 같은 samplerate를 지원
                96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050,
                16000, 12000, 11025, 8000
        };

        // Search the Sampling Frequencies
        // 아래 코드를 통해 0~11 에 맞는 값을 가져와야 합니다.
        // 일반적으로 44100을 사용하고 있으며, 여기에서는 4번에 해당됩니다.
        int sampleIndex = -1;
        for (int i = 0; i < samplingFreq.length; ++i) {
            if (samplingFreq[i] == sampleRate) {
                Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i);
                sampleIndex = i;
            }
        }

        if (sampleIndex == -1) {
            return null;
        }

        /* 디코딩에 필요한 csd-0의 byte를 생성합니다. 이 부분은 Android 4.4.2의 Full source를 참고하여 작성
         * csd-0에서 필요한 byte는 2 byte 입니다. 2byte에 필요한 정보는 audio Profile 정보와
         * sample index, channelConfig 정보가 됩니다.
         */
        ByteBuffer csd = ByteBuffer.allocate(2);
        // 첫 1 byte에는 Audio Profile에 3 bit shift 처리합니다. 그리고 sample index를 1bit shift 합니다.
        csd.put((byte) ((audioProfile << 3) | (sampleIndex >> 1)));

        csd.position(1);
        // 다음 1 byte에는 sample index를 7bit shift 하고, channel 수를 3bit shift 처리합니다.
        csd.put((byte) ((byte) ((sampleIndex << 7) & 0x80) | (channelConfig << 3)));
        csd.flip();
        // MediaCodec에서 필요하는 MediaFormat에 방금 생성한 "csd-0"을 저장합니다.
        format.setByteBuffer("csd-0", csd); // add csd-0

        return format;
    }

    Runnable AACDecoderAndPlayRunnable = new Runnable() {

        @Override
        public void run() {
            AACDecoderAndPlay();
        }


    };


    public void AACDecoderAndPlay() {
        ByteBuffer[] inputBuffers = null;
        ByteBuffer[] outputBuffers = null;

        if( m_low_ver ) {
            inputBuffers = m_decoder.getInputBuffers();
            outputBuffers = m_decoder.getOutputBuffers();
        }
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int buffsize = AudioTrack.getMinBufferSize(m_samplerate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        // create an audiotrack object
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, m_samplerate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                buffsize,
                AudioTrack.MODE_STREAM);
        audioTrack.play();

        while (!m_bstop) {
            int inIndex = m_decoder.dequeueInputBuffer(2000);
            if (inIndex >= 0) {
                ByteBuffer buffer;
                if( m_low_ver )
                {
                    buffer = inputBuffers[inIndex];
                }
                else
                {
                    buffer = m_decoder.getInputBuffer(inIndex);
                }

                int sampleSize = -1;
                try{
                    sampleSize = m_extractor.readSampleData(buffer, 0);
                }catch (Exception e){
                    Log.d(TAG, "--- test inIndex[ " + inIndex + " ] samplesize[ " + sampleSize + " ]");
                    e.printStackTrace();
                }
                Log.d(TAG, "--- test inIndex[ " + inIndex + " ] samplesize[ " + sampleSize + " ]");


                if (sampleSize < 0) {
                    // We shouldn't stop the playback at this point, just pass the EOS
                    // flag to m_decoder, we will get it again from the
                    // dequeueOutputBuffer
                    Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                    m_decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                } else {
                    m_decoder.queueInputBuffer(inIndex, 0, sampleSize, m_extractor.getSampleTime(), 0);
                    m_extractor.advance();
                }

                int outIndex = m_decoder.dequeueOutputBuffer(info, 2000);

                if( outIndex >= 0 ){
                    if( ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) ){
                        Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                        break;
                    }else{
                        ByteBuffer outBuffer;
                        if( m_low_ver ){
                            outBuffer = outputBuffers[outIndex];
                        }
                        else
                        {
                            outBuffer = m_decoder.getOutputBuffer(outIndex);
                        }
                        Log.v(TAG, "We can't use this buffer but render it due to the API limit, " + outBuffer + "");
                        ByteBuffer copyBuffer = ByteBuffer.allocate(outBuffer.remaining());
                        copyBuffer.put(outBuffer);
                        copyBuffer.flip();

                        final byte[] chunk = new byte[info.size];
                        copyBuffer.get(chunk); // Read the buffer all at once
                        copyBuffer.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN



                        audioTrack.write(chunk, info.offset, info.offset + info.size); // AudioTrack write data
                        m_decoder.releaseOutputBuffer(outIndex, false);
                    }

                }
                else if( outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED )
                {
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED");
                }
                else if( outIndex == MediaCodec.INFO_TRY_AGAIN_LATER )
                {
                    Log.d(TAG, "INFO_TRY_AGAIN_LATER");

                }
            }
        }

        m_decoder.stop();
        m_decoder.release();
        m_decoder = null;

        m_extractor.release();
        m_extractor = null;

        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId())
        {
            case R.id.start_btn:
                _play();
                break;
            case R.id.stop_btn:
                _stop();
                break;
            default:
                break;
        }
    }
}
