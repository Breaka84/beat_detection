package at.cp.jku.teaching.amprocessing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OnsetProcessor {

	private AudioFile audiofile;
	// this List should contain your results of the onset detection step (onset
	// times in seconds)
	private List<Double> onsets;
	// this may contain your intermediate results (in frames, before conversion
	// to time in seconds)
	private List<Integer> onsetsFrames;

    List<Double> odfValues = new ArrayList<>();
    List<Double> localMaxValues = new ArrayList<>();
    List<Double> localMeanValues = new ArrayList<>();

	public OnsetProcessor(AudioFile audiofile, List<Double> onsets, List<Integer> onsetsFrames) {
		this.onsets = onsets;
		this.onsetsFrames = onsetsFrames;
		this.audiofile = audiofile;
	}

    // This method is called from the Runner and is the starting point of your
    // onset detection / tempo extraction code
    public void analyzeOnsets() {
        System.out.println("Starting Onset Analysis...");

        //adaptiveWhitening();

        //spectralFluxL1();
        //spectralFluxL2();
        //complexDomain();
        logFiltSpecFlux();
        //highFreqContent();

        System.out.println("onsets = " + onsets.stream().map(d -> String.format("%.2f", d)).collect(Collectors.toList()));
    }

    protected void adaptiveWhitening() {
        // TODO find suitable parameters
        double m = 0.005; // memory coefficient
        double r = 0.0001; // floor parameter
        double[] P1 = null;
        for (int frame = 0; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            double[] P = new double[current.size];
            for (int k = 0; k < current.size; k++) {
                double s = current.magnitudes[k];
                P[k] = Math.max(s, r);
                if (frame > 0) {
                    P[k] = Math.max(P[k], m * P1[k]);
                }
                current.magnitudes[k] /= P[k];
            }
            P1 = P;
        }
    }

    protected void trivialPeakPicking(double threshold) {
        assert onsets.isEmpty();
        for (int n = 1; n < odfValues.size(); n++) {
            if (!minDelayExceeded(n)) {
                continue;
            }
            double odf = odfValues.get(n);
            if (odf < threshold) {
                continue;
            }
            addOnsetAt(n);
        }
    }

    protected void logFiltSpecFlux() {
    	boolean semitoneFilter = true;

        odfValues.add(0.0);

        final Function<double[], double[]> filter;
        if (semitoneFilter) {
        	filter = new SemitoneFilter(audiofile.spectralDataContainer.get(0).size);
        } else {
        	filter = Function.identity();
        }

        for (int frame = 1; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData previous = audiofile.spectralDataContainer.get(frame - 1);
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            double[] filtMag1 = filter.apply(previous.magnitudes);
            double[] filtMag = filter.apply(current.magnitudes);

            double df = 0;
            for (int b = 0; b < filtMag.length; b++) {
                double lambda = 1; // TODO find out if there's a better value
                double Xfilt = filtMag[b];
                double Xfilt1 = filtMag1[b];
                double Xlogfilt = Math.log(lambda * Xfilt + 1);
                double Xlogfilt1 = Math.log(lambda * Xfilt1 + 1);
                df += H(Math.abs(Xlogfilt) - Math.abs(Xlogfilt1));
            }

            odfValues.add(df);
        }

        peakPicking(1.0);
    }

    private void peakPicking(double threshold) {
        assert onsets.isEmpty();
        int windowMax = 9;
        int windowMean = 19;
        int w1 = windowMax / 2, w2 = windowMax / 2;
        int w3 = windowMean / 2, w4 = windowMean / 2;

        localMaxValues.add(0.0);
        localMeanValues.add(0.0);
        for (int n = 1; n < odfValues.size(); n++) {
            double odf = odfValues.get(n);
            double max = windowSubList(n, w1, w2).stream().mapToDouble(Double::doubleValue).max().getAsDouble();
            double mean = windowSubList(n, w3, w4).stream().mapToDouble(Double::doubleValue).average().getAsDouble();
            localMaxValues.add(max);
            localMeanValues.add(mean);
            if (odf != max) {
                continue;
            }
            if (odf < mean + threshold) {
                continue;
            }
            if (!minDelayExceeded(n)) {
                continue;
            }
            addOnsetAt(n);
        }
    }

    private List<Double> windowSubList(int n, int w1, int w2) {
        int min = 1;
        int max = odfValues.size() - 1;
        int from = Math.max(min, n-w1);
        int to = Math.min(max, n+w2);
        assert from <= n && to >= n;

        // if we don't have enough frames on one side to fill half of the window, extend the window to the other side
        if (from > n - w1) {
            to = Math.min(max, to + (from - (n - w1)));
        } else if (to < n + w2) {
            from = Math.max(min, from - ((n + w2) - to));
        }
        assert to - from == w1 + w2 || max - min < w1 + w2;

        assert from >= min && to <= max;
        return odfValues.subList(from, to + 1);
    }

    protected void complexDomain() {
        boolean rectify = true;

        odfValues.add(0.0);
        odfValues.add(0.0);

        for (int frame = 2; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData previous2 = audiofile.spectralDataContainer.get(frame - 2);
            SpectralData previous1 = audiofile.spectralDataContainer.get(frame - 1);
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            double rcd = 0;
            for (int k = 0; k < previous1.size; k++) {
                // Xt(n,k) = |X(n-1,k)| * e^(φ(n-1,k) + φ'(n-1,k))
                double xt = Math.abs(previous1.magnitudes[k]) * Math.exp(previous1.phases[k] + previous1.phases[k] - previous2.phases[k]);
                double x = current.magnitudes[k];
                double cd = Math.abs(x - xt);
                rcd += !rectify || (Math.abs(x) >= Math.abs(xt)) ? cd : 0.0;
            }

            odfValues.add(rcd);
        }

        peakPicking(2.5);
    }

    protected void spectralFluxL1() {
        odfValues.add(0.0);

        for (int frame = 1; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData previous = audiofile.spectralDataContainer.get(frame - 1);
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            double df = current.totalEnergy - previous.totalEnergy;

            odfValues.add(df);
        }

        peakPicking(3);
    }

    protected void spectralFluxL2() {
        // spectral flux using L^2-norm on rectified distance
        odfValues.add(0.0);

        for (int frame = 1; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData previous = audiofile.spectralDataContainer.get(frame - 1);
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            double sd = 0;
            for (int k = 0; k < previous.size; k++) {
                sd += square(H(current.magnitudes[k] - previous.magnitudes[k]));
            }
            sd = Math.sqrt(sd);

            odfValues.add(sd);
        }

        peakPicking(0.3);
    }

    protected void highFreqContent() {
        boolean square = false;
        odfValues.add(0.0);

        for (int frame = 1; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            double e = 0;
            for (int k = 0; k < current.size; k++) {
                double wk = k; // weight = k; not psychoacoustically informed...
                double xk = current.magnitudes[k];
                if (square) xk *= xk;
                e += wk * xk;
            }
            e /= current.size;

            odfValues.add(e);
        }

        peakPicking(0.2);
    }

    @Deprecated
    protected void onsetsSimple() {
        // This is a very simple kind of Onset Detector... You have to implement
        // at least 2 more different onset detection functions
        // have a look at the SpectralData Class - there you can also access the
        // magnitude and the phase in each FFT bin...

        for (int frame = 1; frame < audiofile.spectralDataContainer.size(); frame++) {
            SpectralData previous = audiofile.spectralDataContainer.get(frame - 1);
            SpectralData current = audiofile.spectralDataContainer.get(frame);

            // this does not even do smoothing, it just looks for large, finite, first order differences
            double df = current.totalEnergy - previous.totalEnergy;

            if (df > 15) {
                onsetsFrames.add(frame);
                onsets.add(frame * audiofile.hopTime);
            }
        }
    }

    private boolean minDelayExceeded(int frame) {
        final int minDelay = 5;
        int lastFrame = onsetsFrames.isEmpty() ? -minDelay : onsetsFrames.get(onsetsFrames.size()-1);
        return frame - lastFrame > minDelay;
    }

    private static double square(double x) {
        return x * x;
    }

    /**
     * Rectify, i.e. zero for negative x;
     */
    private static double H(double x) {
        return (x + Math.abs(x)) / 2;
    }

    private void addOnsetAt(int frame) {
        onsetsFrames.add(frame);
        onsets.add(frame * audiofile.hopTime);
    }

    public class SemitoneFilter implements Function<double[], double[]> {
        private static final int SAMPLE_RATE = 44100;
		private static final int BANDS = 12;
        private final int bincount;
        private final double[][] weights;
        private final int[] binspans;

        public SemitoneFilter(int fftSize) {
            // calculate semitone frequencies within (lbound, rbound) Hz
            final double lbound = 20, rbound = 18000;
            final double a1 = 440; // Hz

            // octave is factor 2 apart, therefore:
            // x ^ 12 = 2 => x = 12th root of 2
            double perbandfactor = Math.pow(2.0, 1.0 / BANDS);
            assert perbandfactor > 1.0 && perbandfactor < 2.0;

            // calculate semitone frequencies starting from a1 (440Hz)
            List<Double> stf = new ArrayList<>();
            for (double f = a1; f > lbound; f /= perbandfactor) {
                stf.add(0, f);
            }
            for (double f = a1 * perbandfactor; f < rbound; f *= perbandfactor) {
                stf.add(f);
            }

            // now that we've got the frequencies, we need to map them to fft bins somehow
            double halfsamplerate = SAMPLE_RATE / 2;
            double fftbinwidth = halfsamplerate / fftSize;
            List<Integer> binmapping = stf.stream().map(f -> (int) (f / fftbinwidth)).distinct().collect(Collectors.toList());

            int binedgecount = binmapping.size();
            bincount = binedgecount - 2;

            weights = new double[fftSize][];
            Arrays.setAll(weights, i -> new double[bincount]);
            binspans = new int[bincount];

            // calculate triangular weights for each bin
            for (int i = 1; i < binedgecount - 1; i++) {
                int binstart = binmapping.get(i-1);
                int binmid = binmapping.get(i);
                int binend = binmapping.get(i+1);
                int binspan = binend - binstart;
                int binindex = i-1;
                for (int k = binstart; k < binend; k++) {
                    double midval = 1.0;
                    double halfbinspan = binspan / 2.0;
                    double w_kb = binmid == k ? midval : midval * Math.abs(halfbinspan - Math.abs(binmid - k)) / halfbinspan;
                    assert w_kb >= 0.0 && w_kb <= midval : w_kb;
                    weights[k][binindex] = w_kb;
                    //System.out.printf("k=%d,b=%d; w_kb=%f\n", k, binindex, w_kb);
                }
                binspans[binindex] = binspan;
            }
        }

        public int getBinCount() {
            return bincount;
        }

        @Override
        public double[] apply(double[] magnitudes) {
            // weighted sum of magnitudes per bin
            double[] result = new double[getBinCount()];
            for (int k = 0; k < magnitudes.length; k++) {
                double mag = magnitudes[k];
                double[] wk = weights[k];
                for (int b = 0; b < wk.length; b++) {
                    result[b] += wk[b] * mag;
                }
            }
            return result;
        }
    }
}
