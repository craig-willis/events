package edu.gslis.trec.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Map;
import java.util.TreeMap;

public class Qrels 
{
   String topic;
    Map<String, Integer> docs = new TreeMap<String, Integer>();
    
    public Qrels(String topic) {
        this.topic = topic;
    }
    
    public Map<String, Integer> getDocs() {
        return docs;
    }
    public void addDoc(String docno, int relLevel) {
        docs.put(docno, relLevel);        
    }
        
    public static Map<String, Qrels> readQrels(String path) throws Exception {
        Map<String, Qrels> qrelsMap = new TreeMap<String, Qrels>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;
        while ((line = br.readLine()) != null) {
            String[] fields = line.split(" ");
            String topic = fields[0];
            String docno = fields[2];
            int relLevel = Integer.parseInt(fields[3]);
            Qrels qrels = qrelsMap.get(topic);
            if (qrels == null)
                qrels = new Qrels(topic);
            qrels.addDoc(docno, relLevel);
            qrelsMap.put(topic, qrels);
        }
        br.close();
        return qrelsMap;
    }    
}
