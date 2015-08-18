package edu.gslis.trec.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.lemurproject.kstem.KrovetzStemmer;
import org.lemurproject.kstem.Stemmer;

import edu.gslis.utils.Stopper;

/**
 * Convert from simple TREC Text to Mallet import format with optional stemming (Krovetz) and stopping)
 * @author cwillis
 *
 */
public class TrecToMallet 
{
    // Convert TREC text to Mallet format
    // Optionally stop/stem
    
    public static void main(String[] args) throws ParseException, IOException {
        
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( TrecToMallet.class.getCanonicalName(), options );
            return;
        }
        
        String inputPath = cl.getOptionValue("input");
        String outputPath = cl.getOptionValue("output");
        long interval = Long.valueOf(cl.getOptionValue("interval", "0"));
        long startDate = Long.valueOf(cl.getOptionValue("startDate", "0"));
        boolean stem = cl.hasOption("stem");

        Stopper stopper = new Stopper();
        if (cl.hasOption("stoplist")) {
            String stopPath = cl.getOptionValue("stoplist");
            stopper = new Stopper(stopPath);
        }
        File inputFile = new File(inputPath);
        if (interval > 0) {
            process(inputFile, new File(outputPath), stem, stopper, startDate, interval);
        } else {            
            FileWriter outputWriter = new FileWriter(outputPath);
            process(inputFile, outputWriter, stem, stopper);
            outputWriter.close();
        }
    }
    
    static Pattern epochPattern = Pattern.compile("[^<]*<EPOCH>([^<]*)</EPOCH>");
    static Pattern docnoPattern = Pattern.compile("[^<]*<DOCNO>([^<]*)</DOCNO>");
    static Pattern tagPattern = Pattern.compile("<[^>]*>");
    
    /**
     * Binned LDA. Create one mallet file per temporal bin
     * @throws IOException
     */
    public static void process(File inputFile, File outputDir, boolean stem, Stopper stopper,
            long startDate, long interval) 
            throws IOException {
       if (inputFile.isDirectory()) {
           File[] files = inputFile.listFiles();
           for (File file: files)
               process(file, outputDir, stem, stopper, startDate, interval);
       } else {
           
           BufferedReader br = new BufferedReader(new FileReader(inputFile));
           String line;
           String text = "";
           String docno = "";
           long epoch = 0;
           int bin = 0;
           while ((line = br.readLine()) != null) {
               Matcher m = docnoPattern.matcher(line);
               Matcher mepoch = epochPattern.matcher(line);
               if (m.matches()) {
                  docno = m.group(1);
                  docno= docno.replaceAll(" ", "");
               }
               else if (mepoch.matches()) {
                   epoch = Long.parseLong(mepoch.group(1));
                   bin = (int) ((epoch - startDate)/interval);
               }
               else if (line.contains("</DOC>")) {
                   
                   text = text.toLowerCase();
                   text = text.replaceAll("<[^>]*>", "");
                   text = text.replaceAll("[^a-zA-Z0-9 ]", " ");
                   text = stopper.apply(text);
                   String[] tokens = text.split("\\s+");
                   Stemmer stemmer = new KrovetzStemmer(); 

                   String stemmed = "";
                   for (String token: tokens) {
                       String s = stemmer.stem(token);
                       stemmed += " " + s;
                   }
                   
                   if (bin >= 0) {
                       System.out.println(docno + "," + bin);
                       FileWriter output = new FileWriter(outputDir + File.separator + bin + ".mallet", true);
                       output.write(docno + "," + stemmed + "\n");
                       output.close();
                   }
                   text = "";
                   docno = "";

               }
               else 
                  text += " " + line;
           }
           br.close();
       }           
    }
    
    public static void process(File inputFile, FileWriter output, boolean stem, Stopper stopper) 
            throws IOException {
       if (inputFile.isDirectory()) {
           File[] files = inputFile.listFiles();
           for (File file: files)
               process(file, output, stem, stopper);
       } else {
           
           BufferedReader br = new BufferedReader(new FileReader(inputFile));
           String line;
           String text = "";
           String docno = "";
           while ((line = br.readLine()) != null) {
               Matcher m = docnoPattern.matcher(line);
               if (m.matches()) {
                  docno = m.group(1);
                  docno= docno.replaceAll(" ", "");
               }
               else if (line.contains("</DOC>")) {
                   
                   text = text.toLowerCase();
                   text = text.replaceAll("<[^>]*>", "");
                   text = text.replaceAll("[^a-zA-Z0-9 ]", " ");
                   text = stopper.apply(text);
                   String[] tokens = text.split("\\s+");
                   Stemmer stemmer = new KrovetzStemmer(); 

                   String stemmed = "";
                   for (String token: tokens) {
                       String s = stemmer.stem(token);
                       stemmed += " " + s;
                   }
                   
                   output.write(docno + "," + stemmed + "\n");
                   
                   text = "";
                   docno = "";

               }
               else 
                  text += " " + line;
           }
           br.close();
           output.flush();
           

           
       }
           
    }
    
    
    /*
    my $docno;
    my $text;
    while (<>) {
      chomp();
      if ($_ =~ /<DOCNO>/) {
          $docno = $_;
          $docno =~ s/<[^>]*>//g;
          $docno =~ s/ //g;
      }
      elsif ($_ !~ /^</) {
          $tmp = lc($_);
          $tmp =~ s/[[:punct:]]/ /g;
          $text .= " " . $tmp;
      }
      if ($_ =~ /<\/DOC>/) {
          $text =~ s/ +/ /g;
          print "$docno,$docno,$text\n";
          $docno = "";
          $text = "";
      }
    }
    */
    
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path directory containing TREC text");
        options.addOption("output", true, "Path to Mallet output file or directory for binned models");
        options.addOption("stoplist", true, "Path to stop list");
        options.addOption("stem", false, "Stem (Krovetz");
        options.addOption("startDate", true, "Start date for collection for binned LDA");
        options.addOption("interval", true, "Interval for binned LDA. Zero for no binning");

        return options;
    }

    
}
