package edu.gslis.events.main;

import java.io.File;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import edu.gslis.temporal.scorers.KDEScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;


public class FindEventsNMF 
{
    static final double ALPHA = 0.5;
    static final double MU = 2500;
    static final int NUM_TERMS = 10;
    static final int NUM_DOCS = 50;
    
    IndexWrapper index = null;
    IndexWrapper wpIndex = null;    
    Map<Integer, Map<String, Double>> factors = null;
    static int colStart = 0;
    static int colEnd = 0;
    static int colInterval = 0;
    RUtil rutil = null;
    Stopper stopper;
    CollectionStats colStats;
    CollectionStats wpColStats;
    String systemName;
    
    public static void main(String[] args) throws Exception {
        
        String factorPath = args[0];
        
        String indexPath = args[1];
        String stopperPath = args[2];
        String wpIndexPath = args[3];
        
        colStart = Integer.parseInt(args[4]);
        colEnd = Integer.parseInt(args[5]);
        colInterval = Integer.parseInt(args[6]); 
        String systemName = args[7];

        FindEventsNMF finder = new FindEventsNMF(factorPath, indexPath, stopperPath, wpIndexPath, systemName);
        finder.findEvents();
    }
    
    public FindEventsNMF(String factorPath, String indexPath, String stopperPath, 
            String wpIndexPath, String systemName) 
            throws Exception 
    {
        stopper= new Stopper(stopperPath);
        index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        factors = readFactors(factorPath);
        
        wpIndex = IndexWrapperFactory.getIndexWrapper(wpIndexPath);
        wpIndex.setTimeFieldName(null);
        wpColStats = new IndexBackedCollectionStats();
        wpColStats.setStatSource(wpIndexPath);

        colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);
        this.systemName = systemName;
        
        rutil = new RUtil();

    }
    
    public void findEvents() throws Exception 
    {
        // Run NMF on the TMI data
        DecimalFormat df = new DecimalFormat("#.###");
        // Google doc        
        for (int factor: factors.keySet()) {

            System.out.println("ID\t" + factor);
            System.out.println("Event\t");
            Map<String, Double> terms = factors.get(factor);
            FeatureVector qfv = new FeatureVector(null);
            FeatureVector wpfv = new FeatureVector(null);
            String summary = "";
            String query = "";
            for (String term: terms.keySet())  {
                qfv.addTerm(term, terms.get(term));                
                wpfv.addTerm(term, terms.get(term));
                summary += " " + term + "(" + df.format(terms.get(term)) + ")";
                query += " " + term;
            }
            
            GQuery gquery = new GQuery();
            gquery.setFeatureVector(qfv);
            gquery.setTitle(query);
            
            SearchHits hits = index.runQuery(gquery, 100);
            double[] docTimes = getTimes(hits, hits.size());
            
            System.out.println("Event model\t" + summary.trim());

            try 
            {            
                double[] constraints = rutil.getConstraints(docTimes);
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");            
                Date start = new Date((long)constraints[0]*1000); 
                Date mean = new Date((long)constraints[1]*1000); 
                Date end = new Date((long)constraints[2]*1000); 
                System.out.println("Estimated dates\t" + sdf.format(start) + 
                        "," + sdf.format(mean) + "," + sdf.format(end));
                int duration = (int)( (end.getTime() - start.getTime()) 
                        / (1000 * 60 * 60 * 24) );
                System.out.println("Estimated duration\t" +  duration);
            } catch (Exception e) {
                System.out.println("Problem processing doctimes for " + gquery.toString());
            }

            System.out.println("Google query\thttps://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"));

            System.out.println("Is this an event?\t");
            System.out.println(" - Short description\t");
            System.out.println(" - Estimated event date (MM/DD/YYYY)\t");
            System.out.println("Is the event in Wikipedia?\t");
            System.out.println(" - Best URL\t");
            
            // 2. Wikipedia            
            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(wpfv);
            wpQuery.setTitle(query);

            System.err.println("\n" + wpQuery.toString());
            hits = wpIndex.runQuery(wpQuery, 10);
            for (SearchHit hit: hits.hits()) {  
                System.out.println("https://en.wikipedia.org/wiki/" + hit.getDocno());
            }
            

            
            System.out.println("\n\n");
        }
        
        
        /*
         * JSON
        System.out.println("{");
        System.out.println("\t\"systems\":");
        System.out.println("\t[{");
        System.out.println("\t\t\"name\":\"" + systemName + "\",");
        System.out.println("\t\t\"events\":");
        System.out.println("\t\t[");
        
        int j=1;
        for (int factor: factors.keySet()) {
            System.out.println("\t\t\t{");

            System.out.println("\t\t\t\t\"eventId\": \"" + factor + "\",");
            System.out.println("\t\t\t\t\"termWeights\": {");

            Map<String, Double> terms = factors.get(factor);
            FeatureVector fv = new FeatureVector(null);
            String title = "";
            int i=1;
            for (String term: terms.keySet())  {
                fv.addTerm(term, terms.get(term));
                String json = "\t\t\t\t\t\"" + term + "\": \"" + terms.get(term) + "\"";
                if (i < terms.size()) 
                    json += ",";
                System.out.println(json);
                title += " " + term;
                i++;
            }
            title.trim();
            
            System.out.println("\t\t\t\t},");
            System.out.println("\t\t\t\t\"summary\": \"" + title.trim() + "\",");

            //fv.addTerm("1988", 1);
            
            // 2. Wikipedia
            
            System.out.println("\t\t\t\t\"results\": {");

            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(fv);
            wpQuery.setTitle(title);
            SearchHits hits = wpIndex.runQuery(wpQuery, 10);
//          System.out.println(fv.toString());
            i = 1;
            for (SearchHit hit: hits.hits()) {    
                String json = "\t\t\t\t\t\"" + hit.getDocno() + "\": \"" + hit.getScore() + "\"";
                if (i < hits.size()) 
                    json += ",";
                System.out.println(json);
                i++;
            }
            System.out.println("\t\t\t\t}");
            
            
            if (j < factors.size())
                System.out.println("\t\t\t},");
            else 
                System.out.println("\t\t\t}");
            j++;



            // 1. Relevance model
            System.out.println("-------Relevance Model--------");
            FeatureVector rm = buildRm(fv, stopper, index, colStats);
            wpQuery.setFeatureVector(rm);
            wpQuery.setTitle(title);
            hits = wpIndex.runQuery(wpQuery, 10);
            System.out.println(rm.toString(10));
            for (SearchHit hit: hits.hits()) {                
                System.out.println(hit.getDocno());
            }
            System.out.println();
            
            //hits = index.runQuery(wpQuery, 100);
            //double[] docTimes = getTimes(hits, hits.size());
                
            //String times = "";
            //for (double t:docTimes) {
            //    times += (long)t + ",";
           // }
            //System.out.println(times);
            
            //double[] constraints = rutil.getConstraints(docTimes);
            //SimpleDateFormat df = new SimpleDateFormat("MM/dd/yyyy");            
            //Date start = new Date((long)constraints[0]*1000); 
            //Date mean = new Date((long)constraints[1]*1000); 
            //Date end = new Date((long)constraints[2]*1000); 
            //long sigma = (long)constraints[2]; 
//            System.out.println("Estimated dates: " + df.format(start) + 
//                    "," + df.format(mean) + "," + df.format(end) + "," + sigma);
            //double dps = rutil.dps(docTimes);
            
            //System.out.println(factor + "," + title + "," + first.replaceAll(",", "") + "," + df.format(mean) + "," + dps);
        }
        
        System.out.println("\t\t]");
        System.out.println("\t}]");
        System.out.println("}");  
        */
    }   
    
    public long mean(long[] x) {
        long mean = 0;
        for (long y: x)
            mean += y;
       
        return mean/(long)x.length;
            
    }
    
    public static double[] getTimes(SearchHits hits, int k) {

        double[] times = new double[k];

        for (int i=0; i<k; i++) {
            SearchHit hit = hits.getHit(i);
            times[i] = getTime(hit);
        }

        return times;
    }
    
    public static double getTime(SearchHit hit) {
        String epochStr = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));

        long epoch = Long.parseLong(epochStr);
        return epoch;
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
    
    public static Map<Integer, Map<String, Double>> readFactors(String factorPath) throws Exception 
    {
        Map<Integer, Map<String, Double>> factors = new TreeMap<Integer, Map<String, Double>>();
        List<String> lines = FileUtils.readLines(new File(factorPath));
        for (String line: lines) {
            String[] fields = line.split(",");
            if (fields.length == 3) {
                String term = fields[0];
                double weight = Double.parseDouble(fields[1]);
                int factor = Integer.parseInt(fields[2]);
                Map<String, Double> map = factors.get(factor);
                if (map == null)
                    map = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                map.put(term, weight);
                factors.put(factor, map);
            }
        }
        
        return factors;
    }
     
}
