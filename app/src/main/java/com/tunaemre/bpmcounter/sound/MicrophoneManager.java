package com.tunaemre.bpmcounter.sound;

import android.util.Log;

import java.io.InputStream;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.io.android.AndroidAudioPlayer;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

import be.tarsos.dsp.util.fft.FFT;

public class MicrophoneManager
{
    public static int SAMPLE_RATE = 44100;
    public static int BUFFER_SIZE = 2048;
    public static int OVERLAP = 0;

    public static double SILENCE_THRESHOLD = -30;

    private AudioDispatcher mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP);
    private AudioDispatcher mAudioDispatcherResponse = null;

    private MicrophoneEvent mMicrophoneEvent = null;

    private FFT mFFT = new FFT(BUFFER_SIZE);
    private float[] mResponseAudioFloatBuffer;

    public void init(final InputStream responseStream)
    {
        TarsosDSPAudioFormat format = new TarsosDSPAudioFormat(SAMPLE_RATE, 16, 1, true, false);
        UniversalAudioInputStream uais = new UniversalAudioInputStream(responseStream, format);

        mAudioDispatcherResponse = new AudioDispatcher(uais, BUFFER_SIZE, OVERLAP);

        mAudioDispatcherResponse.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] audioFloatBuffer = audioEvent.getFloatBuffer();

                mFFT.forwardTransform(audioFloatBuffer);

                mResponseAudioFloatBuffer = audioFloatBuffer.clone();

                mFFT.backwardsTransform(audioFloatBuffer);

                return true;
            }

            @Override
            public void processingFinished() {

            }
        });

        mAudioDispatcherResponse.addAudioProcessor(new AndroidAudioPlayer(mAudioDispatcherResponse.getFormat()));

        mAudioDispatcherResponse.run();
    }

    public void run(final MicrophoneEvent microphoneProcessor)
    {
        this.mMicrophoneEvent = microphoneProcessor;

        while (mAudioDispatcher == null)
        {
            //SAMPLE_RATE = SAMPLE_RATE / 2;
            Log.e("AudioDispatcherFactory", "Check Sample Rate:" + SAMPLE_RATE);
            mAudioDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, OVERLAP);
        }

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

        // FFT test
        mAudioDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {

                long startTime = System.currentTimeMillis();

                float[] audioFloatBuffer = audioEvent.getFloatBuffer();

                mFFT.forwardTransform(audioFloatBuffer);

                mFFT.multiply(audioFloatBuffer, mResponseAudioFloatBuffer);

                mFFT.backwardsTransform(audioFloatBuffer);

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
