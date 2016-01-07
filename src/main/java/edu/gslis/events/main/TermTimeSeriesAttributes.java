package edu.gslis.events.main;

import java.io.FileWriter;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Ordering;

import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;

public class TermTimeSeriesAttributes 
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
        for (int i=0; i< bins.length; i++) {
            if (bins[i] == 0)
                bins[i] = 1;
            N+=bins[i];
        }
        
        // Find terms with the highest ACF
        for (String term: terms) {
            double[] ts = tsIndex.get(term);
            double[] tfitf = new double[ts.length];
            
            if (ts.length > 0 ) {                
                double sum = 0;
                for (double t: ts)
                    sum += t;
                
                for (int i=0; i<ts.length; i++) {
                    tfitf[i] = (ts[i]/bins[i]) + Math.log(N/sum);
                }
                               
                if (sum > minOccur) {
                    
                    //int[] cps = rutil.cps(ts);
                    //double kurtosis = rutil.kurtosis(ts);
                    //double skew = rutil.skewness(ts);
                    //double spec = rutil.maxSpec(ts);
                    //double spectf = rutil.maxSpec(tfitf);
                    double[] acftf = rutil.acf(tfitf);
                    //double spec2 = rutil.maxSpec2(ts);
                    //double spectf2 = rutil.maxSpec2(tfitf);
                    
                    //ts = tsIndex.average(ts, 3);

                    double[] acf = rutil.acf(ts);
                    // acf=2, spec=7, spec2=8, acftf=9, spectf=10, spectf2=11
                    out.write(term + "," + acf[0] + "," + acf[1] + "," +  acftf[0] + "\n");
                    //termAcf.put(term,  acf);  
                }
            }
        }
        out.close();
    }    
}
