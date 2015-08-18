package edu.gslis.events.main;

import java.util.Iterator;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQuery;
import edu.gslis.queries.expansion.FeedbackRelevanceModel;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.KDEScorer;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;

public class GetRelModel 
{
    public static void main(String[] args) throws Exception {
        String indexPath = args[0];        
        String stopperPath = args[1];
        String query = "rangoon "; //args[2];
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        Stopper stopper = new Stopper(stopperPath);
        CollectionStats colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);

        FeatureVector qv = new FeatureVector(query, stopper);
        
        GQuery gquery = new GQuery();
        gquery.setFeatureVector(qv);
        gquery.setText(query);
        gquery.setTitle("");
        
        SearchHits hits = index.runQuery(gquery, 1000);
        KDEScorer kde = new KDEScorer();
        kde.setStartTime(571647000);
        kde.setEndTime(631152000);
        kde.setQuery(gquery);
        kde.setParameter("alpha", 0.5);
        kde.setParameter("mu", 2500);
        kde.init(hits);
        kde.setCollectionStats(colStats);
        Iterator<SearchHit> it = hits.iterator();
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = kde.score(hit);
            hit.setScore(score);
        }
        hits.rank();
        
        FeedbackRelevanceModel rm = new FeedbackRelevanceModel();
        rm.setTermCount(20);
        rm.setDocCount(20);
        rm.setRes(hits);
        rm.setIndex(index);
        rm.setOriginalQuery(gquery);
        rm.setStopper(stopper);
        rm.build();
        //FeatureVector rm3 = FeatureVector.interpolate(qv, rm.asFeatureVector(), 0.5);
        FeatureVector rm3 = rm.asFeatureVector();
        rm3.clip(20);
        rm3.normalize(); 
        System.out.println(rm3);
                
        /*
        gquery.setFeatureVector(rm3);
        hits = index.runQuery(gquery, 1000);
        it = hits.iterator();
        Map<String, Double> docnos = new TreeMap<String, Double>();
        double[] epochs = new double[hits.size()];
        double[] scores = new double[hits.size()];
        int i=0;
        while (it.hasNext()) {
            SearchHit hit = it.next();
            double score = kde.score(hit);
            hit.setScore(score);
            double epoch = (Double)hit.getMetadataValue(Indexer.FIELD_EPOCH);
            epochs[i] = epoch;
            scores[i] = score;
            System.out.println(epoch);
            i++;
        }
        RUtil rutil = new RUtil();
        rutil.plot(query.replace(" ", "-"), epochs, scores, "/Users/cwillis/dev/uiucGSLIS/temporal/output/rm3/");
        rutil.hist(query.replace(" ", "-"), epochs, "/Users/cwillis/dev/uiucGSLIS/temporal/output/rm3/");
        hits.rank();
        
        
        for (String docno: docnos.keySet()) {
            System.out.println(docno + "=" + docnos.get(docno));
        }
        */  
    }
}
