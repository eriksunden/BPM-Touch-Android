package com.tunaemre.bpmcounter.sound;

import android.util.Log;
import android.util.TimingLogger;

import java.util.Arrays;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.onsets.OnsetHandler;
import be.tarsos.dsp.onsets.PercussionOnsetDetector;

import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.HammingWindow;

public class MicrophoneManager
{
    public static int SAMPLE_RATE = 44100;
    public static int BUFFER_SIZE = 512;
    public static int OVERLAP = 0;

    public static double SILENCE_THRESHOLD = -70;

    private AudioDispatcher mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP);

    private MicrophoneEvent mMicrophoneEvent = null;

    //private final float[] zeroPaddedInvesedQuery;
    private float[] zeroPaddedData;
    private FFT fft;

    public void run(final MicrophoneEvent microphoneProcessor)
    {
        this.mMicrophoneEvent = microphoneProcessor;

        while (mAudioDispatcher == null)
        {
            SAMPLE_RATE = SAMPLE_RATE / 2;
            Log.e("AudioDispatcherFactory", "Check Sample Rate:" + SAMPLE_RATE);
            mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP);
        }

        mAudioDispatcher.addAudioProcessor(new PercussionOnsetDetector((float) SAMPLE_RATE, BUFFER_SIZE, new OnsetHandler()
        {
            @Override
            public void handleOnset(double time, double salience)
            {
                if (mMicrophoneEvent == null)
                    return;

                mMicrophoneEvent.onBeat(time);
            }
        }, PercussionOnsetDetector.DEFAULT_SENSITIVITY, PercussionOnsetDetector.DEFAULT_THRESHOLD));

        final SilenceDetector silenceDetector = new SilenceDetector(SILENCE_THRESHOLD, false);

        mAudioDispatcher.addAudioProcessor(silenceDetector);

        mAudioDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                if (mMicrophoneEvent == null)
                    return true;

                mMicrophoneEvent.soundMeter(silenceDetector.currentSPL());
                return true;
            }

            @Override
            public void processingFinished() {

            }
        });

        zeroPaddedData= new float[BUFFER_SIZE*2];
        /*int queryIndex = query.length-1;
        for(int i = query.length/2; i < query.length + query.length/2 ; i++){
            zeroPaddedInvesedQuery[i] = query[queryIndex];
            queryIndex--;
        }
        this.handler = handler;*/
        fft =  new FFT(zeroPaddedData.length, new HammingWindow());
        //fft.forwardTransform(zeroPaddedInvesedQuery);

        // FFT test
        mAudioDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                //TimingLogger timings = new TimingLogger("FFT", "FFT Calculation");
                long startTime = System.currentTimeMillis();

                float[] fftData = audioEvent.getFloatBuffer().clone();

                Arrays.fill(zeroPaddedData, 0);
                System.arraycopy(fftData, 0, zeroPaddedData, fftData.length/2, fftData.length);

                fft.forwardTransform(zeroPaddedData);
                //timings.addSplit("forwardTarnsform");

                //fft.multiply(zeroPaddedData, zeroPaddedInvesedQuery);
                fft.backwardsTransform(zeroPaddedData);
                //timings.addSplit("backwardsTransform");

                long estimatedTime = System.currentTimeMillis() - startTime;

                //timings.dumpToLog();

                /*float maxVal = -100000;
                int maxIndex =  0;
                for(int i = 0 ; i<zeroPaddedData.length ; i++){
                    if(zeroPaddedData[i]> maxVal){
                        maxVal = zeroPaddedData[i];
                        maxIndex=i;
                    }
                }*/

                //float time = (float) (audioEvent.getTimeStamp() - audioEvent.getBufferSize()/audioEvent.getSampleRate() + maxIndex/2 /audioEvent.getSampleRate());

                //float time = (float) (audioEvent.getTimeStamp());

                mMicrophoneEvent.fftCalculationTime(estimatedTime);

                //handler.handleCrossCorrelation((float)audioEvent.getTimeStamp(), time, maxVal);

                return true;
            }

            @Override
            public void processingFinished() {

            }
        });

        mAudioDispatcher.run();
    }

    public void stop()
    {
        if (mAudioDispatcher != null)
            mAudioDispatcher.stop();

        mAudioDispatcher = null;
        mMicrophoneEvent = null;
    }
}
