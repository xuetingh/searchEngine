import java.io.IOException;

public class QrySopAnd extends QrySop {
    
    /**
     *  Indicates whether the query has a match.
     *  @param r The retrieval model that determines what is a match
     *  @return True if the query matches, otherwise false.
     */
    @Override
    public boolean docIteratorHasMatch(RetrievalModel r) {
        if(r instanceof RetrievalModelIndri) {
            return this.docIteratorHasMatchMin(r);
        }
        else return this.docIteratorHasMatchAll(r);
    }
 
    /**
     *  Get a score for the document that docIteratorHasMatch matched.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    @Override
    public double getScore (RetrievalModel r) throws IOException {
        if (r instanceof RetrievalModelUnrankedBoolean) {
            return this.getScoreUnrankedBoolean (r);
        } else if (r instanceof RetrievalModelRankedBoolean) {
            return this.getScoreRankedBoolean (r);
        } else if (r instanceof RetrievalModelIndri) {
            return this.getScoreIndri(r);
        } else {
            throw new IllegalArgumentException
            (r.getClass().getName() + " doesn't support the AND operator.");
        }
    }


    /**
     *  getScore for the UnrankedBoolean retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
      if (! this.docIteratorHasMatchCache()) {
        return 0.0;
      } else {
        return 1.0;
      }
    }
    /**
     *  getScore for the RankedBoolean retrieval model.The score is calculated
     *            based on the lowest score of term in args.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */

    private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
        if (! this.docIteratorHasMatchCache()) {
          return 0.0;
        } else {
          double min_score = Double.MAX_VALUE;
          for (Qry q : this.args) {
            if (q.docIteratorHasMatch(r) && 
                    q.docIteratorGetMatch() == this.docIteratorGetMatch() ) {
                //  can't call getScore directly, because it may get false score
                //  when q is sopor or sopand 
              min_score = Math.min(min_score, ((QrySop) q).getScore(r));
            }
          }
          return min_score;
        }
      }
    /**
     *  getScore for the Indri retrieval model.
     *  @param r The retrieval model that determines how scores are calculated.
     *  @return The document score.
     *  @throws IOException Error accessing the Lucene index
     */
    private double getScoreIndri (RetrievalModel r) throws IOException {
        double score = 1.0;
        int docid = this.docIteratorGetMatch();
        for (int i =0; i<this.args.size(); i++) {
            Qry q = this.args.get(i);
            if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == docid) {
                score *= Math.pow(((QrySop) q).getScore(r), 1.0/this.args.size());
            } else {
                score *= Math.pow(((QrySop) q).getDefaultScore(r, docid), 1.0/this.args.size());
            }
        }
        return score;
    }

    /**
     *  get default score only for the Indri retrieval model.
     * @param r
     * @param docid
     * @return
     * @throws IOException
     */
    public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
        double score = 1.0;
        for (int i = 0; i < this.args.size(); i++) {
            Qry q = this.args.get(i);
            score *= ((QrySopScore) q).getDefaultScore(r, docid);
        }

        return score;
    }

}
