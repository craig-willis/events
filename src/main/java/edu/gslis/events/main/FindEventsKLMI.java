package edu.gslis.events.main;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

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


public class FindEventsKLMI 
{
    static final double ALPHA = 0.5;
    static final double MU = 2500;
    static final int NUM_TERMS = 10;
    static final int NUM_DOCS = 50;
    
    TimeSeriesIndex ts = new TimeSeriesIndex();
    IndexWrapper index = null;
    IndexWrapper wpIndex = null;
    Map<String, Double> acfMap = null;
    Map<String, Double> dpsMap = null;
    Map<String, Map<String, Double>> miMap = null;
    RUtil rutil = new RUtil();
    Map<String, Set<Integer>> docids = new HashMap<String, Set<Integer>>();
    static int colStart = 0;
    static int colEnd = 0;
    static int colInterval = 0;

    Stopper stopper;
    CollectionStats colStats;
    CollectionStats wpColStats;
    
    public static void main(String[] args) throws Exception {
        String acfPath = args[0];
        String dpsPath = args[1];
        String miPath = args[2];
        String tsPath = args[3];
        String indexPath = args[4];
        String stopperPath = args[5];
        String wpIndexPath = args[6];

        colStart = Integer.parseInt(args[7]);
        colEnd = Integer.parseInt(args[8]);
        colInterval = Integer.parseInt(args[9]); 

        FindEventsKLMI finder = new FindEventsKLMI(acfPath, miPath, tsPath, indexPath, stopperPath, wpIndexPath, dpsPath);
        finder.findEvents();
    }
    public FindEventsKLMI(String acfPath, String miPath, String tsPath, String indexPath, String stopperPath, 
            String wpIndexPath, String dpsPath) 
            throws Exception 
    {
        ts = new TimeSeriesIndex();
        ts.open(tsPath, true);
        stopper= new Stopper(stopperPath);
        index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        acfMap = readTermAcf(acfPath);
        miMap = readTermMi(miPath);
        dpsMap = readTermDps(dpsPath);
        
        wpIndex = IndexWrapperFactory.getIndexWrapper(wpIndexPath);
        wpIndex.setTimeFieldName(null);
        wpColStats = new IndexBackedCollectionStats();
        wpColStats.setStatSource(wpIndexPath);

        colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);

    }
    
    public void findEventsSimple() throws Exception {
        
        FileWriter fw = new FileWriter("matrix.out");
        for (String f1: acfMap.keySet()) 
        {
            for (String f2: acfMap.keySet()) 
            {
                double c1 = sim(f1, f2);
                double c2 = sim(f2, f1);
                
                double c = (c1 + c2) / 2;
                fw.write(f1 + "," + f2 + "," + c + "\n");
                                   
            }
        }            
        fw.close();
    }
    
    // Lavrenko argues that KL divergence can be treated as minimum 
    // discrimination information statistics, based on Chi Square. 
    // mdis(q,d) = 2 * Sum N * RMq(v) ln (RMq(v) / RMd(v))
    //           = 2N * KL (RMq || RMd)
    public void findEvents() throws Exception 
    {
        // Build a set of language models based on the terms with highest ACF
        Map<String, FeatureVector> lms = new HashMap<String, FeatureVector>();
        for (String f1: acfMap.keySet()) 
        {
            FeatureVector lm = new FeatureVector(stopper);
            
            lm.addTerm(f1, 1);
            for (String f2: acfMap.keySet()) 
            {
                if (f1.equals(f2)) continue;
                
                //double ccf = ccf(f1, f2);
                //double sj = sj(f1, f2);
                //double kl = kl(f2, f1);
                double mi = mi(f1, f2);
                //System.out.println(f1 + "," + f2 + "," + sj + "," + mi + "," + sj*mi);

                lm.addTerm(f2, mi);
            }
            lm.normalize();
            lms.put(f1, lm);
        }

        // Pairwise comparison of language models.                 
        Set<String> merged = new HashSet<String>();
        for (String f1: acfMap.keySet()) {
            System.out.println(f1);
            if (merged.contains(f1)) continue;
            FeatureVector lm1 = lms.get(f1);
            for (String f2: acfMap.keySet()) 
            {
                if (f1.equals(f2)) continue;
                if (merged.contains(f2)) continue;

                if (mi(f1, f2) ==0 ) continue;
                
                FeatureVector lm2 = lms.get(f2);
                
                Set<String> f = new HashSet<String>();
                for (String t: lm1.getFeatures())  {
                    if (lm1.getFeatureWeight(t) > 0) 
                        f.add(t);
                }
                for (String t: lm2.getFeatures())  {
                    if (lm1.getFeatureWeight(t) > 0) 
                        f.add(t);
                }
                int N = f.size();
                double mdis = mdis(lm1, lm2, f);
                int df = N-1;
                
                if (df > 1) {
                    ChiSquaredDistribution csdist = new ChiSquaredDistribution(df);
                    
                    double alpha = 0.00001;
                    double x1 = csdist.inverseCumulativeProbability(1 - alpha/2);
                    double x2 = csdist.inverseCumulativeProbability(alpha /2);
                    
                    if (mdis <=x1 && mdis >= x2) {
                        System.out.println(f1 + "," + f2 + "," + x1 + "," + x2 + "," + mdis  + "*");
                    }
                }
            }
            

        }
    }    

    public void findEventsMuSigma() throws Exception 
    {
        // Build a set of language models based on the terms with highest ACF
        Map<String, FeatureVector> lms = new HashMap<String, FeatureVector>();
        //Set<String> test = new HashSet<String>();
        //test.add("rushdie");
        //test.add("satanic");
        //test.add("salman");
        //test.add("sein");
        //for (String f1: test) 
        for (String f1: acfMap.keySet()) 
        {
            FeatureVector lm = new FeatureVector(stopper);
            
            lm.addTerm(f1, 1);
            for (String f2: acfMap.keySet()) 
            {
                if (f1.equals(f2)) continue;
                double c1 = sim(f1, f2);
                double c2 = sim(f2, f1);
                double c = (c1 + c2) / 2;
                if (c > 0) {
                    lm.addTerm(f2, c);
                }
            }
            lm.normalize();
            lms.put(f1, lm);
        }

        // Pairwise comparison of language models. 
        
        //double cq = chiSq(lms.get("lwin"), lms.get("sein"));
        //System.out.println(cq);
        
        //double kl1 = kl(lms.get("lwin"), lms.get("sein"));
        //double kl2 = kl(lms.get("sein"), lms.get("lwin"));
        //System.out.println(Math.exp(kl1) + "," + Math.exp(kl2));
        
        Set<String> merged = new HashSet<String>();
        for (String f1: acfMap.keySet()) {
            if (merged.contains(f1)) continue;
            FeatureVector lm1 = lms.get(f1);
            DescriptiveStatistics ds = new DescriptiveStatistics();
            Map<String, Double> kls = new HashMap<String, Double>();
            System.out.println("Finding matches for " + f1);
            for (String f2: acfMap.keySet()) 
            {
                if (f1.equals(f2)) continue;
                if (merged.contains(f2)) continue;

                FeatureVector lm2 = lms.get(f2);
                double kl = Math.exp(kl(lm1, lm2));
//                double kl2 = kl(lm2, lm1);
//                double kl = Math.exp( (kl1 + kl2)/2) ;
                ds.addValue(kl);
                kls.put(f2,  kl);
                //System.out.println("Comparing " + f1 + " to " + f2 + "=" + kl);
            }
            
            //System.out.println(lms.get(f1));    
            if (lms.get(f1).getFeatureCount() > 5) {
                System.out.println("\t m=" + ds.getMean() + ", sd=" + ds.getStandardDeviation());
                int i=0;
                for (String f2: kls.keySet()) {
                    double kl = kls.get(f2);
                    if (lms.get(f2).getFeatureCount() > 5 && (kl < 0.75) && (kl > ds.getMean() + 2*ds.getStandardDeviation())) {
                        System.out.println("\t merging " + f1 + " and " + f2  + " (" + kl + ")");
                        //System.out.println(lms.get(f2));
                        merged.add(f2);
                        i++;
                    }
                }
                if (i > 0)
                    merged.add(f1);
            }
        }
        
        // Given two language models (multinomial), test whether they are from the same distribution
        
        // For each 
    }
    
   
    public double mdis(FeatureVector fv1, FeatureVector fv2, Set<String> features) {
        double mdis = 0;
        
        double N = features.size();
        
        for (String feature: features) {
            double p1 = fv1.getFeatureWeight(feature);
            double p2 = fv2.getFeatureWeight(feature);
            if (p1 > 0 && p2 > 0) 
                mdis += N * p1 * Math.log(p1/p2)/Math.log(2);
        }        
        return 2*mdis;
    }
    
    public double kl(String f1, String f2) throws Exception {
        double[] ts1 = ts.get(f1);
        double[] ts2 = ts.get(f2);
        if (ts1 == null || ts2 == null) { System.err.println("Didn't find " + f1 + " or " + f2);  return 0; }

        double kl1 = rutil.kl2(ts1, ts2);
        return Math.exp(-1*kl1);
    }
    public double kl(FeatureVector fv1, FeatureVector fv2) {
        double ll = 0;
        
        Set<String> features = new HashSet<String>();
        features.addAll(fv1.getFeatures());
        features.addAll(fv2.getFeatures());
        
        for (String feature: features) {
            double w2 = fv2.getFeatureWeight(feature);
            double l2 = fv2.getLength();
            
            double cp = (index.termFreq(feature)) / index.termCount();
            double pr = (w2 + NUM_TERMS*cp)/(l2 + NUM_TERMS);

            double w1 = fv1.getFeatureWeight(feature);
            ll += w1 * Math.log(pr);                        
        }        
        return ll;
    }
    public double chiSq(FeatureVector fv1, FeatureVector fv2) {
        double q = 0;
        FeatureVector exp = new FeatureVector(stopper);

        for (String t1: fv1.getFeatures()) {
            System.out.println(t1 + "," + fv1.getFeatureWeight(t1));
            exp.addTerm(t1, fv1.getFeatureWeight(t1));
        }
        for (String t2: fv2.getFeatures()) {
            System.out.println(t2 + "," + fv2.getFeatureWeight(t2));
            exp.addTerm(t2, fv2.getFeatureWeight(t2));
        }
        exp.normalize();
        
        for (String t: exp.getFeatures()) {
            System.out.println(t + "," + fv1.getFeatureWeight(t) + "," + exp.getFeatureWeight(t));
            q += Math.pow((fv1.getFeatureWeight(t) - exp.getFeatureWeight(t)), 2) / exp.getFeatureWeight(t);
        }
        
        for (String t: exp.getFeatures()) {
            System.out.println(t + "," + fv2.getFeatureWeight(t) + "," + exp.getFeatureWeight(t));
            q += Math.pow((fv2.getFeatureWeight(t) - exp.getFeatureWeight(t)), 2) / exp.getFeatureWeight(t);
        }
        return q;
    }
    
    
    
    public void findEventsRmOnly() throws Exception {
        for (String f: acfMap.keySet()) 
        {
            FeatureVector qv = new FeatureVector(stopper);
            qv.addTerm(f);
                
            System.out.println(qv);
            
            FeatureVector rm = buildRm(qv, stopper, index, colStats);

            String title = getQueryTitle(qv);
            GQuery wpQuery = new GQuery();
            wpQuery.setFeatureVector(rm);
            wpQuery.setTitle(title);
            
            System.out.println("\n\nEvent: " + title);
            System.out.println(rm);
            
        }
    }
    
    public void findEventsBak() throws Exception 
    {
        Map<String, Map<String, Double>> candidates = new TreeMap<String, Map<String, Double>>();

        for (String f1: acfMap.keySet()) 
        {
            for (String f2: acfMap.keySet()) 
            {
                if (f1.equals(f2)) continue;
                
                // Calculate similarity between f1/f2 and f2/f1
                double c1 = sim(f1, f2);
                double c2 = sim(f2, f1);
                if (c1 > c2) 
                {
                    Map<String, Double> c = candidates.get(f1);
                    if (c == null)
                        c = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                    
                    c.put(f2, c1);
                    candidates.put(f1, c);
                }
                else  {                        
                    Map<String, Double> c = candidates.get(f2);
                    if (c == null)
                        c = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                    
                    c.put(f1, c2);
                    candidates.put(f2, c);
                }
            }
        }     
        
        Set<String> assigned = new HashSet<String>();
        for (String f1: acfMap.keySet()) {
            try {
                if (!assigned.contains(f1) && candidates.get(f1).size() > 0) {
                    assigned.add(f1);
                    System.out.println("\n\n" + f1);
                    for (String f2: candidates.get(f1).keySet()) {
                        if (!assigned.contains(f2)) {
                            assigned.add(f2);
                            System.out.println ("\t" + f2 + ", " + candidates.get(f1).get(f2));
                        }
                    }
                }
            } catch (Exception e) {
                
            }
        }
    }
    

    public void findEventsACF() throws Exception {
        
        Map<String, Map<String, Double>> candidates = new TreeMap<String, Map<String, Double>>();

        List<String> seen = new ArrayList<String>();
        for (String f1: acfMap.keySet()) 
        //for (String f1: dpsMap.keySet()) 
        {
            for (String f2: acfMap.keySet()) 
            //for (String f2: dpsMap.keySet()) 
            {
                if (f1.equals(f2) || seen.contains(f1 + "," + f2) || seen.contains(f2 + "," + f1)) continue;
                double c1 = sim(f1, f2);
                double c2 = sim(f2, f1);
                if (c1 > 0.005 || c2 > 0.005) {
                    if (c1 > c2) {
                        Map<String, Double> c = candidates.get(f1);
                        if (c == null)
                            c = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                        
                        c.put(f2, c1);
                        candidates.put(f1, c);
                    }
                    else  {                        
                        Map<String, Double> c = candidates.get(f2);
                        if (c == null)
                            c = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
                        
                        c.put(f1, c2);
                        candidates.put(f2, c);
                    }
                    seen.add(f1 + "," + f2);
                    seen.add(f2 + "," + f1);
                }                

                                   
            }
        }     
        
        Set<String> assigned = new HashSet<String>();
        for (String f1: acfMap.keySet()) {
            try {
                if (!assigned.contains(f1) && candidates.get(f1).size() > 0) {
                    assigned.add(f1);
                    System.out.println("\n\n" + f1);
                    for (String f2: candidates.get(f1).keySet()) {
                        if (!assigned.contains(f2)) {
                            assigned.add(f2);
                            System.out.println ("\t" + f2 + ", " + candidates.get(f1).get(f2));
                        }
                    }
                }
            } catch (Exception e) {
                
            }
        }
    }

    public double ccf(String f1, String f2) throws Exception {
        double[] ts1 = ts.get(f1);
        double[] ts2 = ts.get(f2);
        if (ts1 == null || ts2 == null) 
            return 0;
        
        double ccf = rutil.ccf(ts1, ts2, 1);
        return ccf;
    }
    public double sj(String f1, String f2) throws Exception {
        double[] ts1 = ts.get(f1);
        double[] ts2 = ts.get(f2);
        if (ts1 == null || ts2 == null) 
            return 0;
        
        double kl1 = rutil.kl2(ts1, ts2);
        double kl2 = rutil.kl2(ts2, ts1);
        
        double sj = (kl1 + kl2) / 2;
        return Math.exp(-1*sj);
    }
    
    public double sim(String f1, String f2) throws Exception {
        double[] ts1 = ts.get(f1);
        double[] ts2 = ts.get(f2);
        double kl = Math.exp(-1*rutil.kl2(ts1, ts2));
        double mi = mi(f1, f2);
        if (mi < 0) mi = 0;
        return kl*mi;
    }
    
    public void findEventsOld() throws Exception
    {        
        List<String> seen = new ArrayList<String>();
        for (String f1: acfMap.keySet()) {
            if (seen.contains(f1)) continue;

//            System.out.println("\n\n" + f1);

            //double ckl = 0;
            //double cmi = 0;
            //double i = 1;
            FeatureVector r = new FeatureVector(stopper);
            r.addTerm(f1, 1);
            Iterator<String> it = acfMap.keySet().iterator();
            Map<String, Double> candidates = new ValueComparableMap<String, Double>(Ordering.natural().reverse());
            
            while (it.hasNext())
            {
                String f2 = it.next();
                if (f1.equals(f2)) continue;

                double c2 = c(r, f2);
                if (c2 > 0) {
                    candidates.put(f2, c2);
                }
                
                /*
                double c1 = c(r, null);
                double c2 = c(r, f2);
                
                if (c2 > c1) {
                    r.addTerm(f2, c2);
                    seen.add(f2);
                }
                */
            }
            candidates.put(f1, 1D);
            
            System.out.println(f1 + " candidates:");  
            for (String s1: candidates.keySet()) {
                for (String s2: candidates.keySet()) {
                    if (!s1.equals(s2) && !seen.contains(s1) && !seen.contains(s2)) {
                        double c = c(s1, s2);
                        if (c > 0.01) {
                            r.addTerm(s1);
                            r.addTerm(s2);
                            seen.add(s1);
                            seen.add(s2);
                        }
                    }
                }
            }
            /*
            for (String f2: candidates.keySet()) {
                System.out.println("\t" + f2);
                double c1 = c(r, null);
                double c2 = c(r, f2);
                
                if (c2 > c1) {
                    r.addTerm(f2, c2);
                    seen.add(f2);
                }
            }
            */
                
                /*
                double[] ts1 = ts.get(f1);
                double[] ts2 = ts.get(f2);

                double mi = mi(f1, f2);
                if (mi < 0) mi = 0;
                
                // Likelihood that F1 generated F2
                double kl1 = Math.exp(-1*rutil.kl2(ts2, ts1));
                double kl2 = Math.exp(-1*rutil.kl2(ts1, ts2));
                
                if (mi > 0 && kl1 < kl2) {
                    double ctmp1 = ((ckl+kl1) * (cmi+mi)) / (i+1);
                    double ctmp2 = ((ckl*cmi)/i);
                    if (ctmp1 > ctmp2) {
                        //System.out.println("\t" + f2);
                        r.addTerm(f2, ctmp1);
                        ckl += kl1;
                        cmi += mi;
                        i++;
                    }
                }
                */

            if (r.getLength() > 1)
                System.out.println(r);

        }        
    }   
    
    
    public double c(String s1, String s2) throws Exception {
        FeatureVector f = new FeatureVector(stopper);
        f.addTerm(s1);
        return c(f, s2);
    }
    public double c(FeatureVector r, String f) throws Exception {
        double c = 0;
        
        List<String> tmp = new ArrayList<String>();
        tmp.addAll(r.getFeatures());
        if(f != null)
            tmp.add(f);
        if (tmp.size() > 1) {
            double kl = Math.exp(-1*(kl(tmp)));
            double mi = mi(tmp);
            c = kl*mi;
        }
        return c;
    }

    double kl (List<String> r) throws Exception {
        double kl = 0;
        double i=1;
        for (String f1: r) {            
            for (String f2: r) {
                if (f1.equals(f2)) continue;
                double[] ts1 = ts.get(f1);
                double[] ts2 = ts.get(f2);
                double kl1 = rutil.kl(ts1, ts2);
                double kl2 = rutil.kl(ts2, ts1);
                
                kl+= Math.min(kl1, kl2);
//                kl = Math.max(kl, Math.max(kl1, kl2));
                i++;
            }
        }
        return kl/i;
    }

        
    double mi (List<String> r) {
        double mi = 0;
        double i=1;
        for (String f1: r) {
            for (String f2: r) {
                if (f1.equals(f2)) continue;
                double d1 = mi(f1, f2);
                //mi = Math.min(mi, d1);
                mi += d1;
                i++;
            }
        }
        return mi/i;
    }
    
    double mi(String f1, String f2) {
        double mi = 0;       
        Map<String, Double> miTerms = miMap.get(f1);
        try {
            if (miTerms.containsKey(f2)) {
                mi = miTerms.get(f2);
            }
        } catch (Exception e) {
            
        }
        return mi;
    }

    
    double s (List<String> r) {
        double s = 0;
        for (String f: r) {
            s += acfMap.get(f);
        }
        return s;
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
        
        // Normalize
        for (String term1: miTerms.keySet()) {
            Map<String, Double> values = miTerms.get(term1);
            double sum = 0;
            for (String term2: values.keySet())
                sum += values.get(term2);
            
            Map<String, Double> norm = new HashMap<String, Double>();
            for (String term2: values.keySet()) {
                norm.put(term2, values.get(term2)/sum);
            }
            miTerms.put(term1, norm);
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
