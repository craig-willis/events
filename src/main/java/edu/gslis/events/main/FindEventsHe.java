package edu.gslis.events.main;

import java.io.File;
import java.net.URLEncoder;
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
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.util.FeatureHe;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.temporal.util.ValueComparableMap;
import edu.gslis.textrepresentation.FeatureVector;
import edu.gslis.utils.Stopper;


/**
 * Implements the greedy event detection algorithm from He et al 2007
 * to construct models used to query Wikipedia.
 * 
 * For all features with Pf > T/2
 * Truncate trajectory by keeping only bursty period, 
 *    modeled with Gaussian
 *
 */
public class FindEventsHe 
{    
    IndexWrapper index = null;
    IndexWrapper wpIndex = null;
    Map<String, Double> dominantPeriodMap = new HashMap<String, Double>();
    Map<String, Double> dominantPowerSpectrumMap = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
    Map<String, FeatureHe> features = new HashMap<String, FeatureHe>();
    
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
        String featuresPath = args[1];
        String indexPath = args[2];
        String stopperPath = args[3];
        String wpIndexPath = args[4];

        colStart = Integer.parseInt(args[5]);
        colEnd = Integer.parseInt(args[6]);
        colInterval = Integer.parseInt(args[7]); 

        FindEventsHe finder = new FindEventsHe(dpsPath, featuresPath, indexPath, stopperPath, wpIndexPath);
        finder.findEvents();
    }
    
    public FindEventsHe(String dpsPath, String featuresPath, String indexPath, String stopperPath, String wpIndexPath) 
            throws Exception 
    {
        stopper= new Stopper(stopperPath);
        index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        readFeatures(dpsPath, featuresPath);
        
        wpIndex = IndexWrapperFactory.getIndexWrapper(wpIndexPath);
        wpIndex.setTimeFieldName(null);
        wpColStats = new IndexBackedCollectionStats();
        wpColStats.setStatSource(wpIndexPath);

        colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);

    }
    
    public void findEvents() throws Exception
    {   
        
        List<String> hh = new ArrayList<String>();
        hh.addAll(dominantPowerSpectrumMap.keySet());
        
        Set<String> remove = new HashSet<String>();

        Iterator<String> it = hh.iterator();
        int k=0;
        while (it.hasNext()) {
            String f1 = it.next();
            
            if (remove.contains(f1)) {
                it.remove();
                continue;
            }
            
            k++;
            
            List<String> r = new ArrayList<String>();
            r.add(f1);
            double c = c(r, null);
            it.remove();
            String min = "";

            while (!hh.isEmpty()) {

                // Find f2 that minimizes c(R)
                for (String f2: hh) {
                    if (f2.equals(f1) || remove.contains(f2)) continue;
                    double x = 0;
                    if ( (x = c(r, f2)) < c) {
                        //System.out.println("\t" + f2 + "," + x);
                        c = x; 
                        min = f2;
                    }
                }
            
                if (c < c(r, null)) {
                    r.add(min);
                    remove.add(min);
                } else {
                    break;
                }
            }
        
            FeatureVector qv = new FeatureVector(stopper);
            for (String f: r) {
                String[] feat = f.split("_");
                qv.addTerm(feat[0]);
            }
    
            
            String title = getQueryTitle(qv);

            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(qv);
            wpQuery.setTitle(title);
                        
            
            System.out.println("ID\t" + k);
            System.out.println("Event\t");
            System.out.println("Event model\t" + title);

            System.out.println("Google query\thttps://www.google.com/search?q=" + URLEncoder.encode(title, "UTF-8"));

            System.out.println("Is this an event?\t");
            System.out.println(" - Short description\t");
            System.out.println(" - Estimated event date (MM/DD/YYYY)\t");
            System.out.println("Is the event in Wikipedia?\t");
            System.out.println(" - Best URL\t");

            

            //System.out.println(qv);
            SearchHits hits = wpIndex.runQuery(wpQuery, 10);
            Iterator<SearchHit> iter = hits.iterator();
            while (iter.hasNext()) {
                SearchHit hit = iter.next();
                System.out.println("https://en.wikipedia.org/wiki/" + hit.getDocno());
            }

            System.out.println("\n\n");
    }

        
        /*
        // dps is a treemap order by the value descending (terms with highest DPS values first)
        int event=1;
        for (String f1: dominantPowerSpectrumMap.keySet()) 
        {
            if (seen.contains(f1)) continue;

            List<String> r = new ArrayList<String>();
            r.add(f1);
            seen.add(f1);
            System.out.println("\n\n" + f1);

            // Find terms that minimize c(R)
            Set<String> hh = dominantPowerSpectrumMap.keySet();
            Set<String> removed = new HashSet<String>();
            double c = c(r, null);
            String min = "";

            while (!hh.isEmpty()) {
                
                // Find f that minimized c(R)
                for (String f: hh) 
                {                 
                    if (f.equals(f1) || removed.contains(f)) continue;
                    double x = 0;
                    if ( (x = c(r, f)) < c) {
                        System.out.println("\t" + f + "," + x);
                        c = x; 
                        min = f;
                    }
                }
                
                if (c < c(r, null)) {
                    r.add(min); 
                    removed.add(min);
                    //seen.add(min);
                }
                else
                {
                    break;
                }
            }
            


            FeatureVector qv = new FeatureVector(stopper);
            for (String f: r) {
                String[] feat = f.split("_");
                qv.addTerm(feat[0]);
            }


            String title = getQueryTitle(qv);

            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(qv);
            wpQuery.setTitle(title);
                        
            
            System.out.println("ID\t" + event);
            System.out.println("Event\t");
            System.out.println("Event model\t" + title);

            System.out.println("Google query\thttps://www.google.com/search?q=" + URLEncoder.encode(title, "UTF-8"));

            System.out.println("Is this an event?\t");
            System.out.println(" - Short description\t");
            System.out.println(" - Estimated event date (MM/DD/YYYY)\t");
            System.out.println("Is the event in Wikipedia?\t");
            System.out.println(" - Best URL\t");

            

            //System.out.println(qv);
            SearchHits hits = wpIndex.runQuery(wpQuery, 10);
            Iterator<SearchHit> iter = hits.iterator();
            while (iter.hasNext()) {
                SearchHit hit = iter.next();
                System.out.println("https://en.wikipedia.org/wiki/" + hit.getDocno());
            }

            System.out.println("\n\n");
            event++;
        }        
        */
    }   
    
    
    public String getQueryTitle(FeatureVector fv) {
        String title = "";
        for (String f: fv.getFeatures()) {
            title += f + " ";
        }
        return title.trim();
    }

    /**
     * Cost function
     * @param r Event model
     * @param f Feature to be added to event model
     * @return
     * @throws Exception
     */
    public double c(List<String> r, String f) throws Exception {
        double c = 0;
        
        if(f != null) {
            List<String> tmp = new ArrayList<String>();
            tmp.addAll(r);
            tmp.add(f);
        
            double kl = kl(tmp);
            double d = overlap(tmp);
            double s = s(tmp);
    
            c = kl / (d*s);
        } else if (r.size() > 1){        
            
            double kl = kl(r);
            double d = overlap(r);
            double s = s(r);
    
            c = kl / (d*s);

        } else {
            c = 1/ s(r);
        }
        return c;
    }

      
    Map<String, double[][]> burstMap = new HashMap<String, double[][]>();

    double kl (List<String> r) throws Exception 
    {
        double kl = 0;
        for (String f1: r) {
            double[] ts1 = features.get(f1).getValue();
            
            try 
            {                                
    
                for (String f2: r) {
                    if (f1.equals(f2)) continue;
                    double[] ts2 = features.get(f2).getValue();
                    
                    // Calculate kl(f1 | f2) and kl(f2 | f1)
                    double kl1 = rutil.kl2(ts1, ts2);
                    double kl2 = rutil.kl2(ts2, ts1);
                    kl = Math.max(kl, Math.max(kl1, kl2));
                }
            } catch (Exception e) {
                String x = new String();
                for (double d: ts1) 
                    x += d*100 + " ";
                System.out.println(x);

                System.err.println("Error calculating kl for " + f1);
                System.out.println(x);
                e.printStackTrace();
            }
        }
        return kl;
    }

    
    /**
     * For all features in r, calculate the minimum document overlap
     * between all feature pairs
     * @param r Feature set
     * @return
     */
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
    /**
     * Document overlap between features
     * @param f1
     * @param f2
     * @return
     */
    double d(String f1, String f2) {
        if (docids.get(f1) == null) {
            Map<Integer, Integer> d = index.getDocsByTerm(f1.split("_")[0]);
            docids.put(f1, d.keySet());
        }
        Set<Integer> s1 = docids.get(f1);
        
        if (docids.get(f2) == null) {
            Map<Integer, Integer> d = index.getDocsByTerm(f2.split("_")[0]);
            docids.put(f2, d.keySet());
        }
        Set<Integer> s2 = docids.get(f2);
        Set<Integer> intersect = new HashSet<Integer>(s1);
        intersect.retainAll(s2);
        
        return (intersect.size() / (double)Math.min((double)s1.size(), (double)s2.size()));
    }
    
    /**
     * Sum of the dominant power spectra for all features in r.
     * @param r Features
     * @return
     */
    double s (List<String> r) {
        double s = 0;
        for (String f: r) {
            s += dominantPowerSpectrumMap.get(f);
        }
        return s;
    }
    
    public void readTermDps(String path) throws Exception 
    {
        // One strategy is to re-create the term/time series index 
        // for only those terms in the dps, using DFIDF, and
        // to go ahead and create a separate vector for each 
        // fit Gaussian.
        List<String> lines = FileUtils.readLines(new File(path));
        for (String line: lines) {
            String[] fields = line.split(",");
            String term = fields[0];
            double dp = Double.parseDouble(fields[1]);
            double dps = Double.parseDouble(fields[2]);
            dominantPeriodMap.put(term, dp);
            dominantPowerSpectrumMap.put(term, dps);
        }
        
    }
    
    void readFeatures(String dpsPath, String featuresPath) throws Exception
    {
        Map<String, Double> dpMap = new HashMap<String, Double>();
        Map<String, Double> dpsMap = new ValueComparableMap<String, Double>(Ordering.natural().reverse());

        List<String> dpLine = FileUtils.readLines(new File(dpsPath));
        for (String line: dpLine) {
            String[] fields = line.split(",");
            String term = fields[0];
            double dp = Double.parseDouble(fields[1]);
            double dps = Double.parseDouble(fields[2]);
            dpMap.put(term, dp);
            dpsMap.put(term, dps);
        }
        List<String> lines = FileUtils.readLines(new File(featuresPath));

        for (String line: lines) {
            String[] fields = line.split(",");
            String key = fields[0];
            String[] pair = key.split("_");
            String term = pair[0];
            double[] ts  = new double[fields.length-1];
            for (int i=1; i<fields.length; i++) {
                ts[i-1] = Double.parseDouble(fields[i]);
            }
            FeatureHe feature = new FeatureHe(term, ts);
            features.put(key, feature);

            if (dpMap.get(term) != null) 
            {
                double dp = dpMap.get(term);
                double dps = dpsMap.get(term);
                
                dominantPeriodMap.put(key, dp);
                dominantPowerSpectrumMap.put(key, dps);
            }
        }
    }
}
