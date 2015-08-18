package edu.gslis.trec.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.lemurproject.kstem.KrovetzStemmer;
import org.lemurproject.kstem.Stemmer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import edu.gslis.indexes.IndexWrapper;
import edu.gslis.indexes.IndexWrapperFactory;
import edu.gslis.lucene.indexer.Indexer;

/**
 * Divide TREC text files and Qrels into 3 equal parts
 *
 */
public class SubdivideCollection 
{
    
    static Stemmer stemmer = new KrovetzStemmer(); 

    public static void main(String[] args) throws Exception {
        
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( SubdivideCollection.class.getCanonicalName(), options );
            return;
        }
        
        String inputPath = cl.getOptionValue("input");
        String outputPath = cl.getOptionValue("output");
        String qrelsPath = cl.getOptionValue("qrels");
        int parts =Integer.parseInt(cl.getOptionValue("parts"));
        long startTime = Long.parseLong(cl.getOptionValue("start"));
        long endTime = Long.parseLong(cl.getOptionValue("end"));
        long diff = (endTime - startTime)/parts;
        String indexPath = cl.getOptionValue("index");
        IndexWrapper index = IndexWrapperFactory.getIndexWrapper(indexPath);

        long[][] bounds = new long[parts][2];
        for (int x=0; x<parts; x++) {
            bounds[x][0] = startTime + x*diff;
            bounds[x][1] = bounds[x][0] + diff;
        }
                
        File inputFile = new File(inputPath);
        File outputFile = new File(outputPath);
        //process(inputFile, outputFile, outputPath, "", bounds);
        
        processQrels(qrelsPath, outputPath, bounds, index);
    }
    
    
    public static void processQrels(String qrelsPath, String outputPath, long[][] bounds, IndexWrapper index) 
        throws Exception
    {
        File qrelsFile = new File(qrelsPath);
        BufferedReader br = new BufferedReader(new FileReader(qrelsFile));
        String line;
        while ((line = br.readLine()) != null) {
            String[] fields = line.split("\\s+");
            String docno = fields[2];
            long epoch = Long.parseLong(index.getMetadataValue(docno, Indexer.FIELD_EPOCH));
            
            int part = -1;
            for (int k=0; k<bounds.length; k++) {
                long s = bounds[k][0];
                long e = bounds[k][1];
                if (epoch >= s && epoch < e) {
                    part = k;
                    break;
                }
            }
            FileWriter outputWriter = new FileWriter(outputPath + File.separator + 
                    qrelsFile.getName() + "-" + part, true);
            outputWriter.write(line + "\n");
            outputWriter.close();
        }
        br.close();
        
    }
    static Pattern INVALID_XML_CHARS = Pattern.compile("[^\\u0009\\u000A\\u000D\\u0020-\\uD7FF\\uE000-\\uFFFD\uD800\uDC00-\uDBFF\uDFFF]");
    
    
    public static void process(File inputFile, File outputFile, String basePath, String parentPath, long[][] bounds) 
            throws IOException {
       if (inputFile.isDirectory()) {
           File[] files = inputFile.listFiles();
           parentPath += File.separator + inputFile.getName();
           for (File file: files)
               process(file, outputFile, basePath, parentPath, bounds);
       } else {
           
                      
           DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

           try {

               //Using factory get an instance of document builder
               DocumentBuilder db = dbf.newDocumentBuilder();
               String trecText = FileUtils.readFileToString(inputFile, "UTF-8");
               trecText = INVALID_XML_CHARS.matcher(trecText).replaceAll("");
               
               trecText = trecText.replaceAll("&", "&amp;");
               trecText = trecText.replaceAll("<P>", "");
               trecText = trecText.replaceAll("</P>", "");
               trecText = "<ROOT>" + trecText + "</ROOT>";
               Document dom = db.parse(new InputSource(new StringReader(trecText)));
               
               NodeList documents = dom.getElementsByTagName("DOC");
               if (documents != null && documents.getLength() > 0) {                   

                   long part = -1;
                   for (int i = 0; i < documents.getLength(); i++) {                       
                       String newDoc = "<DOC>\n";
                       Node doc = documents.item(i);
                       NodeList elements = doc.getChildNodes();
                       if (elements != null && elements.getLength() > 0) {
                           for (int j = 0; j < elements.getLength(); j++) {

                               Node node = elements.item(j);
                               if (node != null) {
                                   String tag = node.getNodeName();
                                   if (!tag.equals("#text")) {
                                       if (node.getFirstChild() != null)
                                       {
                                           String content = node.getFirstChild().getNodeValue();
                                           if (tag.equals("EPOCH"))
                                           {
                                               // Find which part we're in
                                               long epoch = Long.parseLong(content);
                                               for (int k=0; k<bounds.length; k++) {
                                                   long s = bounds[k][0];
                                                   long e = bounds[k][1];
                                                   if (epoch >= s && epoch < e) {
                                                       part = k;
                                                       break;
                                                   }
                                               }
                                           }
                                           newDoc += "<" + tag + ">" + content + "</" + tag + ">\n";
                                       }
                                   }
                               }
                           }
                       }
                       newDoc += "</DOC>\n";
                       
                       if (part >= 0) {
                           File parentDir = new File(basePath + File.separator + part + File.separator + parentPath);
                           parentDir.mkdirs();
                           FileWriter outputWriter = new FileWriter(
                                   parentDir.getPath() + File.separator + inputFile.getName(), true);
                           outputWriter.write(newDoc);
                           outputWriter.close();
                       }
                   }
               }
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
           
    }
    
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path directory containing TREC text");
        options.addOption("output", true, "Path to output directory");
        options.addOption("start", true, "Start time");
        options.addOption("end", true, "End time");
        options.addOption("parts", true, "Number of parts to subdivide into");
        options.addOption("qrels", true, "Path to qrels");
        options.addOption("index", true, "Path to full index");

        return options;
    }

    
}
