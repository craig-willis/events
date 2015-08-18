package edu.gslis.trec.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.lemurproject.kstem.KrovetzStemmer;
import org.lemurproject.kstem.Stemmer;
import org.w3c.dom.Document;

import com.nytlabs.corpus.NYTCorpusDocument;
import com.nytlabs.corpus.NYTCorpusDocumentParser;

import edu.gslis.utils.Stopper;

public class NYTToTrecText {
    
    static Stemmer stemmer = new KrovetzStemmer(); 
    
    public static void main(String[] args) throws Exception 
    {
        
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( NYTToTrecText.class.getCanonicalName(), options );
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

        BufferedInputStream in = new BufferedInputStream(new FileInputStream(inputPath));
        GzipCompressorInputStream gzip = new GzipCompressorInputStream(in);
        TarArchiveInputStream tar = new TarArchiveInputStream(gzip);
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();                    
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        FileWriter output = new FileWriter(outputPath);

        NYTCorpusDocumentParser nytParser = new NYTCorpusDocumentParser();
        try 
        {
            TarArchiveEntry entry;
            while ((entry=tar.getNextTarEntry()) != null) 
            {
                if (tar.canReadEntryData(entry) && !entry.isDirectory())
                {
                    byte[] buffer = new byte[(int)entry.getSize()];
                    tar.read(buffer);
                    
                    String xml = new String(buffer);

                    xml = xml.replace("<!DOCTYPE nitf "
                            + "SYSTEM \"http://www.nitf.org/"
                            + "IPTC/NITF/3.3/specification/dtd/nitf-3-3.dtd\">", "");

                    Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));
                    String entryName = entry.getName();
                    NYTCorpusDocument nytdoc = nytParser.parseNYTCorpusDocumentFromDOMDocument(null, doc);
                    String body = nytdoc.getBody();
                    if (nytdoc.getLeadParagraph() != null)
                        body = body.substring(nytdoc.getLeadParagraph().length());
                    Date date = nytdoc.getPublicationDate();
                    String headline = nytdoc.getHeadline();
                    
                    if (stem) {
                        body = stem(body, stopper);
                        headline = stem(headline, stopper);
                    }

                    String docno = entryName.substring(entryName.lastIndexOf("/")+1, entryName.indexOf("."));
                    String trecText = "<DOC>\n";
                    trecText += "<DOCNO>" + docno + "</DOCNO>\n";
                    trecText += "<EPOCH>" + date.getTime()/1000 + "</EPOCH>\n";
                    trecText += "<TITLE>" + headline + "</TITLE>\n";
                    trecText += "<TEXT>" + body + "\n</TEXT>\n";
                    trecText += "</DOC>\n";
                    
                    output.write(trecText);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            tar.close();
            gzip.close();
        }
        output.close();
        
    }
    
    public static String stem(String text, Stopper stopper) {
        if (text == null) 
            return "";
        text = text.replaceAll("[^a-zA-Z0-9 ]", " ");
        text = text.toLowerCase();
        String[] tokens = text.split("\\s+");
        String stemmed = "";
        for (String token: tokens) {
            String s = stemmer.stem(token);
            if (!stopper.isStopWord(token))
                stemmed += " " + s;
        }
        return stemmed.trim();
    }
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input gz");
        options.addOption("output", true, "Path to output file");
        options.addOption("stem", true, "true/false");
        options.addOption("stopper", true, "If set, text is stopped");
        return options;
    }
}
