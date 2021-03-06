/*
 * Processor.java
 *
 * This is the class where you can implement your onset detection / tempo extraction methods
 * Of course you may also define additional classes.
 */
package at.cp.jku.teaching.amprocessing;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.security.util.Length;

/**
 *
 * @author andreas arzt
 */
public class Processor {

	private AudioFile audiofile;
	// this List should contain your results of the onset detection step (onset
	// times in seconds)
	private List<Double> onsets;
	// this may contain your intermediate results (in frames, before conversion
	// to time in seconds)
	private List<Integer> onsetsFrames;
	// this variable should contain your result of the tempo estimation
	// algorithm
	private double tempo;
    // TempoHypothesis with highest score (also needed for beats analysis)
	private TempoHypothesis tempoHypothesisWithMaxScore;
	// this List should contain your results of the beat detection step (beat
	// times in seconds)
	private List<Double> beats;
	// this may contain your intermediate beat results (in frames, before
	// conversion to time in seconds)
	private List<Integer> beatsFrames;
	// min and max tempo to be considered
	private int bpmMinimum, bpmMaximum;

	// original onsets (currently from the groundtruth file)
	LinkedList<Double> detectedOnsets = new LinkedList<Double>();

	public Processor(String filename) {
		System.out.println("Initializing Processor...");

		this.onsets = new LinkedList<Double>();
		this.onsetsFrames = new LinkedList<Integer>();
		this.beats = new LinkedList<Double>();
		this.beatsFrames = new LinkedList<Integer>();

		System.out.println("Reading Audio-File " + filename);
		System.out.println("Performing FFT...");
		// an AudioFile object is created with the following Paramters:
		// AudioFile(WAVFILENAME, FFTLENGTH in seconds, HOPLENGTH in seconds)
		// if you would like to work with multiple resolutions you simple create
		// multiple AudioFile objects with different parameters
		// given an audio file with 44.100 Hz the parameters below translate to
		// an FFT with size 2048 points
		// Note that the value is not taken to be precise; it is adjusted so
		// that the FFT Size is always power of 2.
		this.audiofile = new AudioFile(filename, 0.046439, 0.01);
		// this starts the extraction of the basis features (the STFT)
		this.audiofile.processFile();
	}

	// This method is called from the Runner and is the starting point of your
	// onset detection / tempo extraction code
	public void analyze(String onsetGroundTruthFileName) {


		System.out.println("Running Analysis...");

		analyzeOnsets();

		analyzeTempo();

		analyzeBeats();



	}

	private void analyzeTempo() {

		System.out.println("Starting Tempo Analysis...");

		bpmMinimum = 40;
		bpmMaximum = 230;


		// IOIs (rounded to nearest 10ms) in ms
        LinkedList<Integer> detectedOnsetsIOIs = new LinkedList<Integer>();

        // IOIs filtered by bpmMaximum and pbmMinimum in ms
        LinkedList<Integer> detectedOnsetsIOIsFiltered = new LinkedList<Integer>();

        // List of most frequent IOIs in ms with their number of occurrences
        ArrayList<int[]> mostFrequentIOIs = new ArrayList<int[]>();

		// List of all potential IOIs in ms for TempoHypothesises
        ArrayList<Integer> potentialIOIsForTempoHypothesis = new ArrayList<Integer>();
        
        // Lenght of OnsetList
        int lenghtOfOnsets;

        // Score of TempoHypothesis with highest score
        int tempoHypothesisWithMaxScoreInPoints;
        
        for (double onset : onsets) {
        	detectedOnsets.add(onset);
        }
        
        lenghtOfOnsets = detectedOnsets.size();

        for (int i =1; i < detectedOnsets.size(); i++) {
        	int ioi = (int)Math.round((detectedOnsets.get(i)-detectedOnsets.get(i-1))*100)*10;
        	detectedOnsetsIOIs.add(ioi);
        }

        // Filtering the Onset IOIs by BPM Min & Max
        detectedOnsetsIOIsFiltered = filterLinkedList(detectedOnsetsIOIs, ((60*1000)/bpmMaximum)/2, (60*1000)/bpmMinimum);

        // Get the most frequent IOIs
        mostFrequentIOIs = calculateMostFrequentOccurrences(detectedOnsetsIOIsFiltered, 5);

        // Collecting potential IOIs with their multiples
        for (int[] ioi : mostFrequentIOIs) {
        	if (ioi[0] > (60*1000)/bpmMaximum && ioi[0] < (60*1000)/bpmMinimum) {
        		potentialIOIsForTempoHypothesis.add(ioi[0]);
        	}
        	potentialIOIsForTempoHypothesis.addAll(getMultiples(ioi[0],((60*1000)/bpmMaximum), (60*1000)/bpmMinimum));
        }
        potentialIOIsForTempoHypothesis.removeIf(ioi -> ioi == 0);
        
        

        // Generating TempoHypothesises from potential IOIs with startIndex from 0 to 7
        ArrayList<TempoHypothesis> tempoHypothesisContainer = new ArrayList<TempoHypothesis>();
        for (int potentialIOI : potentialIOIsForTempoHypothesis) {
        	for (int i = 0; i < 8 && i < lenghtOfOnsets; i++) {
        		tempoHypothesisContainer.add(new TempoHypothesis(detectedOnsets, potentialIOI, i));
        	}
        }

        // Processing all TempoHypothesises
        for (TempoHypothesis currentHypothesis : tempoHypothesisContainer) {
        	currentHypothesis.process();
        }

        // Finding TempoHypothesis with highest Score
        tempoHypothesisWithMaxScore         = tempoHypothesisContainer.get(0);
        tempoHypothesisWithMaxScoreInPoints = 0;
        for (int i = 0; i < tempoHypothesisContainer.size(); i++) {
        	TempoHypothesis currentHypothesis = tempoHypothesisContainer.get(i);
        	if (currentHypothesis.getScore() > tempoHypothesisWithMaxScoreInPoints) {
        		tempoHypothesisWithMaxScore		    = currentHypothesis;
        		tempoHypothesisWithMaxScoreInPoints = currentHypothesis.getScore();
        	}
        }

        tempo = tempoHypothesisWithMaxScore.getTempo();
	}

	private void analyzeBeats() {

		ArrayList<TempoHypothesis> tempoHypothesisContainer = new ArrayList<TempoHypothesis>();

		System.out.println("Starting Beats Analysis...");
		beats.clear();

		TempoHypothesis tempoHypothesisForBeats = null;
		int minMissedOnsets = 99999;

		for (int i = 0; i < 7 && i < detectedOnsets.size()-1; i++) {
			tempoHypothesisContainer.add(new TempoHypothesis(detectedOnsets, (60/(tempoHypothesisWithMaxScore.getTempo())*1000), i));
		}
		
		tempoHypothesisContainer.forEach(hypothesis -> hypothesis.process());
		
		tempoHypothesisContainer.removeIf(hypothesis -> hypothesis.getBeats().isEmpty());
		
		for (TempoHypothesis hypothesis : tempoHypothesisContainer) {
			if (hypothesis.getMissedOnsets() < minMissedOnsets) {
				minMissedOnsets         = hypothesis.getMissedOnsets();
				tempoHypothesisForBeats = hypothesis;
			}
		}

		for (double beatTime : tempoHypothesisForBeats.getBeats()) {
			//System.out.println(beatTime/1000);
			beats.add(beatTime/1000);
		}
	}

	private void analyzeOnsets() {
		new OnsetProcessor(audiofile, onsets, onsetsFrames).analyzeOnsets();
	}


    private static int[] calculateMostFrequentOccurrences(LinkedList<Integer> inputList) {
    	int[] mostFrequentElement = {0,0};

    	for (int element : inputList) {
    		int tempCount = 0;

    		for (int i = 0; i < inputList.size(); i++) {
    			if (inputList.get(i) == element) {
    				tempCount += 1;
    			}
    		}

    		if (tempCount > mostFrequentElement[1]) {
    			mostFrequentElement[0] = element;
    			mostFrequentElement[1] = tempCount;
    		}
    	}
    	return mostFrequentElement;
    }

    private static ArrayList<int[]> calculateMostFrequentOccurrences(LinkedList<Integer> inputList, int numberOfMostFrequentElements) {
    	ArrayList<int[]> listToReturn 			= new ArrayList<int[]>();
    	LinkedList<Integer> tempCopyOfInputList = new LinkedList<Integer>();
    	for (Integer element : inputList) {
    		tempCopyOfInputList.add(element);
    	}

    	for (int i = 0; i < numberOfMostFrequentElements; i++) {
    		int[] mostFrequentElement = calculateMostFrequentOccurrences(tempCopyOfInputList);
    		listToReturn.add(mostFrequentElement);
    		tempCopyOfInputList.removeIf(element -> element == mostFrequentElement[0]);
    	}
    	listToReturn.removeIf(element -> element[0] == 0);
    	return listToReturn;
    }


	private static LinkedList<Integer> filterLinkedList(LinkedList<Integer> inputList, int minimum, int maximum) {
		LinkedList<Integer> listToReturn = new LinkedList<Integer>();
		for (int element : inputList) {
			listToReturn.add(element);
		}
		listToReturn.removeIf(element -> element < minimum || element > maximum);
		return listToReturn;
	}

	private ArrayList<Integer> getMultiples(int input, int min, int max) {
		ArrayList<Integer> arrayToReturn = new ArrayList<Integer>();

		for (int i = 2; input/i > min; i++) {
			if (input/i < max && input/i > min) { arrayToReturn.add(input/i); }
			//if (input/i*(i+1) < max && input/i*(i+1) > min) { arrayToReturn.add(input/i*(i+1)); }
		}
		for (int i = 2; input*i < max; i++) {
			if (input*i < max && input*i > min) { arrayToReturn.add(input*i); }
			//if (input*i/(i+1) < max && input*i/(i+1) > min) { arrayToReturn.add(input*i/(i+1)); }
		}
		return arrayToReturn;
	}


	public List<Double> getOnsets() {
		return onsets;
	}

	public double getTempo() {
		return tempo;
	}

	public List<Double> getBeats() {
		return beats;
	}
}
