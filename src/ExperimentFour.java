import java.io.*;
// for hw2 experiment 4
public class ExperimentFour {
    void makeQry(double w1, double w2, double w3, double w4, String w) {
        BufferedReader input = null;
        try
        {
            String qLine = null;
            input = new BufferedReader(new FileReader("trainingQuery.txt"));
            File f = new File(w +".txt");
            FileOutputStream fStream = new FileOutputStream(f);
            OutputStreamWriter output = new OutputStreamWriter(fStream);
            //  Each pass of the loop processes one query.
            while ((qLine = input.readLine()) != null) {
                int d = qLine.indexOf(":");

                if (d < 0) {
                    throw new IllegalArgumentException
                            ("Syntax error:  Missing ':' in query line.");
                }
                String terms[] = qLine.split("(\\s+)|(:)");
                output.write( terms[0] + ":#AND(");
                for (int i = 1; i < terms.length; i++) {
                    output.write("#WSUM(" + w1 + " " + terms[i] + ".url "+ w2+ " " + terms[i] + ".keywords "+
                            w3 + " " + terms[i] + ".title  " + w4 + " " + terms[i] + ".body)");
                }
                output.write(")\n");
                output.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args){
        ExperimentFour ef = new ExperimentFour();
        ef.makeQry(0.1, 0.1, 0.7,0.1, "1171");
        ef.makeQry(0.1, 0.7, 0.1,0.1, "1711");
        ef.makeQry(0.7, 0.1, 0.1,0.1, "7111");
        ef.makeQry(0.3, 0.2, 0.1,0.4, "3214");
        ef.makeQry(0.2, 0.1, 0.1,0.6, "2116");

    }

}
