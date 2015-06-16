package at.cp.jku.teaching.amprocessing;

import java.util.ArrayList;
import java.util.LinkedList;

public class TempoHypothesis {
	private ArrayList<Double> onsets = new ArrayList<Double>();
	private ArrayList<Double> beats = new ArrayList<Double>();
	private int hitOnsets, missedOnsets, steps, startIndex;
	private double currentIBIGuess, cumulatedBeatTimeIntervals, initialIBIGuess;
	
	
	public TempoHypothesis(LinkedList<Double> inputList, double initialIBIGuess, int startIndex) {
		this.startIndex 	 = startIndex;
		this.initialIBIGuess = initialIBIGuess;
		this.currentIBIGuess = initialIBIGuess;
		// Converting onsets to ms and round them to 10ms
		for (double element : inputList) {
			onsets.add(element*1000);
		}
	}
	
	public void process() {
		steps 					   = 0;
		currentIBIGuess 		   = initialIBIGuess;
		cumulatedBeatTimeIntervals = 0;
		beats.clear();
		
		// loop for 28000 ms (28 seconds)
		for (double currentBeatTime = onsets.get(startIndex); currentBeatTime < 28000;) {
			steps++;
			double nextBeatTimeGuess = 0;
			double nextBeatTime      = 0;
			beats.add(currentBeatTime);
			
			// guess when the next beat should happen
			nextBeatTimeGuess = currentBeatTime + currentIBIGuess;
						
			// get nearest Onset (+/- 0.5% of nextBeatTimeGuess)
			double nearestOnset = getNearestOnset(nextBeatTimeGuess);
			
			//  If no near Onset is found, take guessed BeatTime
			if (nearestOnset == -1) {
				nextBeatTime  = nextBeatTimeGuess;
				missedOnsets += 1;
			} else {
				nextBeatTime = nearestOnset;
				hitOnsets   += 1;
			}

			cumulatedBeatTimeIntervals += nextBeatTime - currentBeatTime;
			
			// Redefining the IBI of the hypothesis by the average of all previous IBIs
			currentIBIGuess = getAverageBeatInterval();
			currentBeatTime = nextBeatTime;

			/**
			if (currentBeatTimeIntervalGuess < 100) {
				break;
			}
			**/
		}
	}
	
	public double getTempo() {
		return 60/(getAverageBeatInterval()/1000);
	}
	
	public double getInitialBeatTimeIntervalGuess() {
		return initialIBIGuess;
	}
	
	public int getScore() {
		return (hitOnsets*hitOnsets) - (missedOnsets*missedOnsets);
	}
	
	public int getMissedOnsets() {
		return missedOnsets;
	}
	
	public ArrayList<Double> getBeats() {
		return beats;
	}
	
	//////// private methods ////////////////
	
	private double getAverageBeatInterval() {
		return cumulatedBeatTimeIntervals/steps;
	}
	
	private double getNearestOnset(double guessedBeatTime) {
		ArrayList<Double> nearOnsets = new ArrayList<Double>();
		
		for (double onset : onsets) {
			if (onset < guessedBeatTime*1.005 && onset > guessedBeatTime*0.995) {
				nearOnsets.add(onset);
			}
		}
		
		double[] proximity = {0,1000};
		
		for (double element : nearOnsets) {
			if (Math.abs(element - guessedBeatTime) < proximity[1]) {
				proximity[0] = element;
				proximity[1] = Math.abs(element - guessedBeatTime);
			}
		}
		
		if (nearOnsets.isEmpty()) { return -1; }
		return proximity[0];
	}

}
