package edu.gslis.events.main;

public class Test {
    public static double foo2(String a) {
        double c = 0;
        String[] elems = a.split(",");
        for (String e: elems) 
            c += Double.parseDouble(e);
        return c;            
    }
    
    public static double foo(String a) 
    {
        String b = ""; 
        double c = 0;
        
        for(int i=0; i<a.length(); i++){ 
            if(a.charAt(i) == ','){
                c += Double.parseDouble(b);
                b = ""; 
            }
            else
            {
                b += a.charAt(i); 
            }
        }
        return c; 
   }
    
    public static void main(String [] args) {
        //System.out.println(foo2("1,2.5,3,4"));
        System.out.println(foo("1,2.5,3,4"));
        
        String a = "1,2,3";
        System.out.print("<i>foo(&quot;" + a + "&quot;): </i>"); 
        System.out.println("<b>" + foo(a) + "</b>");
    }
}
