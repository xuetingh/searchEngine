
import java.io.*;
import java.util.ArrayList;


public class QryIopNear extends QryIop {
    
    private int distance;
    
    public QryIopNear(int dist) {
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
        ArrayList<Integer> postings = new ArrayList<Integer>();
        if (argSize < 2) {
            return;
        }
        //  Use docIteratorHasMatchAll method in Qry to get the doc that has all terms in it
        while(this.docIteratorHasMatchAll(null)){
            //  location of previous term
            int locRec = -1;
            //  this loop checks if the adjacent two terms meet the distance requirement
            //  when any of distance exceeds parameter distance or for any term there is
            //  no next locations in this doc, break for loop to move on to next doc
            for (int i = 0; i < argSize; i++) {
                QryIop q = this.getArg(i);
                if (!q.locIteratorHasMatch()) {
                    break;
                }
                //for the first term, only record its location
                if (locRec == -1) {
                    locRec = q.locIteratorGetMatch();
                    continue;
                }
                // for other terms, the loc should past the first term's
                q.locIteratorAdvancePast(locRec);
                if(!(q.locIteratorHasMatch())){
                    break;
                }
                int pos1 = q.locIteratorGetMatch();
                if (pos1 - locRec > distance) {
                    //  advance loc of the first term, repeat the for loop by setting i=-1
                    locRec = -1;
                    firstQry.locIteratorAdvance();
                    i = -1;
                    continue;
                }
                locRec = q.locIteratorGetMatch();
                if (i == argSize - 1) {
                    //  record this location just found
                    postings.add(locRec);
                    locRec = -1;
                    // find next match in the same doc, advance all terms' loc
                    // not just the first!!
                    i = -1;
                    int j = 0;
                    for(j = 0; j< argSize; j++) {
                        Qry qry = this.args.get(j);
                        ((QryIop) qry).locIteratorAdvance();
                        if (!((QryIop) qry).locIteratorHasMatch()) break;
                    }
                    //if any of the term's location in this doc is exhausted, 
                    //move on to next doc
                    if (j != argSize) break;
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
