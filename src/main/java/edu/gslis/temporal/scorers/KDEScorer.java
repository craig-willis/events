package edu.gslis.temporal.scorers;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RKernelDensity;

/**
 * Implements Efron et al. (2014) temporal KDE model
 * 
 * Retrieve initial set of documents, score using QL
 * Apply temporal retrieval model to re-rank results
 * From the top k re-ranked documents, estimate feedback models
 * Re-run retrieval with feedback model
 * Re-rank results using temporal model
 * 
 * Temporal model:
 *  Retrieve top 1000 documents
 *  Estimate KDE based on timestamps
 *  Score QL
 *  Score based on KDE
 */
public class KDEScorer extends TemporalScorer {
    
    
    public static String ALPHA = "alpha";
    public static String K = "k";
            
    RKernelDensity dist = null;
    
    /**
     * Estimate temporal density of hits
     */
    @Override
    public void init(SearchHits hits) {        
        // Estimate density for hits based on document timestamp
        
        int k = 0; 
        if (paramTable.get(K) != null ) 
            k = paramTable.get(K).intValue();
        
        if (k <= 0)
            k = hits.size();

        double[] x = getTimes(hits, k);
        double[] w = getProportionalWeights(hits, k);
        dist = new RKernelDensity(x, w);            
    }
    
       
    /**
     * Combine QL score and temporal score
     */
    public double score(SearchHit doc) {
        
        double alpha = paramTable.get(ALPHA);
        
        double ll = super.score(doc);
        double kde = Math.log(dist.density(getTime(doc)));
        
        return alpha*kde + (1-alpha)*ll;
    }
    
    public double[] getScores(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        for (int i=0; i<hits.size(); i++) {
            weights[i] = hits.getHit(i).getScore();
        }
        
        return weights;        
    }
    public double[] getProportionalWeights(SearchHits hits, int k) {
        double[] weights = new double[k];
        
        double total = 0;
        for (int i=0; i<k; i++) 
            total += hits.getHit(i).getScore();
        for (int i=0; i<k; i++) {
            weights[i] = hits.getHit(i).getScore()/total;
        }
        
        return weights;
    }

    public static double[] getUniformWeights(SearchHits hits) {
        return getUniformWeights(hits, hits.size());
    }
    
    public static double[] getUniformWeights(SearchHits hits, int k) {
        double[] weights = new double[k];
        
        for (int i=0; i<k; i++)
            weights[i] = 1;
        
        return weights;
    }
}
