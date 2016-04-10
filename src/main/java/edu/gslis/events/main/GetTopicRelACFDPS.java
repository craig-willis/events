package edu.gslis.events.main;

import java.util.Iterator;

import java.util.Map;

import edu.gslis.docscoring.support.CollectionStats;
import edu.gslis.docscoring.support.IndexBackedCollectionStats;
import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;
import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQueriesJsonImpl;
import edu.gslis.queries.GQuery;
import edu.gslis.searchhits.SearchHit;
import edu.gslis.searchhits.SearchHits;
import edu.gslis.temporal.scorers.TemporalScorer;
import edu.gslis.temporal.util.RUtil;
import edu.gslis.trec.util.Qrels;

public class GetTopicRelACFDPS 
{
    public static void main(String[] args) throws Exception {
        String indexPath = args[0];        
        String queryFilePath = args[1];
        String qrelsPath = args[2];
        
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);
        index.setTimeFieldName(Indexer.FIELD_EPOCH);
        CollectionStats colStats = new IndexBackedCollectionStats();
        colStats.setStatSource(indexPath);
        
        Map<String, Qrels> qrelsMap = Qrels.readQrels(qrelsPath);
        
        RUtil rutil = new RUtil();
        
        GQueries queries = null;
        if (queryFilePath.endsWith("indri")) 
            queries = new GQueriesIndriImpl();
        else
            queries = new GQueriesJsonImpl();

        queries.setMetadataField("timestamp");
        queries.read(queryFilePath);
 
        Iterator<GQuery> queryIterator = queries.iterator();
        while(queryIterator.hasNext()) 
        {                            
            GQuery query = queryIterator.next();
            Qrels qrels = qrelsMap.get(query.getTitle());
            
            SearchHits hits = new SearchHits();

            if (qrels != null)
            {
                Map<String, Integer> docs = qrels.getDocs();
                for (String docno: docs.keySet()) {
                    if (docs.get(docno) > 0) {
                        SearchHit hit = index.getSearchHit(docno, null);
    //                    String epoch = String.valueOf((((Double)hit.getMetadataValue(Indexer.FIELD_EPOCH)).longValue()));
    //                    System.out.println(query.getTitle() + ">>" + docno + "," + docs.get(docno) + "," + hit + "," + hit.getDocID());
                        if (hit != null && hit.getDocID() != -1) {
                            hits.add(hit);
                        }
                    }
                }
            }
            
            System.out.println("HITS=" + hits.size());
            
            double[] times= TemporalScorer.getTimes(hits);
//            System.out.println("d <- c(");
//            for (double t: times) {
//                System.out.println(t + ",");
//            }
//            System.out.println(")");
            if (hits.size() > 1) {
                double[] acf = rutil.histacf(times, 2);
                double dps = rutil.histdps(times);
    
                System.out.println(query.getTitle() +"," + acf[0] + "," + dps);
            }
            else {
                System.out.println(query.getTitle() +",0,0");                
            }
        }
    }
}
