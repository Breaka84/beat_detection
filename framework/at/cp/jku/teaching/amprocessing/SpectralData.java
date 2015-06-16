/*
 * SpectralData.java
 *
 * The container for the data of one frame of the audio file
 */
package at.cp.jku.teaching.amprocessing;

/**
 *
 * @author andreas arzt
 */
public class SpectralData {

    // contains the magnitudes of the sinusoids
    public double[] magnitudes;
    // sets the treshold for splitting the sinusoids
    public int lowFrequencyTreshold = 50;
    // contains the magnitudes of the sinusoids with low frequency
    public double[] lowMagnitudes;
    // contains the phases of the sinusoids
    public double[] phases;
    //contains the unwrapped phases of the sinusoids
    public double[] unwrappedPhases;
    //contains the total energy in this frame
    public double totalEnergy;
    // contains the energy of the low frequencies
    public double lowEnergy;
    // the size of each of the above arrays (= fftSize/2 + 1)
    public int size;

    SpectralData(double[] reBuffer, double[] imBuffer, int fftSize) {
        size = fftSize / 2 + 1;
        phases = new double[size];
        unwrappedPhases = new double[size];
        magnitudes = new double[size];
        lowMagnitudes = new double[size-lowFrequencyTreshold];

        for (int i = 0; i < size; i++) {
            magnitudes[i] = reBuffer[i];
            totalEnergy += reBuffer[i];
            phases[i] = imBuffer[i];
        }
        for (int j = magnitudes.length-1, i = 0; j > lowFrequencyTreshold; j--, i++) {
        	lowMagnitudes[i] = magnitudes[j];
        	lowEnergy += magnitudes[j];
        }
    }

    public void computeUnwrappedPhases(double[] uphases) {
        double cutoff = Math.PI;


        for (int i = 0; i < size; i++) {
            unwrappedPhases[i] = phases[i];

            double dp = phases[i] - uphases[i];
            double dps = normphase(dp);

            if (dps == -Math.PI && dp > 0) {
                dps = Math.PI;
            }
            if (Math.abs(dp) >= cutoff) {
                unwrappedPhases[i] += (dps-dp);
            }
        }
    }

    private double normphase(double ph) {
        return myfmod(ph + Math.PI, 2 * Math.PI) - Math.PI;
    }

    private double myfmod(double x, double y) {
        return x - y * Math.floor(x / y);
    }
}
