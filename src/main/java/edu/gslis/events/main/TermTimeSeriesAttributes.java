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
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();
        
        tsIndex.open(tsIndexPath, true);
        
        RUtil rutil = new RUtil();
        List<String> terms = tsIndex.terms();
        
        
        FileWriter out = new FileWriter(outputPath);
        
        //Map<String, Double> termAcf = new ValueComparableMap<String, Double>(Ordering.natural().reverse());


        // Find terms with the highest ACF
        for (String term: terms) {
            double[] ts = tsIndex.get(term);
            //ts = tsIndex.average(ts, 3);
            if (ts.length > 0 ) {                
                double sum = 0;
                for (double t: ts) 
                    sum += t;
                
               // if (sum > 10) {
                    double[] acf = rutil.acf(ts);
                    out.write(term + "," + acf[0] + "," + acf[1] + "," + sum +    "\n");
                    //termAcf.put(term,  acf);  
               // }
            }
        }
        out.close();
    }    
}
