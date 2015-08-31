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
        
        //Map<String, Double> termAcf = new ValueComparableMap<String, Double>(Ordering.natural().reverse());

        double[] totals = tsIndex.get("_total_");
        double total = 0;
        for (double t: totals)
            total+=t;
        
        // Find terms with the highest ACF
        for (String term: terms) {
            double[] ts = tsIndex.get(term);
            double[] tfitf = new double[ts.length];
            
            if (ts.length > 0 ) {                
                double sum = 0;
                for (double t: ts)
                    sum += t;
                
                for (int i=0; i<ts.length; i++) {
                    tfitf[i] = (ts[i]/totals[i]) + Math.log(total/sum);
                }
                               
                if (sum > minOccur) {
                    
                    //int[] cps = rutil.cps(ts);
                    double kurtosis = rutil.kurtosis(ts);
                    double skew = rutil.skewness(ts);
                    double spec = rutil.maxSpec(ts);
                    double spectf = rutil.maxSpec(tfitf);
                    double[] acftf = rutil.acf(tfitf);
                    double spec2 = rutil.maxSpec2(ts);
                    double spectf2 = rutil.maxSpec2(tfitf);
                    
                    //ts = tsIndex.average(ts, 3);

                    double[] acf = rutil.acf(ts);
                    // acf=2, spec=7, spec2=8, acftf=9, spectf=10, spectf2=11
                    out.write(term + "," + acf[0] + "," + acf[1] + "," + sum + "," + kurtosis + "," + skew + "," + 
                    spec + "," + spec2 + "," + 
                    acftf[0] + "," + spectf + "," + spectf2 + "\n");
                    //termAcf.put(term,  acf);  
                }
            }
        }
        out.close();
    }    
}
