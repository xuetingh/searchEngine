import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 *
 */
public class RetrievalAlgorithmLetor {
    private String trainingQueryFile;
    private String trainingQrelsFile;
    private String trainingFeatureVectorsFile;
    private ArrayList<Integer> disabledFeatures;
    private String svmRankLearnPath;
    private String svmRankClassifyPath;
    private double svmRankParamC;
    private String svmRankModelFile;
    private String testingFeatureVectorsFile;
    private String testingDocumentScores;

    private String trecEvalOutputPath;
    private String queryFilePath;

    private RetrievalModelBM25 modelBM25;

    private double k1;
    private double b;
    private double k3;
    private double lambda;
    private int mu;

    private ArrayList<String> qidInOrder;

    RetrievalAlgorithmLetor(Map<String, String> parameters) throws Exception {
        // read parameters
        trainingQueryFile = parameters.get("letor:trainingQueryFile");
        trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
        trainingFeatureVectorsFile = parameters.get("letor:trainingFeatureVectorsFile");
        svmRankLearnPath = parameters.get("letor:svmRankLearnPath");
        svmRankClassifyPath = parameters.get("letor:svmRankClassifyPath");
        if (parameters.containsKey("letor:svmRankParamC")) {
            svmRankParamC = Double.parseDouble(parameters.get("letor:svmRankParamC"));
        } else {
            svmRankParamC = 0.001;
        }
        svmRankModelFile = parameters.get("letor:svmRankModelFile");
        testingFeatureVectorsFile = parameters.get("letor:testingFeatureVectorsFile");
        testingDocumentScores = parameters.get("letor:testingDocumentScores");
        trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        queryFilePath = parameters.get("queryFilePath");
        k1 = Double.parseDouble(parameters.get("BM25:k_1"));
        b = Double.parseDouble(parameters.get("BM25:b"));
        k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        if (k1 < 0 || k3 < 0 || b < 0 || b > 1) throw new IllegalArgumentException("Illegal BM25 parameters");
        modelBM25 = new RetrievalModelBM25(b, k1, k3);
        lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        mu = Integer.parseInt(parameters.get("Indri:mu"));
        if (mu < 0 || lambda < 0 || lambda > 1) throw new IllegalArgumentException("Illegal Indri parameters");
        disabledFeatures = new ArrayList<>();
        if (parameters.containsKey("letor:featureDisable")) {
            String[] f = parameters.get("letor:featureDisable").split(",");
            for (String number : f) {
                int n = Integer.parseInt(number);
                disabledFeatures.add(n);
            }
        }
        qidInOrder = new ArrayList<>();
        // training process
        // read relevance judgement file
        HashMap<String, ArrayList<DocumentData>> qrels = readRelevanceJudgements();
        // get training data, save in qrels in format of (qid, a list of documents with score and features)
        getFeatures(trainingQueryFile, qrels);
        // write train features
        PrintWriter pwTrainFeatVec = null;
        try {
            pwTrainFeatVec = new PrintWriter(new FileWriter(trainingFeatureVectorsFile));
            for (int i = 0; i < qidInOrder.size(); i++) {
                String qid = qidInOrder.get(i);
                ArrayList<DocumentData> docs = qrels.get(qid);
                for (int j = 0; j < docs.size(); j++) {
                    DocumentData doc = docs.get(j);
                    StringBuilder trainData = new StringBuilder();
                    trainData.append(String.format("%.0f qid:%s %s", doc.score, qid, doc.printFeatures()));
                    pwTrainFeatVec.println(trainData);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pwTrainFeatVec.close();
        }
        // SVM learn
        svmLearn();
        // run a BM25 retrieval to get initial ranking and write testing features
        HashMap<String, ArrayList<DocumentData>> testData = getInitialRanking();
        // SVM classify
        svmClassify();
        // read score file and write final rankings
        getFinalRanking(testData);
    }

    private HashMap<String, ArrayList<DocumentData>> readRelevanceJudgements() {
        BufferedReader input = null;
        String qLine = null;
        HashMap<String, ArrayList<DocumentData>> data = new HashMap<>();
        try {
            input = new BufferedReader(new FileReader(trainingQrelsFile));
            String curr_qid = null;
            ArrayList<DocumentData> r = new ArrayList<>();
            while ((qLine = input.readLine()) != null) {
//                System.out.println(qLine);
                String[] line = qLine.split("[\\s\t]");
                String qid = line[0];
                String externalID = line[2];
                String score = line[3];
                if (curr_qid == null) {
                    r.add(new DocumentData(externalID, score));
                    curr_qid = qid;
                } else if (qid.equals(curr_qid)) {
                    r.add(new DocumentData(externalID, score));
                } else {
                    Collections.sort(r);
                    data.put(curr_qid, r);
                    r = new ArrayList<>();
                    curr_qid = qid;
                    r.add(new DocumentData(externalID, score));
                }
            }
            Collections.sort(r);
            data.put(curr_qid, r);
            //System.out.println("line 119: " + data.get("45").size());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null) input.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    private void getFeatures(String queryFile, HashMap<String, ArrayList<DocumentData>> qrels) {
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(queryFile));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                //System.out.println(line);
                int d = line.indexOf(":");
                if (d < 0) throw new IllegalArgumentException("missing ':' in query line");
                String qid = line.substring(0, d);
                String query = line.substring(d + 1);
                System.out.println(qid);
                if (qrels.keySet().contains(qid)) {
                    ArrayList<DocumentData> dd = calculateFeatures(query, qrels.get(qid));
                    qrels.put(qid, dd);
                    qidInOrder.add(qid);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (scanner != null) scanner.close();
        }
    }

    private class DocumentData implements Comparable<DocumentData> {
        String externalID;
        double[] docFeatures;
        double score; // for training data, relevance score; for testing data, svm rank score

        DocumentData(String id, String s) {
            this.externalID = id;
            this.score = Double.parseDouble(s);
            this.docFeatures = new double[18];
        }

        public void setDocFeatures(double[] f) {
            System.arraycopy(f, 0, this.docFeatures, 0, 18);
        }

        @Override
        // descending order
        public int compareTo(DocumentData doc) {
            return Double.compare(doc.score, this.score);
        }

        public String printFeatures() {
            StringBuilder str = new StringBuilder();
            for (int j = 0; j < 18; j++) {
                str.append(String.format("%d:%.17f ", j + 1, this.docFeatures[j]));
            }
            str.append(String.format(" # %s", this.externalID));
            return str.toString();
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            str.append(this.score);
            str.append(this.printFeatures());
            return str.toString();
        }
    }

    private ArrayList<DocumentData> calculateFeatures(String query, ArrayList<DocumentData> docs) {
        //ArrayList<DocumentData> result = new ArrayList<>();
        for (DocumentData doc : docs) {
            double[] featVec = new double[18];
            for (int i = 0; i < 18; i++) {
                featVec[i] = Double.MAX_VALUE;
            }
            int docid = 0;
            try {
                docid = Idx.getInternalDocid(doc.externalID);
            } catch (Exception e) {
                //e.printStackTrace();
                continue;
            }
            // f1 spam score
            if (!disabledFeatures.contains(1)) {
                try {
                    featVec[0] = Integer.parseInt(Idx.getAttribute("spamScore", docid));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // f2 url depth
            if (!disabledFeatures.contains(2)) {
                try {
                    // try to ignore '/' in "http://"
                    String rawUrl = Idx.getAttribute("rawUrl", docid).replace("//", "");
                    int count = rawUrl.length() - rawUrl.replace("/", "").length();
                    featVec[1] = count;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // f3 from wikipedia
            if (!disabledFeatures.contains(3)) {
                try {
                    String rawUrl = Idx.getAttribute("rawUrl", docid);
                    if (rawUrl.contains("wikipedia.org")) featVec[2] = 1;
                    else featVec[2] = 0;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // f4 PageRank score
            if (!disabledFeatures.contains(4)) {
                try {
                    String pr = Idx.getAttribute("PageRank", docid);
                    if (pr != null) featVec[3] = Double.parseDouble(pr);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            String[] terms = null;

            try {
                terms = QryParser.tokenizeString(query);
            } catch (IOException e) {
                System.out.println("query parser fails");
                e.printStackTrace();
            }
            List<String> termList = Arrays.asList(terms);
            TermVector tvBody = null, tvTitle = null, tvUrl = null, tvInlink = null;
            try {
                tvBody = new TermVector(docid, "body");
                tvTitle = new TermVector(docid, "title");
                tvUrl = new TermVector(docid, "url");
                tvInlink = new TermVector(docid, "inlink");
            } catch (IOException e) {
                e.printStackTrace();
            }
            // f5: BM25 score for <q, dbody>
            if (!disabledFeatures.contains(5)) {
                featVec[4] = helperBM25(termList, docid, tvBody, "body");
            }
            // f6: Indri score for <q, dbody>.
            if (!disabledFeatures.contains(6)) {
                featVec[5] = helperIndri(termList, docid, tvBody, "body");
            }
            // f7: Term overlap score (also called Coordination Match) for <q, dbody>.
            if (!disabledFeatures.contains(7)) {
                featVec[6] = overlapScore(termList, tvBody);
            }
            // f8: BM25 score for <q, dtitle>.
            if (!disabledFeatures.contains(8)) {
                featVec[7] = helperBM25(termList, docid, tvTitle, "title");
            }
            // f9: Indri score for <q, dtitle>.
            if (!disabledFeatures.contains(9)) {
                featVec[8] = helperIndri(termList, docid, tvTitle, "title");
            }
            // f10: Term overlap score (also called Coordination Match) for <q, dtitle>.
            if (!disabledFeatures.contains(10)) {
                featVec[9] = overlapScore(termList, tvTitle);
            }
            // f11: BM25 score for <q, durl>.
            if (!disabledFeatures.contains(11)) {
                featVec[10] = helperBM25(termList, docid, tvUrl, "url");
            }
            // f12: Indri score for <q, durl>.
            if (!disabledFeatures.contains(12)) {
                featVec[11] = helperIndri(termList, docid, tvUrl, "url");
            }
            // f13: Term overlap score (also called Coordination Match) for <q, durl>.
            if (!disabledFeatures.contains(13)) {
                featVec[12] = overlapScore(termList, tvUrl);
            }
            // f14: BM25 score for <q, dinlink>.
            if (!disabledFeatures.contains(14)) {
                featVec[13] = helperBM25(termList, docid, tvInlink, "inlink");
            }
            // f15: Indri score for <q, dinlink>.
            if (!disabledFeatures.contains(15)) {
                featVec[14] = helperIndri(termList, docid, tvInlink, "inlink");
            }
            // f16: Term overlap score (also called Coordination Match) for <q, dinlink>.
            if (!disabledFeatures.contains(16)) {
                featVec[15] = overlapScore(termList, tvInlink);
            }
            // f17: average term frequency
            if (!disabledFeatures.contains(17)) {
                //featVec[16] = helperAvgOccur(termList, tvBody);
                featVec[16] = helperWindow(termList, tvBody);
            }
            // f18: percentage of letters in url
            if (!disabledFeatures.contains(18)) {
                try {
                    String rawUrl = Idx.getAttribute("rawUrl", docid).toLowerCase();
                    int count = 0;
                    for (int i = 0; i < rawUrl.length(); i++) {
                        char ch = rawUrl.charAt(i);
                        int value = (int) ch;
                        if (value >= 97 && value <= 122) {
                            count++;
                        }
                    }
                    featVec[17] = 1.0 * count / rawUrl.length();
//                    if (rawUrl.contains("https")) {
//                        featVec[17] = 1;
//                        System.out.println("https");
//                    } else featVec[17] = 0;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            doc.setDocFeatures(featVec);
        }
        // normalize the feature values for query q to [0..1]
        double[] min = new double[18], max = new double[18];
        double featValue;
        for (int i = 0; i < 18; i++) {
            double min_i = Double.MAX_VALUE;
            double max_i = Double.MIN_VALUE;
            for (DocumentData aResult : docs) {
                featValue = aResult.docFeatures[i];
                if (featValue == Double.MAX_VALUE) continue;
                if (featValue < min_i) min_i = featValue;
                if (featValue > max_i) max_i = featValue;
            }
            min[i] = min_i;
            max[i] = max_i;
        }
        for (DocumentData aResult : docs) {
            double[] normalized = new double[18];
            for (int i = 0; i < 18; i++) {
                featValue = aResult.docFeatures[i];
                if (featValue == Double.MAX_VALUE || max[i] == min[i]) normalized[i] = 0;
                else normalized[i] = (featValue - min[i]) / (max[i] - min[i]);
            }
            aResult.setDocFeatures(normalized);
        }
        return docs;
    }

    private double helperIndri(List<String> terms, int docid, TermVector tv, String field) {
        if (tv.stemsLength() == 0) return Double.MAX_VALUE;
        double score = 1;
        List<String> termsOccured = new ArrayList<>();
        try {
            double collectionDocLen = Idx.getSumOfFieldLengths(field);
            double docLen = Idx.getFieldLength(field, docid);
            for (int i = 1; i < tv.stemsLength(); i++) {
                String term = tv.stemString(i);
                if (!terms.contains(term)) continue;
                double tf = tv.stemFreq(i);
                double s = 0;
                termsOccured.add(term);
                double ctf = Idx.getTotalTermFreq(field, term);
                double mle = ctf / collectionDocLen;
                s = (1 - lambda) * (tf + mu * mle) / (docLen + mu) + lambda * mle;
                score *= s;

            }
            // pitfall 3
            if (termsOccured.size() == 0) {
//              System.out.println("pitfall 3");
                return 0.0;
            }
            for (String term : terms) {
                if (termsOccured.contains(term)) {
                    continue;
                } else {
                    double mle = (double) Idx.getTotalTermFreq(field, term) / collectionDocLen;
                    double s = (1 - lambda) * (mu * mle) / (docLen + mu) + lambda * mle;
                    score *= s;
                }
            }
            score = Math.pow(score, 1.0 / terms.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return score;
    }

    private double helperBM25(List<String> terms, int docid, TermVector tv, String field) {
        if (tv.stemsLength() == 0) return Double.MAX_VALUE;
        double score = 0;
        try {
            double collectionDocLen = Idx.getSumOfFieldLengths(field);
            double docLen = Idx.getFieldLength(field, docid);
            for (int i = 1; i < tv.stemsLength(); i++) {
                String term = tv.stemString(i);
                if (!terms.contains(term)) continue;
                double df = tv.stemDf(i);
                double tf = tv.stemFreq(i);
                double s;
                double avgDocLen = collectionDocLen / Idx.getDocCount(field);
                double idf = Math.max(Math.log((Idx.getNumDocs() - df + 0.5) / (df + 0.5)), 0);
                s = idf * tf / (tf + k1 * ((1 - b) + b * (docLen / avgDocLen))); // assuming qtf = 1
                score += s;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return score;
    }

    private double overlapScore(List<String> terms, TermVector tv) {
        if (tv.stemsLength() == 0) return Double.MAX_VALUE;
        double score = 0;
        int size = tv.stemsLength();
        for (int i = 1; i < size; i++) {
            if (terms.contains(tv.stemString(i))) score++;
        }
        return score / terms.size();
    }

    private void svmLearn() throws Exception {
        Process cmdProc = Runtime.getRuntime().exec(
                new String[]{svmRankLearnPath, "-c", String.valueOf(svmRankParamC), trainingFeatureVectorsFile,
                        svmRankModelFile});
        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    private HashMap<String, ArrayList<DocumentData>> getInitialRanking() {
        Scanner scanner = null;
        PrintWriter pwTestFeatVec = null;
        HashMap<String, ArrayList<DocumentData>> result = new HashMap<>();
        try {
            scanner = new Scanner(new File(queryFilePath));
            pwTestFeatVec = new PrintWriter(new FileWriter(testingFeatureVectorsFile));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                int d = line.indexOf(":");
                if (d < 0) throw new IllegalArgumentException("missing ':' in query line");
                String qid = line.substring(0, d);
                String query = line.substring(d + 1);
                ScoreList r = QryEval.processQuery(query, modelBM25, qid);
                r.sort();
                ArrayList<DocumentData> docs = new ArrayList<>();
                int num = Math.min(100, r.size());
                for (int i = 0; i < num; i++) {
                    String externalID = Idx.getExternalDocid(r.getDocid(i));
                    docs.add(new DocumentData(externalID, "0"));
                }
                ArrayList<DocumentData> dd = calculateFeatures(query, docs);
                int size = dd.size();
                for (int i = 0; i < size; i++) {
                    DocumentData doc = dd.get(i);
                    StringBuilder testData = new StringBuilder();
                    testData.append(String.format("0 qid:%s %s", qid, doc.printFeatures()));
                    pwTestFeatVec.println(testData);
                }
                result.put(qid, dd);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (scanner != null) scanner.close();
            if (pwTestFeatVec != null) pwTestFeatVec.close();
        }
        return result;
    }

    private void svmClassify() throws Exception {
        Process cmdProc = Runtime.getRuntime().exec(
                new String[]{svmRankClassifyPath, testingFeatureVectorsFile, svmRankModelFile,
                        testingDocumentScores});
        // consume stdout and print it out for debugging purposes
        BufferedReader stdoutReader = new BufferedReader(
                new InputStreamReader(cmdProc.getInputStream()));
        String line;
        while ((line = stdoutReader.readLine()) != null) {
            System.out.println(line);
        }
        // consume stderr and print it for debugging purposes
        BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(cmdProc.getErrorStream()));
        while ((line = stderrReader.readLine()) != null) {
            System.out.println(line);
        }

        // get the return value from the executable. 0 means success, non-zero
        // indicates a problem
        int retValue = cmdProc.waitFor();
        if (retValue != 0) {
            throw new Exception("SVM Rank crashed.");
        }
    }

    private void getFinalRanking(HashMap<String, ArrayList<DocumentData>> testData) {
        Scanner input = null;
        PrintWriter output = null;
        try {
            input = new Scanner(new File(testingDocumentScores));
            output = new PrintWriter(new FileWriter(trecEvalOutputPath));
            for (String qid : testData.keySet()) {
                ArrayList<DocumentData> docs = testData.get(qid);
                int size = docs.size();
                for (int i = 0; i < size; i++) {
                    DocumentData doc = docs.get(i);
                    doc.score = Double.parseDouble(input.nextLine());
                }
                Collections.sort(docs);
                for (int i = 0; i < size; i++) {
                    DocumentData doc = docs.get(i);
                    output.println(String.format("%s Q0 %s %d %.12f letor", qid, doc.externalID, i + 1, doc.score));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (input != null) input.close();
            if (output != null) output.close();
        }
    }

    // average term freq in body field
    private double helperAvgOccur(List<String> terms, TermVector tv) {
        if (tv.stemsLength() == 0) return Double.MAX_VALUE;
        int size = terms.size();
        int occur = 0;
        int pos = tv.positionsLength();
        for (int i = 0; i < pos; i++) {
            if (terms.contains(tv.stemString(tv.stemAt(i)))) occur++;
        }
        return 1.0 * occur / size;
    }

    // return the window size that all terms occurs / docLength, if not all terms occurs, return 1
    private double helperWindow(List<String> terms, TermVector tv) {
        if (tv.stemsLength() == 0) return 1;
        int size = terms.size();
        if (size == 1) return 1;
        List<Integer> termIndex = new ArrayList<>();
        for (String term : terms) {
            int idx = tv.indexOfStem(term);
            if (idx != -1) termIndex.add(idx);
            else return 1;
        }
        HashMap<Integer, ArrayList<Integer>> indexes = new HashMap<>();
        for (int i : termIndex) {
            indexes.put(i, new ArrayList<>());
        }
        indexes.put(0, new ArrayList<>());
        int pos = tv.positionsLength();
        // get inverted list
        for (int i = 0; i < pos; i++) {
            for (int termIdx : termIndex) {
                if (termIdx == tv.stemAt(i)) {
                    ArrayList<Integer> list = indexes.get(termIdx);
                    list.add(i);
                    indexes.put(termIdx, list);
                }
            }
        }
        return 1.0 * getApproximity(indexes) / pos;
    }

    private int getApproximity(HashMap<Integer, ArrayList<Integer>> map) {
        ArrayList<ArrayList<Integer>> list = new ArrayList<>();
        for (int key : map.keySet()) {
            ArrayList<Integer> l = map.get(key);
            if (l.size() == 0) return 1;
            list.add(l);
        }
        PriorityQueue<Point> pq = new PriorityQueue<>((a, b) -> (a.x - b.x));
        int size = list.size();
        int[] idxArray = new int[size];
        int max = 0;
        for (int i = 0; i < size; i++) {
            int num = list.get(i).get(0);
            pq.add(new Point(num, i));
            max = Math.max(max, num);
        }
        int start = -1, end = -1, gap = Integer.MAX_VALUE;
        while (pq.size() == size) {
            Point first = pq.poll();
            int min = first.x, idx = first.y;
            if (max - min < gap) {
                gap = max - min;
                start = min;
                end = max;
            }
            if (++idxArray[idx] < list.get(idx).size()) {
                first.x = list.get(idx).get(idxArray[idx]);
                pq.add(first);
                max = Math.max(max, first.x);
            }
        }
        if (size == 1) System.out.println(end + " " + start);
        return end - start + 1;
    }

}
