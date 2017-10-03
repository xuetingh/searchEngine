/**
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
        return this.getScoreRankedBoolean(r);
    } else if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25(r);
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }

  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return this.getArg(0).docIteratorGetMatchPosting().tf;
    }
  }

  /**
   * getScore for the BM25 retrieval model.
   * @param r
   * @return
   * @throws IOException
   */
  public double getScoreBM25 (RetrievalModel r) throws IOException {
    double score = 0;
    Qry q = this.args.get(0);
    if (q.docIteratorHasMatch(r)) {
      int df = ((QryIop) q).getDf();
      //Jamie's code does this
      double idf = Math.max(Math.log((Idx.getNumDocs() - df + 0.5) / (df + 0.5)), 0);
      double avgLength =  (double) Idx.getSumOfFieldLengths(((QryIop) q).field) / Idx.getDocCount(((QryIop) q).field);
      double tf = (((QryIop) q).docIteratorGetMatchPosting().tf);
      double k_1 = ((RetrievalModelBM25) r).getK1();
      double b = ((RetrievalModelBM25) r).getB();
      //double k_3 = ((RetrievalModelBM25) r).getK3();
      double docLen = (Idx.getFieldLength(((QryIop) q).field, q.docIteratorGetMatch()));
      double userWeight = 1.0; // According to write-up, qtf in example query string is always 1, so user weight = 1
      double tfWeight = tf / (tf + k_1 * (1.0 - b + b * (docLen / avgLength)));
      score = idf * tfWeight * userWeight;
    }
    return score;
  }

  /**
   * getScore for the Indri retrieval model.
   * @param r
   * @return
   * @throws IOException
   */
  public double getScoreIndri (RetrievalModel r) throws IOException {
    double score = 1.0;
    Qry q = this.args.get(0);
    if (q.docIteratorHasMatch(r)) {
      double mu = ((RetrievalModelIndri) r).getMu();
      double lambda = ((RetrievalModelIndri) r).getLambda();
      double mle = (double)(((QryIop) q).getCtf()) / (Idx.getSumOfFieldLengths(((QryIop) q).field));
      double docLength = (double)(Idx.getFieldLength(((QryIop) q).field, q.docIteratorGetMatch()));
      double tf = (double)(((QryIop) q).docIteratorGetMatchPosting().tf);
      score = (1 - lambda) * (tf + mu * mle) / (docLength + mu) + lambda * mle;
    }
    return score;
  }

  /**
   * get default score for the Indri retrieval model. tf=0.
   * @param r
   * @param docid
   * @return
   * @throws IOException
   */
  public double getDefaultScore (RetrievalModel r, int docid) throws IOException {
    Qry q = this.args.get(0);
    double mu = ((RetrievalModelIndri) r).getMu();
    double lambda = ((RetrievalModelIndri) r).getLambda();
    double mle = (double)(((QryIop) q).getCtf()) / (double)(Idx.getSumOfFieldLengths(((QryIop) q).field));
    double docLength = (double)(Idx.getFieldLength(((QryIop) q).field, docid));
    double score = (1 - lambda) * (mu * mle) / (docLength + mu) + lambda * mle;
    return score;
  }

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
