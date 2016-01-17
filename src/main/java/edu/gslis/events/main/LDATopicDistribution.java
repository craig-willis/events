package edu.gslis.events.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.collect.Ordering;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;

/**
 * Calculate the term timeseries attributes for the He implementation.
 * This includes the dominant period and dominant power spectrum.
 * 
 */
public class LDATopicDistribution 
{
    public static void main(String[] args) throws Exception
    {
        String docTopicPath = args[0];
        String indexPath = args[1];
        String outputPath = args[2];
        int startTime = Integer.parseInt(args[3]);
        int endTime = Integer.parseInt(args[4]);
        int interval = Integer.parseInt(args[5]);        
        
        int numBins = (endTime - startTime)/interval  + 1;
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);

        
        RUtil rutil = new RUtil();
        
        Map<Integer, double[]> topicTimeSeries = new TreeMap<Integer, double[]>();

        BufferedReader br = new BufferedReader(new FileReader(docTopicPath));
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("#")) continue;
            String[] fields  = line.split("\\t");
            String docno = fields[1].replace(",","");
            int bin = getBin(index, docno, startTime, interval);
            
            for (int i=2; i < fields.length; i=i+2) {
                int topic = Integer.parseInt(fields[i]);
                double weight = Double.parseDouble(fields[i+1]);

                double[] ts = topicTimeSeries.get(topic);
                if (ts == null) {
                    ts = new double[numBins];
                    for (int j=0; j<numBins; j++) {
                        ts[j] = 0;
                    }
                }
                if (bin < numBins) {
                    ts[bin] += weight;
                    topicTimeSeries.put(topic, ts);
                } else {
                    System.out.println(docno);
                }
            }
        }
        br.close();

        Map<Integer, Double> acfMap = new ValueComparableMap<Integer, Double>(Ordering.natural().reverse());
        for (int topic: topicTimeSeries.keySet()) {
            double[] ts = topicTimeSeries.get(topic);
            double[] acf = rutil.acf(ts);
            acfMap.put(topic, acf[0]);
            /*
            for (int i=0; i<numBins; i++) {                
                str += "," + ts[i];
            }
            System.out.println(str);
            */
        }
        
        for (int topic: acfMap.keySet()) {
            System.out.println(topic + "," + acfMap.get(topic));
        }
        
       // FileWriter out = new FileWriter(outputPath);
        
    }
    
    public static int getBin(IndexWrapper index, String docno, int startTime, int interval) {
        SearchHit hit = index.getSearchHit(docno, null);

        String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));

        int epoch = Integer.parseInt(epochStr);
        
        int bin = (epoch - startTime)/interval;
        return bin;
    }
}
