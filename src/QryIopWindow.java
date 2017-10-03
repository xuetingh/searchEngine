import java.io.*;
import java.util.ArrayList;


public class QryIopWindow extends QryIop {

    private int distance;

    public QryIopWindow(int dist) {
        this.distance = dist;
    }

    /**
     *  Evaluate the query operator.
     *  @throws IOException Error accessing the Lucene index.
     */
    @Override
    protected void evaluate() throws IOException {
        //  first query argument, will be used many times
        QryIop firstQry = this.getArg(0);
        int argSize = this.args.size();
        //  Create an empty inverted list.  If there are no query arguments,
        //  that's the final result.
        this.invertedList = new InvList(this.getField());
        // a list of postings to record
        ArrayList<Integer> postings = new ArrayList<>();
        if (argSize < 2) {
            return;
        }
        //  Use docIteratorHasMatchAll method in Qry to get the doc that has all terms in it
        while (this.docIteratorHasMatchAll(null)) {
            //  initialisation of min,max location and min term
            int locMin = -1;
            int locMax = -1;
            QryIop minQry = firstQry;
            //  this loop checks if the adjacent two terms meet the distance requirement
            //  when any of distance exceeds parameter distance or for any term there is
            //  no next locations in this doc, break for loop to move on to next doc
            for (int i = 0; i < argSize; i++) {
                QryIop q = this.getArg(i);
                if (!q.locIteratorHasMatch()) {
                    break;
                }
                //for the first term, only record its location
                if (i == 0) {
                    locMin = q.locIteratorGetMatch();
                    locMax = q.locIteratorGetMatch();
                    minQry = q;
                    continue;
                }
                int pos1 = q.locIteratorGetMatch();
                locMax = Math.max(locMax, pos1);
                if (pos1 < locMin) {
                    minQry = q;
                    locMin = pos1;
                }
                //the for loop reach the last term, now make judgement
                if (i == argSize - 1) {
                    if (locMax - locMin >= distance) {
                        //  advance loc of the min term, repeat the for loop by setting i=-1
                        locMax = -1;
                        locMin = -1;
                        minQry.locIteratorAdvance();
                        minQry = firstQry;
                        i = -1;
                    } else {
                        //  record this location just found
                        postings.add(locMax);
                        locMax = locMin = -1;
                        // find next match in the same doc, advance all terms' loc
                        // not just the first!!
                        i = -1;
                        int j;
                        for (j = 0; j < argSize; j++) {
                            Qry qry = this.args.get(j);
                            ((QryIop) qry).locIteratorAdvance();
                            if (!((QryIop) qry).locIteratorHasMatch()) break;
                        }
                        //if any of the term's location in this doc is exhausted,
                        //move on to next doc
                        if (j != argSize) break;
                    }
                }
            }
            if (postings.size() > 0) {
                //only when postings is not empty can we add those locations,
                // or we will have a lot false results(contain all terms but not near enough)!!
                this.invertedList.appendPosting(firstQry.docIteratorGetMatch(), postings);
                postings.clear();
            }
            //move on to next doc
            if (firstQry.docIteratorHasMatch(null)) {
                firstQry.docIteratorAdvancePast(firstQry.docIteratorGetMatch());
            }
        }
    }
}
