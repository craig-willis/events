package edu.gslis.temporal.util;

import java.util.List;

import org.rosuda.REngine.REXP;
import org.rosuda.REngine.Rserve.RConnection;


public class RUtil {
	

	private RConnection c;

	public RUtil() {
		
		try {
			c = new RConnection();
			

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
   public RUtil(int port) {
        
        try {
            c = new RConnection("localhost", port);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
   
   
   
   public double[][] mixture(double[] x) throws Exception {
       c.assign("x", x);
       c.voidEval("library(mixtools)");
       c.voidEval("nm <- normalmixEM(x)");
       double[] lambda = c.eval("nm$lambda").asDoubles();
       double[] mu = c.eval("nm$mu").asDoubles();
       double[] sigma = c.eval("nm$sigma").asDoubles();
       
       double[][] vals = new double[3][lambda.length];
       
       vals[0] = lambda;
       vals[1] = mu;
       vals[2] = sigma;
       
       return vals;
   }
   public double kl2(double[] x, double[] y) throws Exception {
       
       double kl = 0;
       double xsum = sum(x);
       double ysum = sum(y);
       for (int i=0; i<x.length; i++) {
           double xpr = (x[i] + 0.01)/xsum;
           double ypr = (y[i] + 0.01)/ysum;
           
           kl +=  xpr * Math.log( xpr / ypr);
       }
       return kl;

   }
   
   public double dp(double[] x) throws Exception {
       c.assign("x", x);
       c.voidEval("library(TSA)");
       c.voidEval("p <- periodogram(x, plot=F)");
       return c.eval("length(x)/which(p$spec == max(p$spec))").asDouble();       
   }
   
   public double dps(double[] x) throws Exception {
       c.assign("x", x);
       c.voidEval("library(TSA)");
       c.voidEval("p <- periodogram(x, plot=F)");
       return c.eval("max(p$spec)").asDouble();      
   }

   public double histdps(double[] x) throws Exception {
       c.assign("x", x);
       c.voidEval("library(TSA)");
       c.voidEval("p <- periodogram(hist(x, breaks=100, plot=F)$count, plot=F)");
       return c.eval("max(p$spec)").asDouble();      
   }

   public double maxSpec2(double[] x) throws Exception {
       c.assign("x", x);
       return c.eval("max(abs(2*fft(x)/length(x))^2)").asDouble();
   }
   public double maxSpec(double[] x) throws Exception {
       c.assign("x", x);
       c.voidEval("s <- spec.pgram(x, plot=F, detrend=T)");
       return c.eval("max(s$spec)").asDouble();
   }
   public double sum(double[] x) {
       double sum = 0;
       for (int i=0; i<x.length; i++) 
           sum+= x[i];
       return sum;
   }
   public double kl(double[] x, double[] y) throws Exception{
       c.voidEval("library(entropy)");
       c.assign("x", x);
       c.assign("y", y);
       
       return c.eval("KL.empirical(x+1, y+1, unit=\"log2\")").asDouble();
   }
   
   public double dist(double[] x, double[] y) throws Exception{
       c.assign("x", x);
       c.assign("y", y);
       return c.eval("x %*% y / sqrt(x%*%x * y%*%y)").asDouble();
   }
   
   public int[] cps(double[] x) throws Exception {
       c.voidEval("library(changepoint)");
       c.assign("x", x);
       c.voidEval("m <- cpt.mean(x, method=\"BinSeg\")");
       c.voidEval("cp <- cpts(m)");
       return c.eval("cp").asIntegers();
   }
   public int[] changepoints(double[] x) throws Exception {
       c.voidEval("library(changepoint)");
       c.assign("x", x);
       c.voidEval("m <- cpt.mean(x, method=\"BinSeg\")");
       c.voidEval("cp <- cpts(m)");
       int max = 0, start =0, end = 0;
       try{
           int len = c.eval("length(cp)").asInteger();
           if (len > 0) {
               max = c.eval("which(x == max(x[cp]))").asInteger();
               start = c.eval("min(cp)").asInteger();
               end = c.eval("max(cp)").asInteger();
           }
       } catch (Exception e) {
           e.printStackTrace();
       }
       return new int[] {start, end, max};
   }
   public void hist(String name, double[] x, String path) throws Exception {

       c.voidEval("setwd(\"" + path + "\")");

       c.assign("x", x);
       c.voidEval("png(\"" + name + "-hist.png" + "\")");
       c.voidEval("hist(x, breaks=1000)");       

       c.eval("dev.off()");
       
   }	
   public void density(String name, double[] x, String path) throws Exception {

       c.voidEval("setwd(\"" + path + "\")");

       c.assign("x", x);
       c.voidEval("png(\"" + name + "-density.png" + "\")");
       c.voidEval("plot(density(x))");       

       c.eval("dev.off()");
       
   }
   
   public void plotcp(String name, double[] x, String path, int start, int end, int interval) throws Exception {

       try {
           c.voidEval("setwd(\"" + path + "\")");
    
           c.assign("x", x);
           c.assign("start", new int[] {start});
           c.assign("end", new int[] {end});
           c.assign("interval", new int[] {interval});
           c.voidEval("png(\"" + name + "-cp.png" + "\")");
           
           c.voidEval("library(changepoint)");
           c.voidEval("m <- cpt.mean(x, method=\"BinSeg\")");
           c.voidEval("plot(m, xaxt=\"n\", xlab=\"time\", ylab=\"freq\", main=\"" + name + "\")");
           c.voidEval("bins <- seq(from = start, to = end, by = interval)");
           c.voidEval("dates <- format(as.Date(as.POSIXct(bins, origin = \"1970-01-01\"), tz = \"GMT\"), \"%m/%d/%y\")");
           c.voidEval("axis(1, at=1:length(bins), labels=dates, tick=FALSE)");
    
           c.eval("dev.off()");
       } catch (Exception e) {
           e.printStackTrace();
       }
       
       
   }
   
   public void plot(String name, double[] x, String path) throws Exception {

       c.voidEval("setwd(\"" + path + "\")");

       c.assign("x", x);
       c.voidEval("png(\"" + name + ".png" + "\")");
       c.voidEval("par(mfrow=c(2,1))");
       c.voidEval("plot(x, type=\"l\", main=\"" + name +  "\")");
       
       c.voidEval("ts <- ts(x, freq=2)");
       c.voidEval("decomp.ts <- decompose(ts)");
       c.voidEval("plot(decomp.ts$trend, main=\"Time series trend\")");


       c.eval("dev.off()");
       
   }
   
   
   public void plot(String name, double[] x, double[] y, String path) throws Exception {

       c.voidEval("setwd(\"" + path + "\")");

       c.assign("x", x);
       c.assign("y", y);
       c.voidEval("png(\"" + name + ".png" + "\")");
       c.voidEval("plot(y ~ x, main=\"" + name +  "\")");
       c.eval("dev.off()");
       
   }
   public double[] spec(double[] x, int freq) throws Exception{
       c.voidEval("library(multitaper)");
       c.assign("x", x);
       c.voidEval("ts <- ts(x, freq=" + freq + ")");
       c.voidEval("resSpec <- spec.mtm(ts, k=" + freq + ", nFFT=\"default\", Ftest=TRUE, jackknife=FALSE, plot = FALSE)");
       double s = c.eval("max(resSpec$spec)").asDouble();
       double f = c.eval("resSpec$freq[which(resSpec$spec == max(resSpec$spec))]").asDouble();
       return new double[] {s, f};
   }
   
   public double[] maxima(int[] x, double[] y) throws Exception {
       
       c.assign("x", x);
       c.assign("weights", y);
       c.voidEval("weights = weights / sum(weights)");
       
       c.voidEval("dens <- density(x, weights=weights, bw=\"sj\")");
       c.voidEval("second_deriv <- diff(sign(diff(dens$y)))");
       c.voidEval("max <- which(second_deriv == -2) + 1");       
       c.voidEval("mp <- dens$x[min] / (max(dens$x) - min(dens$x))");
       return c.eval("dens$y[max]/sum(dens$y[max])").asDoubles();       
   }
   
   
   public int[] minima(int[] x, double[] y, String query) throws Exception {
       
       c.assign("x", x);
//       c.assign("y", y);
       c.assign("weights", y);
       voidEval("weights = weights / sum(weights)");
       
//       c.voidEval("y <- as.vector(y)");
//       c.voidEval("dens <- density(y, bw=\"sj\")");
       voidEval("dens <- density(x, weights=weights, bw=\"sj\")");
       voidEval("second_deriv <- diff(sign(diff(dens$y)))");
       voidEval("min <- which(second_deriv == 2) + 1");
       
       //c.voidEval("max <- which(second_deriv == -2) + 1");
       voidEval("mp <- dens$x[min] / (max(dens$x) - min(dens$x))");
       
       // Plot
       /*
       voidEval("setwd(\"/tmp/minima/\")");
       voidEval("png(\"" + query + ".png\")");
       voidEval("plot(dens, main=\"" + query + "\")");
       voidEval("points(dens$x[min],dens$y[min],col=\"red\")");
       voidEval("dev.off()");
       */
       
       return c.eval("x[length(x)*mp]").asIntegers();
   }
   
   
   private void voidEval(String cmd) throws Exception {
       c.assign(".tmp.", cmd);
       REXP r = c.parseAndEval("try( eval (parse (text=.tmp.)),silent=TRUE)");
       if (r.inherits("try-error")) 
           System.err.println("Error: "+ r.asString());
   }
   
   
	public double[] acf(double[] data) throws Exception { 
	    return acf(data, 2);
	}
	
	public double silvermantest(double[] data, int modes) throws Exception {
	    c.voidEval("library(silvermantest)");
        c.assign("x", data);
        return c.eval("silverman.test(x, " + modes + ")@p_value").asDouble();	    	
	}
	
	public double ccf(double[] x, double[]y, int lag) throws Exception  {
        c.assign("x", x);
        c.assign("y", y);
        c.voidEval("cc <- ccf(x, y, plot=F)");

        return c.eval("cc$acf[which(cc$lag == " + lag + ")]").asDouble();	    
	}
	
	public double[] acf(double[] data, int lag) throws Exception {
        c.assign("x", data);
        c.voidEval("ac <- acf(x, plot=F)");
        c.voidEval("sig <- qnorm((1 + 0.9999)/2)/sqrt(sum(!is.na(x)))");

        return new double[] { c.eval("ac$acf[" + lag + "]").asDouble(),  c.eval("sig").asDouble() };
	}
	
	public double[] histacf(double[] data, int lag) throws Exception {
        c.assign("x", data);
        c.voidEval("ac <- acf(hist(x, breaks=100, plot=F)$counts, plot=F)");
        c.voidEval("sig <- qnorm((1 + 0.9999)/2)/sqrt(sum(!is.na(x)))");

        return new double[] { c.eval("ac$acf[" + lag + "]").asDouble(),  c.eval("sig").asDouble() };	    
	}

	public double kurtosis(double[] data) throws Exception {
        c.assign("x", data);
        c.voidEval("library(moments)");

        c.voidEval("k <- kurtosis(x)");

        return c.eval("k").asDouble();	    
	}

   public double skewness(double[] data) throws Exception {
        c.assign("x", data);
        c.voidEval("library(moments)");

        c.voidEval("s <- skewness(x)");

        return c.eval("s").asDouble();      
    }

   
	public void close() {
		try {
			c.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public double[][] getBursts(double[] data, int k, int method) throws Exception {
	    	    
        c.assign("y", data);
        c.voidEval("y <- y*100");
        c.voidEval("x <- rep(1:length(y), y)");
        c.voidEval("library(mixtools)");
 
        double[] mus = new double[k];
        double[] sigmas = new double[k];
        if (k > 1) {
            try {
                c.voidEval("nmix <- normalmixEM(x, ECM=T, k=" + k + ")");
                
                //c.voidEval("lambda <- which(nmix$lambda == max(nmix$lambda))");
                //c.voidEval("mu <- nmix$mu[lambda]");
                //c.voidEval("sigma <- nmix$sigma[lambda]");

                mus = c.eval("nmix$mu").asDoubles();
                sigmas = c.eval("nmix$sigma").asDoubles();

            } catch (Exception e) {
                mus = c.eval("mean(x)").asDoubles();
                sigmas = c.eval("sd(x)").asDoubles();
                //c.voidEval("mu <- mean(x)");
                //c.voidEval("sigma <- sd(x)");                            
            }
        }
        else {

            if (method == 1) {
                
                c.voidEval("burst <- which(y >= mean(y) + 2*sd(y))");
                c.voidEval("mu <- mean(burst)");
                c.voidEval("sigma <- sd(burst)");
                mus = c.eval("mu").asDoubles();
                sigmas = c.eval("sigma").asDoubles();

            } else if (method == 2) {

                try
                {
                    c.voidEval("nmix <- normalmixEM(x, ECM=T, k=" + 2 + ")");
                    
                    c.voidEval("dens <- dnorm(nmix$mu, nmix$mu, nmix$sigma)");
                    c.voidEval("lambda <- which(dens == max(dens))");
                    c.voidEval("mu <- nmix$mu[lambda]");
                    c.voidEval("sigma <- nmix$sigma[lambda]");
        
                    mus = c.eval("mu").asDoubles();
                    sigmas = c.eval("sigma").asDoubles();
                } catch (Exception e) {
                    mus = c.eval("mean(x)").asDoubles();
                    sigmas = c.eval("sd(x)").asDoubles();
                    //c.voidEval("mu <- mean(x)");
                    //c.voidEval("sigma <- sd(x)");                            
                }    
            } else {

                mus = c.eval("mean(x)").asDoubles();
                sigmas = c.eval("sd(x)").asDoubles();
            }
        }
        
        return new double[][] { mus, sigmas};
        
        //return new double[] { c.eval("mu").asDouble(), c.eval("sigma").asDouble() }; 
	}
	
	public double[] getConstraints(double[] data) throws Exception {
        c.assign("x", data);    
        c.voidEval("library(mixtools)");
        c.voidEval("nmix <- normalmixEM(x, k=2)");
        c.voidEval("mu <- nmix$mu[which(nmix$sigma == min(nmix$sigma))]");
        c.voidEval("sigma <- nmix$sigma[which(nmix$sigma == max(nmix$sigma))]");
        c.voidEval("start <- qnorm(0.025, mean=mu, sd=sigma)"); 
        c.voidEval("end <- qnorm(0.975, mean=mu, sd=sigma)"); 
        return new double[] { c.eval("start").asDouble(), c.eval("mu").asDouble(), c.eval("end").asDouble(), c.eval("sigma").asDouble() };        
	}
}
