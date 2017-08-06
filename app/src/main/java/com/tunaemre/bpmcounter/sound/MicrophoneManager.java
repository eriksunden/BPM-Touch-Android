package com.tunaemre.bpmcounter.sound;

import android.util.Log;
import android.media.AudioManager;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.onsets.OnsetHandler;
import be.tarsos.dsp.onsets.PercussionOnsetDetector;

import be.tarsos.dsp.util.fft.FFT;

public class MicrophoneManager
{
    public static int SAMPLE_RATE = 44100;
    public static int BUFFER_SIZE = 2048;
    public static int OVERLAP = 0;

    public static double SILENCE_THRESHOLD = -30;

    private AudioDispatcher mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP);

    private MicrophoneEvent mMicrophoneEvent = null;

    private FFT fft;

    public void run(final MicrophoneEvent microphoneProcessor)
    {
        this.mMicrophoneEvent = microphoneProcessor;

        while (mAudioDispatcher == null)
        {
            //SAMPLE_RATE = SAMPLE_RATE / 2;
            Log.e("AudioDispatcherFactory", "Check Sample Rate:" + SAMPLE_RATE);
            mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP);
        }

        /*mAudioDispatcher.addAudioProcessor(new PercussionOnsetDetector((float) SAMPLE_RATE, BUFFER_SIZE, new OnsetHandler()
        {
            @Override
            public void handleOnset(double time, double salience)
            {
                if (mMicrophoneEvent == null)
                    return;

                mMicrophoneEvent.onBeat(time);
            }
        }, PercussionOnsetDetector.DEFAULT_SENSITIVITY, PercussionOnsetDetector.DEFAULT_THRESHOLD));*/

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

        fft =  new FFT(BUFFER_SIZE);

        // FFT test
        mAudioDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {

                long startTime = System.currentTimeMillis();

                float[] audioFloatBuffer = audioEvent.getFloatBuffer();

                fft.forwardTransform(audioFloatBuffer);

                fft.backwardsTransform(audioFloatBuffer);

                long estimatedTime = System.currentTimeMillis() - startTime;

                mMicrophoneEvent.fftCalculationTime(estimatedTime);

                return true;
            }

            @Override
            public void processingFinished() {

            }
        });

        //mAudioDispatcher.addAudioProcessor(new AudioPlayer(mAudioDispatcher.getFormat()));
        mAudioDispatcher.addAudioProcessor(new AndroidAudioPlayer(mAudioDispatcher.getFormat()));

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
