package edu.gslis.trec.util;

import java.io.FileWriter;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.lemurproject.kstem.KrovetzStemmer;
import org.lemurproject.kstem.Stemmer;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import edu.gslis.utils.Stopper;

public class ReutersToTrecText {
    
    static Stemmer stemmer = new KrovetzStemmer(); 
    
    public static void main(String[] args) throws Exception 
    {
        
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( ReutersToTrecText.class.getCanonicalName(), options );
            return;
        }
        String inputPath = cl.getOptionValue("input");
        String outputPath = cl.getOptionValue("output");
        boolean stem = Boolean.parseBoolean(cl.getOptionValue("stem", "true"));
        Stopper stopper = new Stopper();
        if (cl.hasOption("stopper")) {
            String stopperPath = cl.getOptionValue("stopper");
            stopper = new Stopper(stopperPath);
        }
        
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        ZipFile zipFile = new ZipFile(inputPath);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();                    
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        FileWriter outputWriter = new FileWriter(outputPath);

        while(entries.hasMoreElements())
        {
            try
            {
                ZipEntry entry = entries.nextElement();
                InputStream is = zipFile.getInputStream(entry);
                Document root = builder.parse(is);
                
                NodeList codes = root.getElementsByTagName("code");
                boolean output = false;
                if (codes != null) 
                {
                    for (int j=0; j < codes.getLength(); j++) {
                        Node codeNode = codes.item(j);
                        NamedNodeMap codeAttrs = codeNode.getAttributes();
                        String code = codeAttrs.getNamedItem("code").getTextContent();
                        System.out.println(code);
                        // CCAT MCAT ECAT
                        if (code.equals("GCAT")) {
                            output = true;
                        }
                                        
                    }
                }
                NodeList documents = root.getElementsByTagName("newsitem");
                if (documents != null && output) 
                {
                    for (int i=0; i < documents.getLength(); i++) {
                        outputWriter.write("<DOC>\n");
                        Node doc = documents.item(i);
                        
                        NamedNodeMap attrs = doc.getAttributes();
                        String itemid = attrs.getNamedItem("itemid").getTextContent();
                        outputWriter.write("<DOCNO>" + itemid + "</DOCNO>\n");
                        String date = attrs.getNamedItem("date").getTextContent();
                        long epoch = df.parse(date).getTime()/1000;
                        outputWriter.write("<EPOCH>" + epoch + "</EPOCH>\n");

                        
                        NodeList elements = doc.getChildNodes();
                        if (elements != null && elements.getLength() > 0) 
                        {
                            for (int j = 0; j < elements.getLength(); j++) 
                            {
                                Node node = elements.item(j);
                                if (node != null) {
                                    String tag = node.getNodeName();
                                    if (!tag.equals("#text")) {
                                        
                                        String content = node.getTextContent();
                                        if (tag.equals("headline"))
                                            outputWriter.write("<TITLE>" + clean(content, stopper, stem) + "</TITLE>\n");
                                        else if (tag.equals("text"))
                                            outputWriter.write("<TEXT>\n" + clean(content, stopper, stem) + "\n</TEXT>\n");
                                    }
                                }
                            }
                        }
                        outputWriter.write("</DOC>\n");          
                    }
                }
/*    
                <newsitem itemid="3328" id="root" date="1996-08-20" xml:lang="en">^M
                <title>MEXICO: Mexico to stand by steel import tariffs - ministry.</title>^M
                <headline>Mexico to stand by steel import tariffs - ministry.</headline>^M
                <dateline>MEXICO CITY 1996-08-20</dateline>^M
                <text>
                        <p>Mexico's Trade Ministry said Tuesday the government had decided to stand by a 1993 decision to impose compensatory import tariffs on hot and cold rolled steel plate imports.</p>
                        <p>The ministry said requests to revise the tariffs did not succeed. &quot;In both cases, the ministry considered that the companies that asked for the revisions did not supply sufficient proof...that allowed for a change in the imposed tariffs,&quot; it said.</p>
                        <p>The ministry confirmed that the current tariffs, first set April 28, 1993 after dumping allegations against U.S. companies, remained in place. They are 28.2-28.7 percent for hot-rolled and 12.8 percent for cold rolled.</p>
                        <p>--Chris Aspin, Mexico City newsroom (525) 7289530.</p>
                </text>
                </newsitem>
*/
                /*
                String docno = entryName.substring(entryName.lastIndexOf("/")+1, entryName.indexOf("."));
                String trecText = "<DOC>\n";
                trecText += "<DOCNO>" + docno + "</DOCNO>\n";
                trecText += "<EPOCH>" + date.getTime()/1000 + "</EPOCH>\n";
                trecText += "<TITLE>" + headline + "</TITLE>\n";
                trecText += "<TEXT>" + body + "\n</TEXT>\n";
                trecText += "</DOC>\n";
                outputWriter.write(trecText);
                */
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        zipFile.close();        
        outputWriter.close();

    }
    
    public static String clean(String text, Stopper stopper, boolean stem) {
        if (text == null) 
            return "";
        text = text.replaceAll("[^a-zA-Z0-9 ]", " ");
        text = text.toLowerCase();
        String[] tokens = text.split("\\s+");
        String stemmed = "";
        for (String token: tokens) {
            String s = token;
            if (stem)
                s = stemmer.stem(token);
            if (!stopper.isStopWord(token))
                stemmed += " " + s;
        }
        return stemmed.trim();
    }
    
    public static String stop(String text, Stopper stopper) {
        if (text == null) 
            return "";
        text = text.replaceAll("[^a-zA-Z0-9 ]", " ");
        text = text.toLowerCase();
        String[] tokens = text.split("\\s+");
        String stopped = "";
        for (String token: tokens) {
            if (!stopper.isStopWord(token))
                stopped += " " + token;
        }
        return stopped.trim();   
    }
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input zip file");
        options.addOption("output", true, "Path to output file");
        options.addOption("stem", true, "true/false");
        options.addOption("stopper", true, "If set, text is stopped");
        return options;
    }
}
