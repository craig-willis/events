package edu.gslis.events.main;

import java.io.File;
import java.net.URLEncoder;
import java.text.DecimalFormat;
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
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;


/**
 * Read topic-keys.out
 *  Topic, Hyperparam, Terms
 * Read topics-10.out
 *  Topic, term, probability
 *  
 * Order topics by hyperparam
 * 
 * Construct query
 * Run query against WP
 * 
 * @author cwillis
 *
 */
public class FindEventsLDA 
{
    
    IndexWrapper index = null;
    IndexWrapper wpIndex = null;    
    Map<Integer, Double> topicAcfMap = new ValueComparableMap<Integer, Double>(Ordering.natural().reverse());
    Map<Integer, Map<String, Double>> topicsMap = new TreeMap<Integer, Map<String, Double>>();
    RUtil rutil = null;
    Stopper stopper;
    CollectionStats colStats;
    CollectionStats wpColStats;
    String systemName;
    
    public static void main(String[] args) throws Exception {
        
        String topicAcfPath = args[0];
        String topicsPath = args[1];
        
        String indexPath = args[2];
        String stopperPath = args[3];
        String wpIndexPath = args[4];

        FindEventsLDA finder = new FindEventsLDA(topicAcfPath, topicsPath, indexPath, stopperPath, wpIndexPath);
        finder.findEvents();
    }
    
    public FindEventsLDA(String topicAcfPath, String topicsPath, String indexPath, String stopperPath, 
            String wpIndexPath) 
            throws Exception 
    {
        stopper= new Stopper(stopperPath);
        index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        readTopics(topicAcfPath, topicsPath);
        
        wpIndex = IndexWrapperFactory.getIndexWrapper(wpIndexPath);
        wpIndex.setTimeFieldName(null);
        wpColStats = new IndexBackedCollectionStats();
        wpColStats.setStatSource(wpIndexPath);

        colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);
        
        rutil = new RUtil();

    }
    
    public void findEvents() throws Exception 
    {
        // Run NMF on the TMI data
        DecimalFormat df = new DecimalFormat("#.###");
        // Google doc        
        int i = 1;
        for (int topic: topicAcfMap.keySet()) {

            double weight = topicAcfMap.get(topic);
            System.out.println("ID\t" + i + " (" + topic + "=" + weight + ")");
            System.out.println("Event\t");
            Map<String, Double> terms = topicsMap.get(topic);
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
            
            
            System.out.println("Event model\t" + summary.trim());

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
            SearchHits hits = wpIndex.runQuery(wpQuery, 10);
            for (SearchHit hit: hits.hits()) {  
                System.out.println("https://en.wikipedia.org/wiki/" + hit.getDocno());
            }
            
            System.out.println("\n\n");
            i++;
        }
        
    }   
    
    public void readTopics(String topicAcfPath, String topicsPath) throws Exception 
    {
        List<String> lines = FileUtils.readLines(new File(topicAcfPath));
        for (String line: lines) {
            String[] fields = line.split(",");
            int topic = Integer.parseInt(fields[0]);
            double acf = Double.parseDouble(fields[1]);
            topicAcfMap.put(topic, acf);
        }
        
        lines = FileUtils.readLines(new File(topicsPath));
        for (String line: lines) {
            String[] fields = line.split(",");
            if (fields.length == 5) {
                int topic = Integer.parseInt(fields[0]);
                String term = fields[1];
                double weight = Double.parseDouble(fields[4]);
                Map<String, Double> map = topicsMap.get(topic);
                if (map == null)
                    map = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                map.put(term, weight);
                topicsMap.put(topic, map);
            }
        }
    }
     
     
}
