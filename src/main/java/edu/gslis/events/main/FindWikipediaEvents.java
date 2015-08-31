package edu.gslis.events.main;

import java.io.File;
import java.io.FileWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.google.common.collect.Ordering;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.indexes.TimeSeriesIndex;
import edu.gslis.temporal.scorers.KDEScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class FindWikipediaEvents 
{
    static final double ALPHA = 0.5;
    static final double MU = 2500;
    static final int NUM_TERMS = 10;
    static final int NUM_DOCS = 50;

    static int colStart = 0;
    static int colEnd = 0;
    static int colInterval = 0;
    /**
     * 1. Read list of high ACF terms 
     * 2. Read list of high CCF term
     * 
     */
    public static void main(String[] args) throws Exception
    {
        String acfTermsPath = args[0];
        String indexPath = args[1];
        String wpIndexPath = args[2];
        String stopperPath = args[3];
        String outputPath = args[4];
        colStart = Integer.parseInt(args[5]);
        colEnd = Integer.parseInt(args[6]);
        colInterval = Integer.parseInt(args[7]); 
        
        Map<String, Double> acfMap = readTermAcf(acfTermsPath);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        Stopper stopper = new Stopper(stopperPath);
        CollectionStats colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);

        IndexWrapper wpIndex = IndexWrapperFactory.getIndexWrapper(wpIndexPath);
        CollectionStats wpColStats = new IndexBackedCollectionStats();
        wpColStats.setStatSource(wpIndexPath);

        File output = new File(outputPath);
        
        // Find all terms with high CCF
        List<String> seen = new ArrayList<String>();
        for (String acfTerm: acfMap.keySet()) {
            
            if (seen.contains(acfTerm))
                continue;
            
            FeatureVector fv = new FeatureVector(acfTerm, stopper); 

            FeatureVector rm = buildRm(fv, stopper, index, colStats);
            
            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(rm);
            wpQuery.setTitle(acfTerm);
            SearchHits hits = wpIndex.runQuery(wpQuery, 1);
            SearchHit hit = hits.getHit(0);
            String docno = hit.getDocno();
            System.out.println("\n\nTerm:" + acfTerm);
            System.out.println(rm);
            System.out.println("Hit: " + docno);

        }
    }
    

    public static FeatureVector buildRm(FeatureVector qv, Stopper stopper, IndexWrapper index, CollectionStats collStats) 
    {
        //FeatureVector qv = new FeatureVector(term, stopper);
        GQuery gquery = new GQuery();
        gquery.setFeatureVector(qv);
//        gquery.setText(term);
        gquery.setTitle("");
        
        SearchHits hits = index.runQuery(gquery, NUM_DOCS);
        KDEScorer kde = new KDEScorer();
        kde.setStartTime(colStart);
        kde.setEndTime(colEnd);
        kde.setQuery(gquery);
        kde.setParameter("alpha", ALPHA);
        kde.setParameter("mu", MU);
        kde.init(hits);
        kde.setCollectionStats(collStats);
        Iterator<SearchHit> it = hits.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = kde.score(hit);
            hit.setScore(score);
        }
        hits.rank();
        
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setTermCount(NUM_TERMS);
        rm.setDocCount(NUM_DOCS);
        rm.setRes(hits);
        rm.setIndex(index);
        rm.setOriginalQuery(gquery);
        rm.setStopper(stopper);
        rm.build();
        //FeatureVector rm3 = FeatureVector.interpolate(qv, rm.asFeatureVector(), 0.5);
        FeatureVector rm3 = rm.asFeatureVector();
        rm3.clip(NUM_TERMS);
        rm3.normalize();
        return rm3;
    }
    
    public static double kl(FeatureVector p, FeatureVector q, IndexWrapper index) {
        
        double ll = 0.0;
        Iterator<String> queryIterator = p.iterator();
        while(queryIterator.hasNext()) 
        {
            String feature = queryIterator.next();
            double docFreq = q.getFeatureWeight(feature);
            double docLength = q.getLength();
            
            double cp = (index.termFreq(feature)) / index.termCount();
            double pr = (docFreq + NUM_TERMS*cp)/(docLength + NUM_TERMS);

            double queryWeight = p.getFeatureWeight(feature)/p.getLength();
            ll += queryWeight * Math.log(pr);                        
        }
        return ll;
    }

    public static Map<String, Map<String, Double>> readTermCcf(String ccfTermPath) throws Exception 
    {
        Map<String, Map<String, Double>> ccfTerms = new HashMap<String, Map<String, Double>>();
        List<String> lines = FileUtils.readLines(new File(ccfTermPath));
        for (String line: lines) {
            String[] fields = line.split(",");
            if (fields.length == 3) {
                String term1 = fields[0];
                String term2 = fields[1];
                double ccf = Double.parseDouble(fields[2]);
                if (ccf < 0.50)
                    continue;
                Map<String, Double> map = ccfTerms.get(term1);
                if (map == null)
                    map = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                map.put(term2, ccf);
                ccfTerms.put(term1, map);
            }
        }
        return ccfTerms;
    }

    
    public static Map<String, Map<String, Double>> readTermMi(String miTermPath) throws Exception 
    {
        Map<String, Map<String, Double>> miTerms = new HashMap<String, Map<String, Double>>();
        List<String> lines = FileUtils.readLines(new File(miTermPath));
        for (String line: lines) {
            String[] fields = line.split(",");
            if (fields.length == 3) {
                String term1 = fields[0];
                String term2 = fields[1];
                double mi = Double.parseDouble(fields[2]);
                Map<String, Double> map = miTerms.get(term1);
                if (map == null)
                    map = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                map.put(term2, mi);
                miTerms.put(term1, map);
            }
        }
        return miTerms;
    }
    
    public static Map<String, Double> readTermAcf(String acfTermPath) throws Exception 
    {
        Map<String, Double> acfTerms = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
        List<String> lines = FileUtils.readLines(new File(acfTermPath));
        for (String line: lines) {
            String[] fields = line.split(",");
            String term = fields[0];
            double acf = Double.parseDouble(fields[1]);
            acfTerms.put(term, acf);
        }
        return acfTerms;
    }
}
