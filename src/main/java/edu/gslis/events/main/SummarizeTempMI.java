package edu.gslis.events.main;

import java.io.File;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.temporal.util.RUtil;



/**
 * Summarize the temporal mutual information using the specified approach
 *
 */
public class SummarizeTempMI 
{
    public static final String VAR = "var";
    public static final String MAX = "max";
    public static final String DIFF = "diff";
    public static final String ACF = "acf";
    
    public static void main(String[] args) throws Exception {
        
        String path = args[0];
        List<String> lines = FileUtils.readLines(new File(path));
        RUtil rutil = new RUtil();
        
        System.out.println("t1,t2,var,mean,max,diff,sum,acf,dps");
        
        for (String line: lines) {
            line = line.replaceAll("NaN", "0");
            String[] fields = line.split("\t");
            String t1 = fields[0];
            String t2 = fields[1];
            String tmiStr = fields[2];
            String[] tmis = tmiStr.split(",");
            DescriptiveStatistics ds = new DescriptiveStatistics();
            double[] mis = new double[tmis.length];
            for (int i=0 ; i < tmis.length; i++) {
                mis[i] = Double.valueOf(tmis[i]);
                ds.addValue(mis[i]);
            }
            
            double var = ds.getVariance();
            double mean = ds.getMean();
            double max = ds.getMax();
            double diff = max-mean;
            double sum = ds.getSum();
            double[] acfs = rutil.acf(mis);
            double acf = 0;
            if (acfs[0] > 1) 
                acf = acfs[0];
            double dps = rutil.dps(mis);
            System.out.println(t1 +"," + t2 + "," + var + "," + mean + "," + max + "," + diff + "," + sum + "," + acf + "," + dps);
        }
        
    }
}
