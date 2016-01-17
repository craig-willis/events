    package edu.gslis.temporal.indexes;

import java.io.BufferedReader;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;


/**
 * Interface to a simple database containing term time series information
 * for a collection.
 */
public class TimeSeriesIndex {

    int numBins = -1;
    FileWriter writer = null;
    Map<String, double[]> timeSeriesMap = new HashMap<String, double[]>();
    boolean readOnly = false;

    public void open(String path, boolean readOnly) 
            throws IOException
    {        
        if (readOnly) {
            load(path);
        } else {
            writer = new FileWriter(path);
        }
    }
    
    public void load(String path) throws IOException 
    {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new GZIPInputStream(new FileInputStream(path)), "UTF-8"));
        String line;
        int j=0;
        System.err.println("Loading " + path);

        while ((line = br.readLine()) != null) {
            if (j%10000 == 0) 
                System.err.print(".");
            if (j%100000 == 0) 
                System.err.print(j + "\n");

            String[] fields = line.split(",");
            String term = fields[0];
            double[] counts = new double[fields.length-1];
            for (int i=1; i<fields.length; i++) {
                counts[i-1] = Double.valueOf(fields[i]);
            }
            timeSeriesMap.put(term, counts);
            j++;
        }
        System.err.println("Done");
                
        br.close();
    }
    
    

    public void add(String term, long[] counts) throws IOException {
        if (!readOnly) {
            writer.write(term);
            for (long count: counts) {
                writer.write("," + count);
            }
            writer.write("\n");
        }
    }
    
    
    public List<String> terms()
    {
        List<String> terms = new ArrayList<String>();

        terms.addAll(timeSeriesMap.keySet());
        
        return terms;
    }
    public double[] get(String term) 
    {
        return timeSeriesMap.get(term).clone();
    }
    
    public double[] getDfIDF(String term) {
        
        double[] bins = timeSeriesMap.get("_total_");
        double N = 0;
        for (int i=0; i< bins.length; i++) {
            if (bins[i] == 0)
                bins[i] = 1;
            N+=bins[i];
        }
        
        double[] ts = timeSeriesMap.get(term);
        double sum = 0;
        for (double t: ts) {
            sum += t;
        }
        
        double[] dfidf = new double[ts.length];

        for (int i=0; i<ts.length; i++) 
            dfidf[i] = (ts[i]/bins[i]) * Math.log(N/sum);

        return dfidf;
    }
    
    public double get(String term, int bin)
    {
        double freq = 0;

        double[] counts = timeSeriesMap.get(term);
        if (counts != null && counts.length == getNumBins())
            freq = counts[bin];
        return freq;
    }
    
    public int getNumBins() {
        if (numBins == -1)
            numBins = timeSeriesMap.get("_total_").length;

        return numBins;
    }
    
    public void smoothMovingAverage(String newpath, int win) throws IOException {
        FileWriter writer = new FileWriter(newpath);
        Set<String> vocab = timeSeriesMap.keySet();
        
        DecimalFormat df = new DecimalFormat("###.####");
        double[] totals = timeSeriesMap.get("_total_");
        totals = new double[totals.length];
        for (String term: vocab) {
            
            if (term.equals("_total_"))
                continue;
            
            double[] series = timeSeriesMap.get(term);
            if (series.length < series.length)
                continue;
            
            series = average(series, win);
            
            writer.write(term);
            for (int i=0; i<series.length; i++) {
                writer.write("," + df.format(series[i]));
                
                totals[i] += series[i];
            }
            writer.write("\n");           
        }
        
        writer.write("_total_");
        for (int i=0; i<totals.length; i++) {
            writer.write("," + df.format(totals[i]));
        }
        writer.write("\n");           

        writer.close();
    }
    
    public double[] average(double[] series, int winSize) 
    {        
        double[] smoothed = new double[series.length];
        
        for (int t=0; t<series.length; t++) {
            double timeFreq = series[t];
            int n = 1;
            
            int size = series.length;
            if (t < size) {

                for (int i=0; i < winSize; i++) {
                    if (t > i)
                        timeFreq += series[t - i];
                    if (t < size - i)
                        timeFreq += series[t + i];
                    n++;
                }
            }
            smoothed[t] = timeFreq/(double)n;
        }
        return smoothed;
    }
    
    public double getLength(int bin) throws Exception {
        return get("_total_", bin);
    }
}
