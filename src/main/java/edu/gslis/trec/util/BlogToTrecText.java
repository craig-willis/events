package edu.gslis.trec.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.knallgrau.utils.textcat.TextCategorizer;

public class BlogToTrecText {
    

    public static void main(String[] args) throws Exception 
    {
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine cl = parser.parse( options, args);
        if (cl.hasOption("help")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( BlogToTrecText.class.getCanonicalName(), options );
            return;
        }
        //DateFormat defaultDf = new SimpleDateFormat("yyyyMMdd");
        String inputPath = cl.getOptionValue("input");
        String outputPath = cl.getOptionValue("output");
        long startTime = Long.parseLong(cl.getOptionValue("startTime"));
        long endTime = Long.parseLong(cl.getOptionValue("endTime"));
        //Date defaultDate = defaultDf.parse(cl.getOptionValue("date"));
        TextCategorizer guesser = new TextCategorizer();
        FileWriter output = new FileWriter(outputPath);

        // 2005-11-16T09:47:45+0000
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        
        try {

            BufferedReader br = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(new FileInputStream(inputPath)), "UTF-8"));
            String line = "";
            
            int i=0;
            while ( (line = br.readLine()) != null) 
            {
                if (line.startsWith("<DOC>")) {
                    String doc = line + "\n";
                    String docno = "";
                    String language = "";
                    long epoch = 0;
                    while ( (line = br.readLine()) != null) {
                        line = line.replace("^\\s+", "");

                        /*
                        if (line.equals("</DOC>")) {
                            doc += line + "\n";
                                                        
                            // Only consider documents with a publication date
                            if (language.equals("english")  && (epoch >=     startTime && epoch <= endTime)) {
                                output.write(doc);
                            } else {
                                System.err.println("Skipping " + docno + ", language = " + language + ", epoch = " + epoch);
                            }
//                            System.out.println("Done with " + docno + " (" + i + ")");
                            i++;
                            break;
                        }
                        */
                        //<DATE_XML>2005-11-16T09:47:45+0000</DATE_XML>                        
                        if (line.startsWith("<DATE_XML>")) {
                            
                            doc += line + "\n";
                            String time = line.substring(10, line.lastIndexOf("<"));
                            if (!time.equals("")) {
                                try {
                                    epoch = df.parse(time).getTime()/1000;
                                } catch (Exception e) {
                                    System.err.println("Exception while parsing time for " + docno);
                                    System.err.println(line);
                                    e.printStackTrace();
                                }
                            }
                            doc += "<EPOCH>" + epoch + "</EPOCH>\n";
                        }
                        else if (line.startsWith("<DOCNO>")) {
                            doc += line + "\n";
                            docno = line.substring(7, line.lastIndexOf("<"));
                            //epoch = defaultDate.getTime()/1000;                                
                            //doc += "<EPOCH>" + epoch + "</EPOCH>\n";
                        }
                        else if (line.startsWith("<DOCHDR>")) {
                            // Everything from DOCTYPE to </DOC> is html
                            while (!br.readLine().startsWith("</DOCHDR>")) {
                            }

                            //else if (line.startsWith("<html")) {
                            String html = line;
                            int j=0;
                            while (( line = br.readLine()) != null) {
                                
                                if (j < 2000) 
                                    html += line + " CRLF";
                                
                                // Only look at the 1st 1000 lines...
                                //if (line.startsWith("</html>") || line.startsWith("</DOC>"))
                                if (line.startsWith("</DOC>"))
                                    break;
                                
                                j++;
                            }  
                            //System.err.println(docno + " lines=" + j);
                            Document hdoc = Jsoup.parse(html);
                            String title = hdoc.title();
                            String text = hdoc.text();
                            String[] lines = text.split("CRLF");
                            text = "";
                            for (String l: lines) {
                                int length = l.split(" ").length;
                                if (length > 15) {
                                    text += l + "\n";
                                }                                
                            }
                            language = guesser.categorize(text);
                            doc += "<TITLE>\n" + title + "</TITLE>\n";
                            doc += "<TEXT>\n" + text + "</TEXT>\n";
                            
                            doc += "</DOC>\n";
                            
                            // Only consider documents with a publication date
                            if (language.equals("english")  && (epoch >= startTime && epoch <= endTime)) {
                                output.write(doc);
                            } else {
                                System.err.println("Skipping " + docno + ", language = " + language + ", epoch = " + epoch);
                            }
                            i++;
                            break;
                        }
                        else {
                            if (!line.equals(""))
                                doc += line + "\n";
                        }
                        
                    }
                    
                } 

            }
            br.close();
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static Options createOptions()
    {
        Options options = new Options();
        options.addOption("input", true, "Path to input gz");
        options.addOption("output", true, "Path to output file");
        //options.addOption("date", true, "Default date");
        options.addOption("startTime", true, "Collection start date");
        options.addOption("endTime", true, "Collection end date");
        return options;
    }
}
