package edu.gslis.events.main;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.apache.commons.io.FileUtils;

import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.temporal.util.RUtil;

public class TermTimeSeriesCCF 
{
    public static void main(String[] args) throws Exception
    {
        String tsIndexPath = args[0];
        String termsPath = args[1];
        String outputPath = args[2];
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();        
        tsIndex.open(tsIndexPath, true);
        
        List<String> terms = FileUtils.readLines(new File(termsPath));
        
                
        RUtil rutil = new RUtil();
        
        FileWriter out = new FileWriter(outputPath);

        for (String term1: terms) {
            for (String term2: terms) {
                if (term1.equals(term2)) continue;
                
                double[] ts1 = tsIndex.get(term1);
                double[] ts2 = tsIndex.get(term2);
                try {
                    double ccf = rutil.ccf(ts1, ts2, 0);
                    //if (ccf > 0.6) {
                        out.write(term1 + "," + term2 + "," + ccf +"\n");
                    //}
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("Error in ccf for " + term1 + "," + term2);
                }
            }
        }
        out.close();
    }
}
