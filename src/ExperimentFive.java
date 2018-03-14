import java.io.*;

// for hw2 experiment 4
public class ExperimentFive {
    void makeQry(double w1, double w2, double w3, String w) {
        BufferedReader input = null;
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader("query.txt"));
            File f = new File("query_hw2_e5_" + w + ".txt");
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
                output.write(terms[0] + ":#WAND(");
                output.write(w1 + " #AND( ");
                for (int i = 1; i < terms.length; i++) {
                    output.write(" " + terms[i]);
                }
                output.write(") " + w2 + " #AND(");
                for (int i = 1; i < terms.length - 1; i++) {
                    output.write(" #NEAR/1( " + terms[i] + " " + terms[i + 1] + " )");
                }
                output.write(") " + w3 + " #AND(");
                for (int i = 1; i < terms.length - 1; i++) {
                    output.write(" #WINDOW/8( " + terms[i] + " " + terms[i + 1] + " )");
                }
                output.write(") )\n");
                output.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ExperimentFive ef = new ExperimentFive();
        ef.makeQry(0.1, 0.8, 0.1, "181");
        ef.makeQry(0.1, 0.1, 0.8, "118");
        ef.makeQry(0.2, 0.4, 0.4, "244");
        ef.makeQry(0.4, 0.2, 0.4, "424");
        ef.makeQry(0.4, 0.4, 0.2, "442");

    }

}
