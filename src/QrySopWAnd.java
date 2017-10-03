import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopWAnd extends QrySopW {


    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        return this.docIteratorHasMatchMin(r);
    }

    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */

    public double getScore (RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the WAND operator.");
        }
    }

    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri (RetrievalModel r) throws IOException {
        double score = 1;
        int docid = this.docIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i++) {
            QrySop q = (QrySop) this.args.get(i);
            double weight = this.weights.get(i);
            if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid) {
                score *= Math.pow(q.getScore(r), weight / this.weightSum);
            } else {
                score *= Math.pow(q.getDefaultScore(r, docid), weight / this.weightSum);
            }
        }
        return score;
    }

    /**
     *  get default score for Indri model when a document doesn't contain a term in query.
     * @param r
     * @param docid
     * @return
     */
    public double getDefaultScore (RetrievalModel r, int docid) throws IOException{
        double score = 1;
        for (int i = 0; i < this.args.size(); i++) {
            Qry q = this.args.get(i);
            double weight = this.weights.get(i);
            score *= Math.pow(((QrySop) q).getDefaultScore(r, docid), weight / this.weightSum);
        }
        return score;
    }

}