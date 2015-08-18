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

public class FindEvents 
{
    //static final int START_TIME = 571647000;
    //static final int END_TIME = 631152000;
    static final double ALPHA = 0.5;
    static final double MU = 2500;
    static final int NUM_TERMS = 10;
    static final int NUM_DOCS = 50;
   // static final int WEEK = 604800;

    static long colStart = 0;
    static long colEnd = 0;
    static long colInterval = 0;
    /**
     * 1. Read list of high ACF terms 
     * 2. Read list of high CCF term
     * 
     */
    public static void main(String[] args) throws Exception
    {
        String tsIndexPath = args[0];
        String indexPath = args[1];
        String acfTermsPath = args[2];
        String ccfTermsPath = args[3];
        String stopperPath = args[4];
        String miPath = args[5];
        String outputPath = args[6];
        colStart = Long.parseLong(args[7]);
        colEnd = Long.parseLong(args[8]);
        colInterval = Long.parseLong(args[9]);  
        File output = new File(outputPath);
        FileWriter outputHtml = new FileWriter(output + File.separator + "index.html");
        
        TimeSeriesIndex tsIndex = new TimeSeriesIndex();        
        tsIndex.open(tsIndexPath, true);
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        Stopper stopper = new Stopper(stopperPath);
        CollectionStats colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);

        RUtil rutil = new RUtil();
        Map<String, Double> acfMap = readTermAcf(acfTermsPath);
        Map<String, Map<String, Double>> ccfMap = readTermCcf(ccfTermsPath);
        Map<String, Map<String, Double>> miMap = readTermMi(miPath);
        
        // Find all terms with high CCF
        Map<String, FeatureVector> acfFv = new HashMap<String, FeatureVector>();
        List<String> seen = new ArrayList<String>();
        for (String acfTerm: acfMap.keySet()) {
            
            if (seen.contains(acfTerm))
                continue;
            
            Map<String, Double> ccfTerms = ccfMap.get(acfTerm);
            Map<String, Double> miTerms = miMap.get(acfTerm);
            
            FeatureVector fv = acfFv.get(acfTerm);
            if (fv == null) {
                fv = new FeatureVector(stopper); 
                fv.addTerm(acfTerm, 1);
            }
            if (ccfTerms != null) {
                for (String term: ccfTerms.keySet()) {
                    double ccf = ccfTerms.get(term);
                    try {
                        if (miTerms.containsKey(term)) {
                            fv.addTerm(term, ccf);  
                            seen.add(term);
                        }
                    } catch (Exception e) {
                        
                    }
                }
            }
            acfFv.put(acfTerm, fv);
        }
        
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        
        // Read ACF terms
        for (String acfTerm: acfMap.keySet()) {
            
            if (seen.contains(acfTerm))
                continue;
            double acf = acfMap.get(acfTerm);
            double[] ts = tsIndex.get(acfTerm);
            double sum = 0;
            for (double t: ts) 
                sum+=t;
            

            outputHtml.write("<hr>\n Term: " + acfTerm  + " (acf=" + acf + ", freq=" + sum + ")<br>\n");

            Map<String, Double> ccfTerms = ccfMap.get(acfTerm);
            int i=0;
            outputHtml.write("Top terms by time series cross correlation:<br>\n");
            if (ccfTerms != null) {
                outputHtml.write("<pre>\n");

                for (String ccfTerm: ccfTerms.keySet()) {
                    if (i==10) break;
                    double ccf = ccfTerms.get(ccfTerm);
                    outputHtml.write(ccfTerm + " " + ccf + "\n");
                    i++;
                }
            }
            outputHtml.write("</pre>\n");
            i = 0;
            outputHtml.write("\n</pre>\n<br>Top terms by normalized PMI:<br>\n\n");
            Map<String, Double> miTerms = miMap.get(acfTerm);
            if (miTerms != null) 
            {
                outputHtml.write("<pre>\n");
                for (String miTerm: miTerms.keySet()) {
                    if (i==10) break;
                    
                    try {
                    double mi = miTerms.get(miTerm);
                    outputHtml.write(miTerm + " " + mi + "\n");
                    i++;
                    } catch (Exception e) {
                       }
                }
                outputHtml.write("</pre>\n");
            }
                


            FeatureVector fv = acfFv.get(acfTerm);
            outputHtml.write("<br>Combined feature vector:<br><pre>\n");
            outputHtml.write(fv + "</pre>\n");

            FeatureVector rm = buildRm(fv, stopper, index, colStats);
            outputHtml.write("<br>KDE relevance model:<br><pre>\n");
            outputHtml.write(rm + "</pre>\n");

            rutil.plotcp(acfTerm, ts, output.getAbsolutePath());
            outputHtml.write("<img src=\"" + acfTerm + "-cp.png\"><br>");
            
//            FeatureVector rm = buildRm(acfTerm, stopper, index, colStats);
//            for (String term: rm.getFeatures())
//                seen.add(term);
            
            int[] cps = rutil.changepoints(ts);
            long startTime = colStart + cps[0]*colInterval;
            long endTime = colStart + cps[1]*colInterval;
            long maxTime = colStart + cps[2]*colInterval;
            
            long area = 0;
            for (int j=cps[0]; j<cps[1]; j++) {
                area += ts[j];
            }
            String start = df.format(new Date(startTime*1000));
            String end = df.format(new Date(endTime*1000));
            String max = df.format(new Date(maxTime*1000));
            outputHtml.write("\t\t start=" + start + "<br>\n");
            outputHtml.write("\t\t end=" + end + "<br>\n");
            outputHtml.write("\t\t max=" + max + "<br>\n");
            outputHtml.write("ms=" + startTime + "," + endTime + "," + maxTime + "<br>\n");
            outputHtml.write(acfTerm + " area=" + area);
            outputHtml.flush();
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
