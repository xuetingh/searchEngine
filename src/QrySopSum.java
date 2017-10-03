import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopSum extends QrySop {

    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    public boolean docIteratorHasMatch (RetrievalModel r) {
        return this.docIteratorHasMatchMin (r);
    }

    /**
     *
     * @param r The retrieval model that determines how scores are calculated.
     * @return
     * @throws IOException
     */
    public double getScore (RetrievalModel r) throws IOException {

        if (r instanceof RetrievalModelBM25) {
            return this.getScoreBM25(r);
        } else {
            throw new IllegalArgumentException
                    (r.getClass().getName() + " doesn't support the SUM operator.");
        }
    }

    public double getScoreBM25(RetrievalModel r) throws IOException{

        if (! this.docIteratorHasMatch(r))  {
            return 0;
        }

        int doc_id = this.docIteratorGetMatch();
        double score = 0;
        for (int i = 0; i < this.args.size(); i++) {
            QrySop q = (QrySop) this.args.get(i);
//            if ((q instanceof QrySopOr) || (q instanceof QrySopAnd) || (q instanceof QrySopW)) {
//                throw new IllegalArgumentException
//                        (r.getClass().getName() + " doesn't support the OR/AND/WAND/WSUM operator.");
//            }
            if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == doc_id) {
                score += q.getScore(r);
            }
        }
        return score;
    }

    // This method is never used, only for eliminating error.
    @Override
    public double getDefaultScore(RetrievalModel r, int docid) {
        return 0;
    }

}