package edu.gslis.events.main;

import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Ordering;

import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;

public class TermTimeSeriesAttributesHe 
{
    public static void main(String[] args) throws Exception
    {
        String tsIndexPath = args[0];
        String outputPath = args[1];
        int minOccur = Integer.parseInt(args[2]);
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        
        tsIndex.open(tsIndexPath, true);
        
        RUtil rutil = new RUtil();
        List<String> terms = tsIndex.terms();
        
        
        FileWriter out = new FileWriter(outputPath);
        

        double[] bins = tsIndex.get("_total_");
        double N = 0;
        for (double t: bins)
            N+=t;
        
        for (String term: terms) {
            double[] ts = tsIndex.get(term);
            double[] dfidf = new double[ts.length];
            
            if (ts.length > 0 ) {                
                double sum = 0;
                for (double t: ts)
                    sum += t;
                
                for (int i=0; i<ts.length; i++) {
                    dfidf[i] = (ts[i]/bins[i]) + Math.log(N/sum);
                }
                               
                if (sum > minOccur) {
                   double dp = rutil.dp(dfidf);   
                   double dps = rutil.dps(dfidf);
                   double[] acf = rutil.acf(dfidf);
                   out.write(term + "," + dp + "," + dps + "," + acf[0] + "\n");
                   out.flush();
                }
            }
        }
        out.close();
    }    
}
