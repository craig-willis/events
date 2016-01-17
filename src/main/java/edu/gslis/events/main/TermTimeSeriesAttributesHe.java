package edu.gslis.events.main;

import java.io.FileWriter;
import java.util.List;

import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.temporal.util.RUtil;

/**
 * Calculate the term timeseries attributes for the He implementation.
 * This includes the dominant period and dominant power spectrum.
 * Output
 *  Term, DP, DPS, ACF
 */
public class TermTimeSeriesAttributesHe 
{
    public static void main(String[] args) throws Exception
    {
        String tsIndexPath = args[0];             // Path to file created by CreateTermTimeIndex
        String outputPath = args[1];              // Output path
        int minOccur = Integer.parseInt(args[2]); // Minimum term occurrence
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        
        tsIndex.open(tsIndexPath, true);
        
        RUtil rutil = new RUtil();
        List<String> terms = tsIndex.terms();
        
        
        FileWriter out = new FileWriter(outputPath);
        

        double[] bins = tsIndex.get("_total_");
        double N = 0;
        for (int i=0; i< bins.length; i++) {
            if (bins[i] == 0)
                bins[i] = 1;
            N+=bins[i];
        }
        
        for (String term: terms) {
            double[] ts = tsIndex.get(term);
            int nonEmpty = 0;
            double[] dfidf = new double[ts.length];
            
            if (ts.length > 0 ) {                
                double sum = 0;
                for (double t: ts) {
                    if (t > 0) nonEmpty++;
                    sum += t;
                }
                
                for (int i=0; i<ts.length; i++) 
                    dfidf[i] = (ts[i]/bins[i]) * Math.log(N/sum);
                               
                if (sum > minOccur) {
                    try
                    {
                       double dp = rutil.dp(dfidf);   
                       double dps = rutil.dps(dfidf);
                       double[] acf = rutil.acf(dfidf);
                       if (dp > ts.length/2 && nonEmpty > 1) {
                           out.write(term + "," + dp + "," + dps + "," + acf[0] + "\n");
                       }
                       out.flush();
                    } catch (Exception e) {
                        System.err.println(term);
                        for (int i=0; i<dfidf.length; i++) {
                            System.out.print(dfidf[i] + " ");
                        }
                        System.out.println();
                        e.printStackTrace();
                    }
                }
            }
        }
        out.close();
    }    
}
