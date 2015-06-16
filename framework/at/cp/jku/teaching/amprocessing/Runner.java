/*
 * Runner.java
 * contains the main method, handles the input parameters, writes the results to files and evaluates the results
 * if possible, do not change anything in this file
 * 
 */
package at.cp.jku.teaching.amprocessing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * @author andreas arzt
 */
public class Runner {

    /*
     * Options:
     * -i WAVFILENAME (the file you want to analyze)
     * -d DIR (the directory in which the 2 resultfiles (WAVFILENAME.onsets and WAVEFILENAME.tempo) are written to
     * -o ONSETGROUNDTRUTHFILE (the file including the onset groundtruth, optional!)
     * -t TEMPOGROUNDTRUTHFILE (the file including the tempo groundtruth, optional!)
     * -b BEATGROUNDTRUTHFILE (the file including the beat groundtruth, optional!)
     *
     */
    public static void main(String[] args) {
        String wavFileName = new String();
        String shortWavFileName = new String();
        String outputDirectory = new String();
        String outputOnsetsFileName = new String();
        String outputTempoFileName = new String();
        String outputBeatsFileName = new String();
        String onsetGroundTruthFileName = new String();
        String tempoGroundTruthFileName = new String();
        String beatGroundTruthFileName = new String();
        boolean hasOnsetGroundTruth = false;
        boolean hasTempoGroundTruth = false;
        boolean hasBeatGroundTruth = false;

        OptionParser parser = new OptionParser("i:d:o:t:b:");
        OptionSet options = parser.parse(args);

        if (!options.has("i")) {
            System.out.println("Inputfile required! (-i INPUTFILE)");
            System.exit(1);
        }

        if (!options.has("d")) {
            System.out.println("Output Directory required! (-d OUTPUTDIR)");
            System.exit(1);
        }

        wavFileName = options.valueOf("i").toString();
        outputDirectory = options.valueOf("d").toString();

        System.out.println(outputDirectory);

        File dir = new File(outputDirectory);
        if (!dir.exists()) {
            System.out.println("Output directory does not exist!");
            System.exit(1);
        }

        if (!outputDirectory.endsWith("/")) {
            outputDirectory = outputDirectory + "/";
        }

        shortWavFileName = wavFileName.substring(wavFileName.lastIndexOf("/") + 1, wavFileName.lastIndexOf("."));
        outputOnsetsFileName = outputDirectory + shortWavFileName + ".onsets";
        outputTempoFileName = outputDirectory + shortWavFileName + ".bpms";
        outputBeatsFileName = outputDirectory + shortWavFileName + ".beats";

        if (options.has("o")) {
            onsetGroundTruthFileName = options.valueOf("o").toString();
            hasOnsetGroundTruth = true;
        }

        if (options.has("t")) {
            tempoGroundTruthFileName = options.valueOf("t").toString();
            hasTempoGroundTruth = true;
        }

        if (options.has("b")) {
            beatGroundTruthFileName = options.valueOf("b").toString();
            hasBeatGroundTruth = true;
        }

        Processor p = new Processor(wavFileName);
        p.analyze(onsetGroundTruthFileName);

        System.out.println();
        System.out.println("Outputting Onset Times to " + outputOnsetsFileName + "...");
        writeDataToFile(p.getOnsets(), outputOnsetsFileName);
        System.out.println("Outputting Tempo to " + outputTempoFileName + "...");
        writeDataToFile(p.getTempo(), outputTempoFileName);
        System.out.println("Outputting Beat Times to " + outputBeatsFileName + "...");
        writeDataToFile(p.getBeats(), outputBeatsFileName);


        if (hasOnsetGroundTruth) {
            String onsetEvalOut = outputDirectory + shortWavFileName + ".onsets.eval";
            evaluateOnsets(p.getOnsets(), onsetGroundTruthFileName, onsetEvalOut);
        }
        if (hasTempoGroundTruth) {
            String tempoEvalOut = outputDirectory + shortWavFileName + ".bpms.eval";
            evaluateTempo(p.getTempo(), tempoGroundTruthFileName, tempoEvalOut);
        }
        if (hasBeatGroundTruth) {
            String beatEvalOut = outputDirectory + shortWavFileName + ".beats.eval";
            evaluateBeats(p.getBeats(), beatGroundTruthFileName, beatEvalOut);
        }
    }

    /*
     * Simple Fileout Method for LinkedList<Double>
     */
    private static void writeDataToFile(List<Double> data, String filename) {
        try {
            FileWriter outputwriter = new FileWriter(filename);
            for (Double d : data) {
                outputwriter.write(d + "\n");
            }
            outputwriter.flush();
            outputwriter.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /*
     * Simple Fileout Method for a single double (the tempo...)
     */
    private static void writeDataToFile(double data, String filename) {
        try {
            FileWriter outputwriter = new FileWriter(filename);
            outputwriter.write(data + "\n");
            outputwriter.flush();
            outputwriter.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    // Evaluate the Onset Estimations
    private static void evaluateOnsets(List<Double> onsets, String onsetGroundTruthFileName, String onsetEvalOut) {

        int TP = 0;
        int FP = 0;
        int FN = 0;
        double precision = 0;
        double recall = 0;
        double fmeasure = 0;

        LinkedList<Double> groundtruthOnsets = new LinkedList<Double>();
        LinkedList<Double> groundtruthOnsetsRaw = new LinkedList<Double>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(onsetGroundTruthFileName));
            String line;

            while ((line = reader.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);
                groundtruthOnsetsRaw.add(Double.parseDouble(st.nextToken()));
            }
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }

        for (int i = 0; i < groundtruthOnsetsRaw.size(); i++) {
            if (groundtruthOnsets.size() == 0) {
                groundtruthOnsets.add(groundtruthOnsetsRaw.get(i));
            }
            if (groundtruthOnsetsRaw.get(i) - groundtruthOnsets.getLast() >= 0.05) {
                groundtruthOnsets.add(groundtruthOnsetsRaw.get(i));
            } else {
                double mean = (groundtruthOnsetsRaw.get(i) + groundtruthOnsets.getLast()) / 2;
                groundtruthOnsets.remove(groundtruthOnsets.size() - 1);
                groundtruthOnsets.add(mean);
            }
        }

        Iterator<Double> it = groundtruthOnsets.iterator();
        while (it.hasNext()) {
            double d = it.next();
            double tmp = findNearest(d, onsets);
            if (Math.abs(d - tmp) <= 0.05) {
                onsets.remove(tmp);
                it.remove();
                TP++;
            }

        }
        FN = groundtruthOnsets.size();
        FP = onsets.size();

        precision = (double) TP / (TP + FP);
        recall = (double) TP / (TP + FN);
        fmeasure = (2 * precision * recall) / (precision + recall);

        StringBuilder sb = new StringBuilder();
        sb.append("TP: ");
        sb.append(TP);
        sb.append("\n");
        sb.append("FP: ");
        sb.append(FP);
        sb.append("\n");
        sb.append("FN: ");
        sb.append(FN);
        sb.append("\n");
        sb.append("Precision: ");
        sb.append(precision);
        sb.append("\n");
        sb.append("Recall: ");
        sb.append(recall);
        sb.append("\n");
        sb.append("F-Measure: ");
        sb.append(fmeasure);

        System.out.println("\nOnset Evaluation: \n" + sb.toString());
        System.out.println("Outputting Onset Evaluation to " + onsetEvalOut);
        try {
            FileWriter outputwriter = new FileWriter(onsetEvalOut);
            outputwriter.append(sb.toString());
            outputwriter.flush();
            outputwriter.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static double findNearest(double val, List<Double> list) {
        double minDist = Double.MAX_VALUE;
        double retval = 0;

        for (Double d : list) {
            if (Math.abs(val - d) < minDist) {
                minDist = Math.abs(val - d);
                retval = d;
            }
        }
        return retval;

    }

    // Evaluate the Tempo Estimation
    private static void evaluateTempo(double tempo, String tempoGroundTruthFileName, String tempoEvalOut) {
        double gtempo = 0;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(tempoGroundTruthFileName));
            String line;

            line = reader.readLine();
            StringTokenizer st = new StringTokenizer(line);

            double tempo1 = Double.parseDouble(st.nextToken());
            if (st.hasMoreElements()) {
                double tempo2 = Double.parseDouble(st.nextToken());
                double perc = Double.parseDouble(st.nextToken());

                if (perc >= 0.5) {
                    gtempo = tempo1;
                } else {
                    gtempo = tempo2;
                }
            } else {
                gtempo = tempo1;
            }
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }

        boolean correctTempo = false;
        boolean multipleTempo = false;

        if (gtempo * 1.04 > tempo && gtempo * 0.96 < tempo) {
            correctTempo = true;
            multipleTempo = true;
        } else {
            LinkedList<Double> multTempi = new LinkedList<Double>();
            multTempi.add(gtempo * 2);
            multTempi.add(gtempo / 2);
            multTempi.add(gtempo * 3);
            multTempi.add(gtempo / 3);

            for (Double d : multTempi) {
                if (d * 1.04 > tempo && d * 0.96 < tempo) {
                    multipleTempo = true;
                }
            }
        }

        System.out.println("\nTempo Evaluation:");
        System.out.println("Correct Tempo:" + gtempo + " Estimated Tempo: " + tempo);
        System.out.println("Correct Tempo found: " + correctTempo);
        System.out.println("Multiple of Correct Tempo found: " + multipleTempo);

        System.out.println("Outputting Tempo Evaluation to " + tempoEvalOut);
        try {
            FileWriter outputwriter = new FileWriter(tempoEvalOut);
            if (correctTempo) {
                outputwriter.append("1 ");
            } else {
                outputwriter.append("0 ");
            }

            if (multipleTempo) {
                outputwriter.append("1");
            } else {
                outputwriter.append("0");
            }

            outputwriter.flush();
            outputwriter.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    // Evaluate the Beat Estimations
    private static void evaluateBeats(List<Double> beats, String beatGroundTruthFileName, String beatEvalOut) {
        int TP = 0;
        int FP = 0;
        int FN = 0;
        double precision = 0;
        double recall = 0;
        double fmeasure = 0;

        LinkedList<Double> groundtruthBeats = new LinkedList<Double>();
        LinkedList<Double> groundtruthBeatsRaw = new LinkedList<Double>();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(beatGroundTruthFileName));
            String line;

            while ((line = reader.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line);
                groundtruthBeatsRaw.add(Double.parseDouble(st.nextToken()));
            }
            reader.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }

        for (int i = 0; i < groundtruthBeatsRaw.size(); i++) {
            if (groundtruthBeats.size() == 0) {
                groundtruthBeats.add(groundtruthBeatsRaw.get(i));
            }
            if (groundtruthBeatsRaw.get(i) - groundtruthBeats.getLast() >= 0.05) {
                groundtruthBeats.add(groundtruthBeatsRaw.get(i));
            } else {
                double mean = (groundtruthBeatsRaw.get(i) + groundtruthBeats.getLast()) / 2;
                groundtruthBeats.remove(groundtruthBeats.size() - 1);
                groundtruthBeats.add(mean);
            }
        }

        Iterator<Double> it = groundtruthBeats.iterator();
        while (it.hasNext()) {
            double d = it.next();
            double tmp = findNearest(d, beats);
            if (Math.abs(d - tmp) <= 0.05) {
                beats.remove(tmp);
                it.remove();
                TP++;
            }

        }
        FN = groundtruthBeats.size();
        FP = beats.size();

        precision = (double) TP / (TP + FP);
        recall = (double) TP / (TP + FN);
        fmeasure = (2 * precision * recall) / (precision + recall);

        StringBuilder sb = new StringBuilder();
        sb.append("TP: ");
        sb.append(TP);
        sb.append("\n");
        sb.append("FP: ");
        sb.append(FP);
        sb.append("\n");
        sb.append("FN: ");
        sb.append(FN);
        sb.append("\n");
        sb.append("Precision: ");
        sb.append(precision);
        sb.append("\n");
        sb.append("Recall: ");
        sb.append(recall);
        sb.append("\n");
        sb.append("F-Measure: ");
        sb.append(fmeasure);

        System.out.println("\nBeat Evaluation: \n" + sb.toString());
        System.out.println("Outputting Beat Evaluation to " + beatEvalOut);
        try {
            FileWriter outputwriter = new FileWriter(beatEvalOut);
            outputwriter.append(sb.toString());
            outputwriter.flush();
            outputwriter.close();
        } catch (IOException ex) {
            Logger.getLogger(Runner.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
