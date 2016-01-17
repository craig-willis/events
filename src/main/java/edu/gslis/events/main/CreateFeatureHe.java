package edu.gslis.events.main;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.Ordering;

import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.temporal.util.FeatureHe;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;


/**
 * Create a new feature structure for He implementation.
 * 
 * 1. Convert TimeSeriesIndex to DFIDF
 * 2. Fit Gaussians
 * 3. Create per-Gaussian feature entry
 */
public class CreateFeatureHe 
{    
    TimeSeriesIndex ts = new TimeSeriesIndex();
    Map<String, Double> dominantPeriodMap = new HashMap<String, Double>();
    Map<String, Double> dominantPowerSpectrumMap = new ValueComparableMap<String, Double>(Ordering.natural().reverse());

    RUtil rutil = new RUtil();

    
    public static void main(String[] args) throws Exception {
        String dpsPath = args[0];
        String tsPath = args[1];

        CreateFeatureHe finder = new CreateFeatureHe(dpsPath, tsPath);
        finder.getFeatures();
    }
    
    public CreateFeatureHe(String dpsPath, String tsPath) 
            throws Exception 
    {
        ts = new TimeSeriesIndex();
        ts.open(tsPath, true);
        readTermDps(dpsPath);              
    }
    
    public void getFeatures() throws Exception
    {   
        // 1. Convert tsidx to DFIDF
        for (String term: dominantPowerSpectrumMap.keySet()) { 
            double[] dfidf = dfidf(term);           
            
            double dp = dominantPeriodMap.get(term);
            int k = dfidf.length/(int)dp;
            
            System.err.println(term);
            double[][] bursts = rutil.getBursts(dfidf, k);
            
            for (int j=0; j<k; j++) 
            {
                double mu = bursts[0][j];
                double sigma = bursts[1][j];
                
                double[] tstmp = dfidf.clone();
                for (int i=0; i<tstmp.length; i++) {
                    if (i < (mu-sigma) || i > (mu+sigma)) {
                        tstmp[i] = 0;
                    }
                }
                String tsstr = "";
                for (int i=0; i< tstmp.length; i++) {
                    if (i > 0)
                        tsstr += ",";
                    tsstr += tstmp[i];
                }
                System.out.println(term + "_" + j + "," + tsstr);
            }    
        }                
    }
    
    double[] dfidf(String term) {
        double[] ts1 = ts.get(term);
        double[] bins = ts.get("_total_");
        double N = 0;
        for (int i=0; i< bins.length; i++) {
            if (bins[i] == 0)
                bins[i] = 1;
            N+=bins[i];
        }
        
        double[] dfidf = new double[ts1.length];
        
        if (ts1.length > 0 ) {                
            double sum = 0;
            for (double t: ts1) {
                sum += t;
            }
            
            for (int i=0; i<ts1.length; i++) 
                dfidf[i] = (ts1[i]/bins[i]) * Math.log(N/sum);
        }
        return dfidf;
    }
    


    public void readTermDps(String path) throws Exception 
    {
        // One strategy is to re-create the term/time series index 
        // for only those terms in the dps, using DFIDF, and
        // to go ahead and create a separate vector for each 
        // fit Gaussian.
        List<String> lines = FileUtils.readLines(new File(path));
        for (String line: lines) {
            String[] fields = line.split(",");
            String term = fields[0];
            double dp = Double.parseDouble(fields[1]);
            double dps = Double.parseDouble(fields[2]);
            dominantPeriodMap.put(term, dp);
            dominantPowerSpectrumMap.put(term, dps);
        }
    
    }
}
