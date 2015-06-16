/*
 * AudioFile.java
 * Representation of an Audio File
 *
 * After Initialization you have to call the process() method to do the feature extraction
 *
 * The most important variables for you are
 *   - LinkedList<SpectralData> spectralDataContainer (contains the data of the STFT)
 *   - LinkedList<Double> sampleDataContainer (contains the samples in the time domain)
 * also:
 *   - double fftTime (in seconds, contains the size of the window which you set via the constructor)
 *   - double hopTime (in seconds, contains the hoptime (also set via the constructor)
 *
 * hopSize and fftSize are automatically computed from the fftTime and the hopTime.
 * the fftsize is always a power of 2!
 *
 * the signal is windowed using a hamming window
 *
 */
package at.cp.jku.teaching.amprocessing;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 *
 * @author andreas arzt
 */
public class AudioFile {

    private AudioInputStream rawInputStream;
    private AudioFormat audioFormat;
    private int channels;
    private float sampleRate;
    private AudioInputStream pcmInputStream;
    private int hopSize;
    private int fftSize;
    private byte[] inputBuffer;
    private double[] circBuffer;
    private double[] reBuffer;
    private double[] imBuffer;
    private int cbIndex;
    private double[] window;
    private double frameRMS;
    private int frameCount;
    
    public double fftTime;
    public double hopTime;
    // Contains the Spectral Data (Magnitude, Phase, Unwrapped Phase) for each Frame
    public LinkedList<SpectralData> spectralDataContainer;
    // Contains the value of each sample
    public LinkedList<Double> sampleDataContainer;

    public AudioFile(String filename, double fftTime, double hopTime) {
        this.fftTime = fftTime;
        this.hopTime = hopTime;
        try {
            File audioFile = new File(filename);

            if (!audioFile.isFile()) {
                throw new FileNotFoundException(
                        "Requested file does not exist: " + filename);
            }
            rawInputStream = AudioSystem.getAudioInputStream(audioFile);
            audioFormat = rawInputStream.getFormat();
            channels = audioFormat.getChannels();
            sampleRate = audioFormat.getSampleRate();
            pcmInputStream = rawInputStream;
            if ((audioFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED)
                    || (audioFormat.getFrameSize() != channels * 2)
                    || audioFormat.isBigEndian()) {
                AudioFormat desiredFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16,
                        channels, channels * 2, sampleRate, false);
                pcmInputStream = AudioSystem.getAudioInputStream(desiredFormat,
                        rawInputStream);
                audioFormat = desiredFormat;
            }



            hopSize = (int) Math.round(sampleRate * hopTime);
            this.hopTime = hopSize/sampleRate;
            fftSize = (int) Math.round(Math.pow(2,
                    Math.round(Math.log(fftTime * sampleRate) / Math.log(2))));

            int buffSize = hopSize * channels * 2;
            if ((inputBuffer == null) || (inputBuffer.length != buffSize)) {
                inputBuffer = new byte[buffSize];
            }
            if ((circBuffer == null) || (circBuffer.length != fftSize)) {
                circBuffer = new double[fftSize];
                reBuffer = new double[fftSize];
                imBuffer = new double[fftSize];
                window = FFT.makeWindow(FFT.HAMMING, fftSize, fftSize);
                for (int i = 0; i < fftSize; i++) {
                    window[i] *= Math.sqrt(fftSize);
                }
            }


            frameCount = 0;


            cbIndex = 0;
            frameRMS = 0;

            spectralDataContainer = new LinkedList<SpectralData>();
            sampleDataContainer = new LinkedList<Double>();


        } catch (Exception e) {
        }



    }

    /** Reads a frame of input data, averages the channels to mono, scales
     *  to a maximum possible absolute value of 1, and stores the audio data
     *  in a circular input buffer.
     *  @return true if a frame (or part of a frame, if it is the final frame)
     *  is read. If a complete frame cannot be read, the InputStream is set
     *  to null.
     */
    private boolean getFrame() {
        if (pcmInputStream == null) {
            return false;
        }
        try {
            int bytesRead = (int) pcmInputStream.read(inputBuffer);


            if (bytesRead < inputBuffer.length) {
                System.out.println("End of input.\n");
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        frameRMS = 0;
        double sample;
        switch (channels) {
            case 1:
                for (int i = 0; i < inputBuffer.length; i += 2) {
                    sample = ((inputBuffer[i + 1] << 8)
                            | (inputBuffer[i] & 0xff)) / 32768.0;
                    frameRMS += sample * sample;
                    circBuffer[cbIndex++] = sample;
                    sampleDataContainer.add(sample);
                    if (cbIndex == fftSize) {
                        cbIndex = 0;
                    }
                }
                break;
            case 2: // saves ~0.1% of RT (total input overhead ~0.4%) :)
                for (int i = 0; i < inputBuffer.length; i += 4) {
                    sample = (((inputBuffer[i + 1] << 8) | (inputBuffer[i] & 0xff))
                            + ((inputBuffer[i + 3] << 8) | (inputBuffer[i + 2] & 0xff))) / 65536.0;
                    frameRMS += sample * sample;
                    circBuffer[cbIndex++] = sample;
                    sampleDataContainer.add(sample);
                    if (cbIndex == fftSize) {
                        cbIndex = 0;
                    }
                }
                break;
            default:
                for (int i = 0; i < inputBuffer.length;) {
                    sample = 0;
                    for (int j = 0; j < channels; j++, i += 2) {
                        sample += (inputBuffer[i + 1] << 8) | (inputBuffer[i] & 0xff);
                    }
                    sample /= 32768.0 * channels;
                    frameRMS += sample * sample;
                    circBuffer[cbIndex++] = sample;
                    sampleDataContainer.add(sample);
                    if (cbIndex == fftSize) {
                        cbIndex = 0;
                    }
                }
        }
        frameRMS = Math.sqrt(frameRMS / inputBuffer.length);
        return true;
    }

    /** Processes the Audio File
     * Reads Frames, computes the STFT and inserts Data into the spectral Container Object until EOF
     */
    public void processFile() {

        while (getFrame()) {
            for (int i = 0; i < fftSize; i++) {
                reBuffer[i] = window[i] * circBuffer[cbIndex];
//                reBuffer[i] = circBuffer[cbIndex];
                if (++cbIndex == fftSize) {
                    cbIndex = 0;
                }
            }
            Arrays.fill(imBuffer, 0);
            FFT.magnitudePhaseFFT(reBuffer, imBuffer);
            SpectralData s = new SpectralData(reBuffer, imBuffer, fftSize);
            spectralDataContainer.add(s);


            frameCount++;
            /**
            if ((frameCount % 100) == 0) {
                System.out.println("Progress:" + frameCount);

            }
            **/

        }

        for(int i = 0; i < spectralDataContainer.size(); i++) {
            if(i == 0) {
                Arrays.fill(spectralDataContainer.get(i).unwrappedPhases, 0);
                continue;
            }
            spectralDataContainer.get(i).computeUnwrappedPhases(spectralDataContainer.get(i-1).unwrappedPhases);
        }
    }
}
