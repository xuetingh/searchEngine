/*
 *  Copyright (c) 2018, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.2.
 */

import java.io.*;
import java.util.*;
import java.util.Scanner;
import java.util.Map.Entry;

/**
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * It implements an unranked Boolean retrieval model, however it is
 * easily extended to other retrieval models.  For more information,
 * see the ReadMe.txt file.
 */
public class QryEval {

    //  --------------- Constants and variables ---------------------

    private static final String USAGE =
            "Usage:  java QryEval paramFile\n\n";

    private static final String[] TEXT_FIELDS =
            {"body", "title", "url", "inlink"};
    // all parameters read from parameter file
    private static Map<String, String> parameters = new HashMap<>();
    // for pfb, save documents in form of (qid, ScoreList)
    private static Map<String, ScoreList> ranking;
    private static boolean fb = false;
    private static boolean hasRanking = false;
    private static PrintWriter expandedQryFile = null;

    //  --------------- Methods ---------------------------------------

    /**
     * @param args The only argument is the parameter file name.
     * @throws Exception Error accessing the Lucene index.
     */
    public static void main(String[] args) throws Exception {

        //  This is a timer that you may find useful.  It is used here to
        //  time how long the entire program takes, but you can move it
        //  around to time specific parts of your code.

        Timer timer = new Timer();
        timer.start();

        //  Check that a parameter file is included, and that the required
        //  parameters are present.  Just store the parameters.  They get
        //  processed later during initialization of different system
        //  components.

        if (args.length < 1) {
            throw new IllegalArgumentException(USAGE);
        }
        parameters = readParameterFile(args[0]);
        //  Open the index and initialize the retrieval model.

        Idx.open(parameters.get("indexPath"));
        RetrievalModel model = initializeRetrievalModel(parameters);

        //  Perform experiments.
        processQueryFile(model);
        //  Clean up.

        timer.stop();
        System.out.println("Time:  " + timer);
    }

    /**
     *
     * @param r
     * @return
     */
    private static String queryExpansion(ScoreList r) {
        StringBuilder expandedQuery = null;
        // get parameters needed
        int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
        int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
        int fbMu = Integer.parseInt(parameters.get("fbMu"));
        try {
            double length = Idx.getSumOfFieldLengths("body");
            // for each query(qid), a new map of term and p_mle(t|C) is created, avoid repeated calculation
            HashMap<String, Double> termMLE = new HashMap<>();
            // for each term, stores its sum of scores from top fbDocs documents
            HashMap<String, Double> scoreMap = new HashMap<>();
            // for each term, keep track of what documents it appeared in, so we can calculate default score later
            HashMap<String, ArrayList<Integer>> docs = new HashMap<>();
            int docSize = Math.min(fbDocs, r.size());
            // go through the top fbDocs documents
            for (int i = 0; i < docSize; i++) {
                int docid = r.getDocid(i);
                double docLen = Idx.getFieldLength("body", docid);
                double docScore = r.getDocidScore(i);
                TermVector tv = new TermVector(docid, "body");
                // go through the forward index
                for (int j = 1; j < tv.stemsLength(); j++) {
                    String term = tv.stemString(j);
                    // ignore words with "." or ","
                    if (term.contains(".") || term.contains(",")) {
                        continue;
                    }
                    // save which documents contains a certain term, so we know which documents don't
                    if (docs.containsKey(term)) {
                        ArrayList<Integer> docList = docs.get(term);
                        docList.add(docid);
                        docs.put(term, docList);
                    } else {
                        ArrayList<Integer> docList = new ArrayList<>();
                        docList.add(docid);
                        docs.put(term, docList);
                    }
                    double mle;
                    if (!termMLE.containsKey(term)) {
                        double ctf = tv.totalStemFreq(j);
                        mle = ctf / length;
                        termMLE.put(term, mle);
                    } else {
                        mle = termMLE.get(term);
                    }
                    double tf = tv.stemFreq(j);
                    double ptd = (tf + fbMu * mle) / (docLen + fbMu);
                    double score_d = ptd * docScore * (Math.log(1.0 / mle));
                    if (scoreMap.containsKey(term)) {
                        double score = scoreMap.get(term);
                        score = score + score_d;
                        scoreMap.put(term, score);
                    } else {
                        scoreMap.put(term, score_d);
                    }
                }
            }
            // now, go through the term in scoreMap, add default scores of those documents where tf=0
            for (String term : docs.keySet()) {
                ArrayList<Integer> docsList = docs.get(term);
                for (int i = 0; i < docSize; i++) {
                    int docid = r.getDocid(i);
                    if (docsList.contains(docid)) {
                        continue;
                    } else {
                        double docScore = r.getDocidScore(i);
                        double mle = termMLE.get(term);
                        double docLen = Idx.getFieldLength("body", docid);
                        double ptd = (fbMu * mle) / (docLen + fbMu);
                        double score_d = ptd * docScore * (Math.log(1.0 / mle));
                        double score = scoreMap.get(term);
                        score = score + score_d;
                        scoreMap.put(term, score);
                    }
                }
            }
            // sort the scoreMap by score in descending order
            LinkedList<Entry<String, Double>> termsInOrder = sortByComparator(scoreMap);
            expandedQuery = new StringBuilder();

            expandedQuery.append("#wand (");
            int termSize = Math.min(fbTerms, termsInOrder.size());
            for (int i = 0; i < termSize; i++) {
                Entry<String, Double> firstTerm = termsInOrder.pollFirst();
                double score = firstTerm.getValue();
                String term = firstTerm.getKey();
                expandedQuery.append(String.format(" %.4f %s", score, term));
            }
            expandedQuery.append(")");
        } catch (IOException ex) {
            System.err.println("Caught IOException: " + ex.getMessage());
        } finally {
            System.out.println(expandedQuery.toString());
            return expandedQuery.toString();
        }
    }

    /**
     * helper function, to sort the hashmap scoreMap by the score.
     * the way to sort hashmap by value comes from:
     * https://stackoverflow.com/questions/8119366/sorting-hashmap-by-values
     *
     * @param unsortMap
     * @return
     */
    private static LinkedList<Entry<String, Double>> sortByComparator(Map<String, Double> unsortMap) {
        LinkedList<Entry<String, Double>> list = new LinkedList<>(unsortMap.entrySet());
        // Sorting the list based on values in descending order
        Collections.sort(list, new Comparator<Entry<String, Double>>() {
            public int compare(Entry<String, Double> o1, Entry<String, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        // Maintaining insertion order with the help of LinkedList
        return list;
    }

    /**
     * Go through the ranking file, sort and store pairs of docid and score by qid to a Hashmap
     *
     * @param initialRankingFile a file containing ranked docs of a qid
     * @return a Hashmap whose key is qid, value is a ScoreList containing pairs of docid and score
     * @throws IOException
     */
    private static HashMap<String, ScoreList> getRankingFile(String initialRankingFile) throws IOException {
        BufferedReader input = null;
        HashMap<String, ScoreList> ranking = new HashMap<>();
        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(initialRankingFile));
            String curr_qid = null;
            ScoreList r = new ScoreList();
            while ((qLine = input.readLine()) != null) {
                String[] line = qLine.split("[\\s\t]");
                String qid = line[0];
                int docid = Idx.getInternalDocid(line[2]);
                double score = Double.parseDouble(line[4]);
//                System.out.println(docid + ", " + score);
                if (curr_qid == null) {
                    System.out.println("curr qid = null");
                    r.add(docid, score);
                    curr_qid = qid;
                } else if (qid.equals(curr_qid)) {
                    System.out.println("same qid");
                    r.add(docid, score);
                } else {
                    System.out.println("next qid");
                    r.sort();
                    ranking.put(curr_qid, r);
                    r = new ScoreList();
                    curr_qid = qid;
                }
            }
            r.sort();
            ranking.put(curr_qid, r);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            input.close();
            System.out.println(ranking);
            return ranking;

        }
    }

    /**
     * Allocate the retrieval model and initialize it using parameters
     * from the parameter file.
     *
     * @return The initialized retrieval model
     * @throws IOException Error accessing the Lucene index.
     */

    private static RetrievalModel initializeRetrievalModel(Map<String, String> parameters)
            throws IOException {

        RetrievalModel model = null;
        String modelString = parameters.get("retrievalAlgorithm").toLowerCase();

        if (modelString.equals("unrankedboolean")) {
            model = new RetrievalModelUnrankedBoolean();
        } else if (modelString.equals("rankedboolean")) {
            model = new RetrievalModelRankedBoolean();
        } else if (modelString.equals("bm25")) {
            double k1 = Double.parseDouble(parameters.get("BM25:k_1"));
            double b = Double.parseDouble(parameters.get("BM25:b"));
            double k3 = Double.parseDouble(parameters.get("BM25:k_3"));
            model = new RetrievalModelBM25(b, k1, k3);
        } else if (modelString.equals("indri")) {
            int mu = Integer.parseInt(parameters.get("Indri:mu"));
            double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
            model = new RetrievalModelIndri(mu, lambda);
        } else {
            throw new IllegalArgumentException
                    ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
        }
        return model;
    }

    /**
     * Print a message indicating the amount of memory used. The caller can
     * indicate whether garbage collection should be performed, which slows the
     * program but reduces memory usage.
     *
     * @param gc If true, run the garbage collector before reporting.
     */
    public static void printMemoryUsage(boolean gc) {

        Runtime runtime = Runtime.getRuntime();

        if (gc)
            runtime.gc();

        System.out.println("Memory used:  "
                + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
    }

    /**
     * Process one query.
     *
     * @param qString A string that contains a query.
     * @param model   The retrieval model determines how matching and scoring is done.
     * @return Search results
     * @throws IOException Error accessing the index
     */
    static ScoreList processQuery(String qString, RetrievalModel model, String qid)
            throws IOException {

        String defaultOp = model.defaultQrySopName();
        qString = defaultOp + "(" + qString + ")";
        Qry q = QryParser.getQuery(qString);

        ScoreList r;
        // do not need feedback
        if (!fb) {
            System.out.println("    ---->" + qString);
            r = processQry(q, model);
        } else {
            double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
            // need feedback, so we need to expand query based on initial ranking
            // if ranking file is specified
            if (hasRanking) r = ranking.get(qid);
                // if ranking file is not specified, do a retrieval to get a ranking
            else r = processQry(q, model);
            String expandedQuery = queryExpansion(r);
            if (expandedQryFile != null) expandedQryFile.println(qid + ": " + expandedQuery);
            else System.out.println("Expanded Query: " + expandedQuery);
            String newQuery = String.format("#wand ( %.2f %s %.2f %s)",
                    fbOrigWeight, qString, 1 - fbOrigWeight, expandedQuery);
            System.out.println("    ---->" + newQuery);
            Qry qNew = QryParser.getQuery(newQuery);
            r = processQry(qNew, model);
        }
        return r;
    }

    /**
     * helper function, process a query and returns ScoreList
     *
     * @param q     a query operator with terms
     * @param model
     * @return
     * @throws IOException
     */
    static ScoreList processQry(Qry q, RetrievalModel model) throws IOException {
        if (q != null) {

            ScoreList r = new ScoreList();

            if (q.args.size() > 0) {        // Ignore empty queries

                q.initialize(model);

                while (q.docIteratorHasMatch(model)) {
                    int docid = q.docIteratorGetMatch();
                    double score = ((QrySop) q).getScore(model);
                    r.add(docid, score);
                    q.docIteratorAdvancePast(docid);
                }
            }
            r.sort();
            return r;
        } else
            return null;
    }

    static void processQueryFile(RetrievalModel model)
            throws IOException {
        // get parameters need
        int outputLength = Integer.parseInt(parameters.get("trecEvalOutputLength"));
        String queryFilePath = parameters.get("queryFilePath");
        String trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        String initialRankingFile = parameters.get("fbInitialRankingFile");

        if (parameters.keySet().contains("fb") && Boolean.parseBoolean(parameters.get("fb"))) {
            fb = true;
            if (parameters.keySet().contains("fbInitialRankingFile")) {
                hasRanking = true;
                ranking = getRankingFile(initialRankingFile);
            }
        }
        if(fb && parameters.containsKey("fbExpansionQueryFile")){
            String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
            expandedQryFile = new PrintWriter(new FileWriter(fbExpansionQueryFile));
        }

        BufferedReader input = null;

        try {
            String qLine = null;
            input = new BufferedReader(new FileReader(queryFilePath));
            PrintWriter output = null;

            try {
                output = new PrintWriter(new FileWriter(trecEvalOutputPath));
                //  Each pass of the loop processes one query.
                while ((qLine = input.readLine()) != null) {
                    int d = qLine.indexOf(':');
                    if (d < 0) {
                        throw new IllegalArgumentException
                                ("Syntax error:  Missing ':' in query line.");
                    }

                    printMemoryUsage(false);
                    String qid = qLine.substring(0, d);
                    String query = qLine.substring(d + 1);
                    System.out.println("Query " + qLine);

                    ScoreList r = null;
                    r = processQuery(query, model, qid);
                    if (r != null) {
                        printResults(qid, r, output, outputLength);
                        System.out.println();
                    }
                }
            } catch (IOException ex) {
                System.err.println("Caught IOException: " + ex.getMessage());
            } finally {
                output.close();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            input.close();
            if (expandedQryFile!= null) expandedQryFile.close();
        }
    }

    /**
     * Print the query results.
     * <p>
     * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
     * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
     * <p>
     * QueryID Q0 DocID Rank Score RunID
     *
     * @param queryName Original query.
     * @param result    A list of document ids and scores
     * @throws IOException Error accessing the Lucene index.
     */
    static void printResults(String queryName, ScoreList result, PrintWriter output, int outputLength)
            throws IOException {
        if (result.size() < 1) {
            output.println(String.format("%s\tQ0\tdummy\t1\t0\trun-1", queryName));
        } else {
            for (int i = 0; i < Math.min(outputLength, result.size()); i++) {
                output.println(String.format("%s\tQ0\t%s\t%d\t%.18f\trun-1", queryName,
                        Idx.getExternalDocid(result.getDocid(i)), i + 1, result.getDocidScore(i)));
            }
        }
        output.flush();
    }

    /**
     * Read the specified parameter file, and confirm that the required
     * parameters are present.  The parameters are returned in a
     * HashMap.  The caller (or its minions) are responsible for processing
     * them.
     *
     * @return The parameters, in <key, value> format.
     */
    private static Map<String, String> readParameterFile(String parameterFileName)
            throws IOException {

        Map<String, String> parameters = new HashMap<>();

        File parameterFile = new File(parameterFileName);

        if (!parameterFile.canRead()) {
            throw new IllegalArgumentException
                    ("Can't read " + parameterFileName);
        }

        Scanner scan = new Scanner(parameterFile);
        String line = null;
        do {
            line = scan.nextLine();
            String[] pair = line.split("=");
            parameters.put(pair[0].trim(), pair[1].trim());
        } while (scan.hasNext());

        scan.close();

        if (!(parameters.containsKey("indexPath") &&
                parameters.containsKey("queryFilePath") &&
                parameters.containsKey("trecEvalOutputPath") &&
                parameters.containsKey("trecEvalOutputLength") &&
                parameters.containsKey("retrievalAlgorithm"))) {
            throw new IllegalArgumentException
                    ("Required parameters were missing from the parameter file.");
        }
        return parameters;
    }

}
