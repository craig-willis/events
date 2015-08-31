package edu.gslis.events.main;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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


public class FindEventsHe 
{
    static final double ALPHA = 0.5;
    static final double MU = 2500;
    static final int NUM_TERMS = 10;
    static final int NUM_DOCS = 50;
    
    TimeSeriesIndex ts = new TimeSeriesIndex();
    IndexWrapper index = null;
    IndexWrapper wpIndex = null;
    Map<String, Double> dps = new HashMap<String, Double>();
    RUtil rutil = new RUtil();
    Map<String, Set<Integer>> docids = new HashMap<String, Set<Integer>>();
    static int colStart = 0;
    static int colEnd = 0;
    static int colInterval = 0;

    Stopper stopper;
    CollectionStats colStats;
    CollectionStats wpColStats;
    
    public static void main(String[] args) throws Exception {
        String dpsPath = args[0];
        String tsPath = args[1];
        String indexPath = args[2];
        String stopperPath = args[3];
        String wpIndexPath = args[4];

        colStart = Integer.parseInt(args[5]);
        colEnd = Integer.parseInt(args[6]);
        colInterval = Integer.parseInt(args[7]); 

        FindEventsHe finder = new FindEventsHe(dpsPath, tsPath, indexPath, stopperPath, wpIndexPath);
        finder.findEvents();
    }
    public FindEventsHe(String dpsPath, String tsPath, String indexPath, String stopperPath, String wpIndexPath) throws Exception {
        ts = new TimeSeriesIndex();
        ts.open(tsPath, true);
        stopper= new Stopper(stopperPath);
        index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        dps = readTermDps(dpsPath);      
        
        wpIndex = IndexWrapperFactory.getIndexWrapper(wpIndexPath);
        wpIndex.setTimeFieldName(null);
        wpColStats = new IndexBackedCollectionStats();
        wpColStats.setStatSource(wpIndexPath);

        colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);

    }
    
    public void findEvents() throws Exception
    {        
        List<String> seen = new ArrayList<String>();
        for (String f1: dps.keySet()) {
            if (seen.contains(f1)) continue;
            
            List<String> r = new ArrayList<String>();
            r.add(f1);
            seen.add(f1);
            
            Iterator<String> it = dps.keySet().iterator();
            while (it.hasNext())
            {
                String f2 = it.next();
                if (f1.equals(f2) || seen.contains(f2)) continue;
                
                if (c(r, f2) < c(r, null)) {
                    r.add(f2);
                    seen.add(f2);
                }                
            }

            FeatureVector qv = new FeatureVector(stopper);
            for (String f: r)
                qv.addTerm(f);
                
            FeatureVector rm = buildRm(qv, stopper, index, colStats);

            String title = getQueryTitle(qv);
            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(rm);
            wpQuery.setTitle(title);
            
            System.out.println("\n\nEvent: " + title);
            System.out.println(rm);
            
            SearchHits hits = wpIndex.runQuery(wpQuery, 100);
            Iterator<SearchHit> iter = hits.iterator();
            int i=0;
            while (iter.hasNext()) {
                SearchHit hit = iter.next();
                System.out.println(title + "," + i + "," + hit.getScore() + "," + hit.getDocno());
            }
            
            
            // Build simple event model
            /*
            double[] e = new double[ts.get("_total_").length];      
            for (int i=0; i<e.length; i++)
                e[i] = 0;
            for (String f: r) {
                double[] y = ts.get(f);
                for (int i=0; i<y.length; i++)
                    e[i] += y[i];
                System.out.println("\t" + f);
            }
            for (int i=0; i<e.length; i++)
                System.out.print(e[i] + ",");
            System.out.println();
            */
        }        
    }   
    
    
    public String getQueryTitle(FeatureVector fv) {
        String title = "";
        for (String f: fv.getFeatures()) {
            title += f + " ";
        }
        return title.trim();
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
    public double c(List<String> r, String f) throws Exception {
        double c = 0;
        
        List<String> tmp = new ArrayList<String>();
        tmp.addAll(r);
        if(f != null) {
            tmp.add(f);
        
            double kl = kl(tmp);
            double d = overlap(tmp);
            double s = s(tmp);
    
            c = kl / (d*s);
        } else {        
            c = 1/ s(tmp);
        }
        return c;
    }
    
    double kl (List<String> r) throws Exception {
        double kl = 0;
        for (String f1: r) {
            for (String f2: r) {
                if (f1.equals(f2)) continue;
                double[] ts1 = ts.get(f1);
                double[] ts2 = ts.get(f2);
                double kl1 = rutil.kl2(ts1, ts2);
                double kl2 = rutil.kl2(ts2, ts1);
                kl = Math.max(kl, Math.max(kl1, kl2));
            }
        }
        return kl;
    }

        
    double overlap (List<String> r) {
        double d = Double.POSITIVE_INFINITY;
        for (String f1: r) {
            for (String f2: r) {
                if (f1.equals(f2)) continue;
                double d1 = d(f1, f2);
                d = Math.min(d, d1);
            }
        }
        return d;
    }
    
    // Document overlap between f1 and f2
    double d(String f1, String f2) {
        if (docids.get(f1) == null) {
            Map<Integer, Integer> d = index.getDocsByTerm(f1);
            docids.put(f1, d.keySet());
        }
        Set<Integer> s1 = docids.get(f1);
        
        if (docids.get(f2) == null) {
            Map<Integer, Integer> d = index.getDocsByTerm(f2    );
            docids.put(f2, d.keySet());
        }
        Set<Integer> s2 = docids.get(f2);
        Set<Integer> intersect = new HashSet<Integer>(s1);
        intersect.retainAll(s2);
        
        return (intersect.size() / (double)Math.min((double)s1.size(), (double)s2.size()));
    }
    
    double s (List<String> r) {
        double s = 0;
        for (String f: r) {
            s += dps.get(f);
        }
        return s;
    }
    
    public static Map<String, Double> readTermDps(String path) throws Exception 
    {
        Map<String, Double> terms = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
        List<String> lines = FileUtils.readLines(new File(path));
        for (String line: lines) {
            String[] fields = line.split(",");
            String term = fields[0];
            double dps = Double.parseDouble(fields[2]);
            terms.put(term, dps);
        }
        return terms;
    }
}
