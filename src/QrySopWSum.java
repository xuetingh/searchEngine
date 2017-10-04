import java.io.*;

/**
 * The AND operator for all retrieval models.
 */
public class QrySopWSum extends QrySopW {

    /**
     * Indicates whether the query has a match.
     *
     * @param r The retrieval model that determines what is a match
     * @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch(RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    /**
     * Get a score for the document that docIteratorHasMatch matched.
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return The document score.
     * @throws IOException Error accessing the Lucene index
     */
    public double getScore(RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WSUM operator.");
        }
    }

    /**
     * @param r
     * @return
     * @throws IOException
     */
    private double getScoreIndri(RetrievalModel r) throws IOException {
        double score = 0.0;
        int docid = this.docIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i++) {
            QrySop q = (QrySop) this.args.get(i);
            double weight = this.weights.get(i);
            if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid) {
                score += (q.getScore(r)) * weight / this.weightSum;
            } else {
                score += (q.getDefaultScore(r, docid)) * weight / this.weightSum;
            }
        }
        return score;
    }

    /**
     * Only calculate default for Indri model.
     *
     * @param r
     * @param docid
     * @return
     */
    @Override
    public double getDefaultScore(RetrievalModel r, int docid) throws IOException {
        double score = 0.0;
        for (int i = 0; i < this.args.size(); i++) {
            Qry q = this.args.get(i);
            double weight = this.weights.get(i);
            score += (((QrySop) q).getDefaultScore(r, docid)) * weight / this.weightSum;
        }
        return score;
    }

}