package edu.gslis.temporal.scorers;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;

import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;

/**
 * Temporal model:
 *  Retrieve top 1000 documents
 *  Estimate normal mixtures based on timestamps
 *  Score QL
 *  Score based on mixture model
 */
public class MixtureScorer extends TemporalScorer {
    
    
    public static String ALPHA = "alpha";
    public static String K = "k";
            
    RUtil rutil = new RUtil();
    
    Map<NormalDistribution, Double> densities = new HashMap<NormalDistribution, Double>();
    
    /**
     * Estimate normal mixture model.
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
        
        try {
           
            double[][] mixture = rutil.mixture(x);
            double[] lambda = mixture[0];
            double[] mu = mixture[1];
            double[] sigma = mixture[2];
            
            for (int i=0; i < lambda.length; i++) {
                NormalDistribution nd = new NormalDistribution(mu[i], sigma[i]);
                densities.put(nd, lambda[i]);
            }
        } catch (Exception e) {
            System.out.println("Error with query " + gQuery.getTitle()  + " (" + x.length + ")");
            e.printStackTrace();
        }
    }
    
       
    /**
     * Combine QL score and temporal score
     */
    public double score(SearchHit doc) {
        
        double alpha = paramTable.get(ALPHA);
        
        double ll = super.score(doc);
        
        double mm = 0;
        for (NormalDistribution nd: densities.keySet()) {
            double weight = densities.get(nd);
            mm = Math.log(weight* nd.density(getTime(doc)));
        }
        
        return alpha*mm + (1-alpha)*ll;
    }
    
    public double[] getScores(SearchHits hits) {
        double[] weights = new double[hits.size()];
        
        for (int i=0; i<hits.size(); i++) {
            weights[i] = hits.getHit(i).getScore();
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
