package edu.gslis.temporal.scorers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.textrepresentation.FeatureVector;

public class TemporalScorer extends RerankingScorer 
{
    String MU = "mu";
    
    long startTime;
    long endTime;
    long interval;
    String tsIndexPath;
    
    TimeSeriesIndex tsIndex = null;
    
    double[] kls = null;
    DescriptiveStatistics klstats = new DescriptiveStatistics();
    
    public void init(SearchHits hits) {
    }
    
    public long getDocTime(SearchHit doc) {
        double epoch = (Double)doc.getMetadataValue(Indexer.FIELD_EPOCH);
        long docTime = (long)epoch;
        return docTime;
    }
    public int getBin(long time) {
        // Get the bin for this document       
        int t = (int)((time - startTime)/interval);
        return t;
    }
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    public void setInterval(long interval) {
        this.interval = interval;
    }
    public void setIndex(TimeSeriesIndex tsIndex) {
        this.tsIndex = tsIndex;
    }
    
    public void setKLs(double[] kls) {
        
        double total = 0;
        for (int i=0; i< kls.length; i++) {
            total += kls[i];
        }
        
        for (int i=0; i< kls.length; i++)
            kls[i] /= total;

        for (int i=0; i< kls.length; i++)
            klstats.addValue(kls[i]);
        
        this.kls = kls;
    }
    
    public void close() {        
    }

    public double score(SearchHit doc) 
    {
        // Get the bin for this document 
        long docTime = getDocTime(doc);

        // Ignore documents outside of the temporal bounds
        if (docTime < startTime || docTime > endTime)
            return Double.NEGATIVE_INFINITY;
        
        double logLikelihood = 0.0;
        Iterator<String> queryIterator = gQuery.getFeatureVector().iterator();
        while(queryIterator.hasNext()) 
        {
            String feature = queryIterator.next();
            double docFreq = doc.getFeatureVector().getFeatureWeight(feature);
            double docLength = doc.getLength();
            
            // The 1 here is because some query terms in TREC don't appear in all collections when we 
            // separate out sources.
            double collectionProb = (1 + collectionStats.termCount(feature)) / collectionStats.getTokCount();
            double pr = (docFreq + 
                    paramTable.get(MU)*collectionProb) / 
                    (docLength + paramTable.get(MU));
            double queryWeight = gQuery.getFeatureVector().getFeatureWeight(feature);
            logLikelihood += queryWeight * Math.log(pr);                        
        }
        return logLikelihood;
    }
    
    public static double[] getTimes(SearchHits hits) {
        return getTimes(hits, hits.size());
    }
    public static double[] getTimes(SearchHits hits, int k) {
        
        double[] times = new double[k];
        
        for (int i=0; i<k; i++) {
            SearchHit hit = hits.getHit(i);
            times[i] = getTime(hit);
        }
        
        return times;
    }
    
    public static long getTime(SearchHit hit) {
        String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));  
        
        long epoch = Long.parseLong(epochStr);
        return epoch;
    }
    
    public double kl(FeatureVector p, FeatureVector q) {
        double kl = 0;
        
        Iterator<String> it = p.iterator();
        while(it.hasNext()) {
            String feature = it.next();
            double pi = p.getFeatureWeight(feature)/p.getLength();
            double qi = q.getFeatureWeight(feature)/q.getLength();
            if (pi > 0 && qi > 0)
                kl += pi * Math.log(pi/qi);
        }
        return kl;
    }
    
 
    
    /**
     * Score the temporal model with repsect to the document model.
     * Smooth the temporal model using the collection model.
     * 
     * @param dv    Document model
     * @param tfv   Temporal model
     * @return
     */
    public double scoreTemporalModel(FeatureVector dm, FeatureVector tm)
    {
        double logLikelihood = 0.0;
                     
        for (String feature: dm.getFeatures())
        {                                       
            double tfreq = tm.getFeatureWeight(feature);
            double tlen = tm.getLength();
            
            double smoothedProb = (tfreq + 1)/(tlen + collectionStats.getTokCount());

            double docWeight = dm.getFeatureWeight(feature);
            
            logLikelihood += docWeight * Math.log(smoothedProb);
        }                        
           
        return logLikelihood;
    }
    
    /**
     * Create a feature vector for each temporal bin
     * @param dm Document model
     * @return
     */
    public Map<Integer, FeatureVector> createTemporalModels(FeatureVector dm) throws Exception
    {
        Map<Integer, FeatureVector> tms = new TreeMap<Integer, FeatureVector>();
        
        // Totals for each bin
        double[] total = tsIndex.get("_total_");

        Iterator<String> dvIt = dm.iterator();
        while(dvIt.hasNext()) {
            String feature = dvIt.next();
            // Time series for feature
            double[] series = tsIndex.get(feature); 
            if (series != null) {
                // Populate feature vector for each bin
                for (int i=0; i<series.length; i++) {
                    FeatureVector tm = tms.get(i);
                    if (tm == null)
                        tm = new FeatureVector(null);
                    
                    if (total[i] != 0) 
                        tm.addTerm(feature, series[i]/total[i]);
                    ///else
                    //    System.out.println("Warning: bin " + i + " is empty");
                    
                    tms.put(i, tm);
                }
            }
        }
        return tms;
    }   
    
    
    public double scoreTemporalModel(FeatureVector dm, Map<String, Double> tm)
    {
        double logLikelihood = 0.0;

        double tlen = tm.get("_total_");
        for (String feature: dm.getFeatures())
        {         
            double tfreq = 0;
            if (tm.get(feature) != null)
                tfreq = tm.get(feature);

            //double smoothedProb = (tfreq + 1)/(tlen + collectionStats.getTokCount());
            double pwC = collectionStats.termCount(feature) / collectionStats.getTokCount();
            double smoothedProb = (tfreq + 1000 * pwC ) / (tlen + 1000);
                    
            double docWeight = dm.getFeatureWeight(feature);
            
            logLikelihood += docWeight * Math.log(smoothedProb);
        }                        
           
        return logLikelihood;
    }
 
    // KL divergence of temporal model (bin) 
    public double scoreTemporalModel(FeatureVector dm, int bin)
    {
        double logLikelihood = 0.0;

        try
        {
            double tlen = tsIndex.getLength(bin);
            if (tlen == 0)
                return Double.NEGATIVE_INFINITY;
            
            for (String feature: dm.getFeatures())
            {         
                double tfreq = tsIndex.get(feature, bin);
    
                double smoothedProb = (tfreq + 1)/(tlen + collectionStats.getTokCount());
                //double pwC = collectionStats.termCount(feature) / collectionStats.getTokCount();
                //double smoothedProb = (tfreq + 1000 * pwC ) / (tlen + 1000);
                        
                double docWeight = dm.getFeatureWeight(feature);
                
                logLikelihood += docWeight * Math.log(smoothedProb);
            }                        
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logLikelihood;
    }
    
    // Remove the document vector
    public double scoreTemporalModelIgnore(FeatureVector dm, int bin)
    {
        double logLikelihood = 0.0;

        try
        {
            double tlen = tsIndex.get("_total_", bin);
            if (tlen == 0)
                return Double.NEGATIVE_INFINITY;
            
            for (String feature: dm.getFeatures()) {
                tlen -= dm.getFeatureWeight(feature);
            }

            for (String feature: dm.getFeatures())
            {         
                double tfreq = tsIndex.get(feature, bin);
                
                tfreq -= dm.getFeatureWeight(feature);
    
                double smoothedProb = (tfreq + 1)/(tlen + collectionStats.getTokCount());
                //double pwC = collectionStats.termCount(feature) / collectionStats.getTokCount();
                //double smoothedProb = (tfreq + 1000 * pwC ) / (tlen + 1000);
                        
                double docWeight = dm.getFeatureWeight(feature);
                
                logLikelihood += docWeight * Math.log(smoothedProb);
            }                        
        } catch (Exception e) {
            e.printStackTrace();
        }
        return logLikelihood;
    }
    
    public Map<Integer, Map<String, Double>> createTemporalModels(Set<String> features) 
            throws Exception
    {
        Map<Integer, Map<String, Double>> tms = new TreeMap<Integer, Map<String, Double>>();
        
        // Totals for each bin
        double[] totals = tsIndex.get("_total_");
        for (int i=0; i<totals.length; i++) {
            Map<String, Double> tm = new HashMap<String, Double>();
            tm.put("_total_", totals[i]);
            tms.put(i, tm);
        }
        
        for (String feature: features) 
        {            
            // Time series for feature
            double[] series = tsIndex.get(feature); 
            if (series != null) {
                // Populate feature vector for each bin
                for (int i=0; i<series.length; i++) {
                    Map<String, Double> tm = tms.get(i);
                    
                    tm.put(feature, series[i]);
                    
                    tms.put(i, tm);
                }
            }
        }
        
        return tms;
    }      
    
    public static void main(String[] args) {
        
        // Exponentiate and sum or sum logs then exponentiate:
        double z1 = 1;
        double z2 = 0;
        for (int i=-13; i<-7; i++) {
            double pr = Math.exp(i);
            z1 *= pr;
            
            z2 += i;            
        }        
        
        double z3 = Math.exp(z2);
        System.out.println(z1  + "," + z2 + "," + z3);
    }
}
