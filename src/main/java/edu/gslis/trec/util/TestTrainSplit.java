package edu.gslis.trec.util;

import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import edu.gslis.queries.GQueries;
import edu.gslis.queries.GQueriesIndriImpl;
import edu.gslis.queries.GQuery;

public class TestTrainSplit 
{

    public static void main(String[] args) throws Exception {
        
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( TestTrainSplit.class.getCanonicalName(), options );
            return;
        }
        
        String topicsPath = cl.getOptionValue("topics");
        String qrelsPath = cl.getOptionValue("qrels");
        
        GQueries queries = new GQueriesIndriImpl();
        
        queries.read(topicsPath);
        Map<String, Qrels> qrelsMap = Qrels.readQrels(qrelsPath);
        
        FileWriter qrelsTest = new FileWriter(qrelsPath + ".test");
        FileWriter qrelsTrain = new FileWriter(qrelsPath + ".train");
        FileWriter topicsTest = new FileWriter(topicsPath.replace(".indri", ".test.indri"));
        FileWriter topicsTrain = new FileWriter(topicsPath.replace(".indri", ".train.indri"));
        
        topicsTest.write("<parameters>\n");
        topicsTrain.write("<parameters>\n");

        Iterator<GQuery> it = queries.iterator();
        int i=1;
        while (it.hasNext()) {
            GQuery query = it.next();
            Qrels qrels = qrelsMap.get(query.getTitle());
            
            if (i%2 == 0) {
                writeTopics(topicsTrain, query);
                writeQrels(qrelsTrain, qrels);                
            } else {
                writeTopics(topicsTest, query);
                writeQrels(qrelsTest, qrels);
            }
            i++;
        }
        
        topicsTest.write("</parameters>\n");
        topicsTrain.write("</parameters>\n");

        qrelsTest.close();
        qrelsTrain.close();
        topicsTest.close();
        topicsTrain.close();
    }
 
    
    public static void writeTopics(FileWriter topicWriter, GQuery query)
            throws Exception
    {
        topicWriter.write("<query>\n");
        topicWriter.write("<number>" + query.getTitle() + "</number>\n");
        topicWriter.write("<text>" + query.getText() + "</text>\n");
        topicWriter.write("</query>\n");        
    }
    
    public static void writeQrels(FileWriter qrelsWriter, Qrels qrels) throws Exception
    {
        for (String docno: qrels.docs.keySet()) {
            int level = qrels.docs.get(docno);
            
            qrelsWriter.write(qrels.topic + " 0 " + docno + " " + level + "\n");
        }
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("topics", true, "Path to topics file");
        options.addOption("qrels", true, "Path to qrels");

        return options;
    }

    
}
