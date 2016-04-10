package edu.gslis.events.main;

import java.io.BufferedReader;
import java.io.FileReader;

import edu.gslis.temporal.util.RUtil;

public class TMIACF 
{
    public static void main(String[] args) throws Exception
    {
        String tmiPath = args[0];
        BufferedReader br = new BufferedReader(new FileReader(tmiPath));
        String line;
        RUtil rutil = new RUtil();
        while ((line = br.readLine()) != null) {
            String[] fields = line.split("\\t");
            String term1 = fields[0];
            String term2 = fields[1];
            String mi = fields[2];
            String[] vals = mi.split(",");
            double[] x = new double[vals.length];
            double sum = 0;
            for (int i=0; i<vals.length; i++)  {
                x[i]= Double.parseDouble(vals[i]);
                sum += x[i];
            }
            
            try {
                double[] acf = rutil.acf(x);
                double dps = rutil.dps(x);
                
                if (acf[0] > 0.5)
                    System.out.println(term1 + "," + term2 + "," + acf[0] + "," + dps + "," + sum);
            } catch (Exception e) {

            }
        }
        br.close();
    }    
}
