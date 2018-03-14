import java.util.ArrayList;

/**
 *  The root class of all "weighted" query operators WAND and WSUM in Indri retrieval model
 *  to determine whether a query matches a document and to calculate a
 *  score for the document.  This class extends QrySop. It has two more fields than QrySop,
 *  First, it has an arraylist to store weights of each query term,
 *  Second, it has a double float as the sum of weights.
 */

abstract class QrySopW extends QrySop {

    ArrayList<Double> weights = new ArrayList<>();

    double weightSum = 0;

    /**
     * This method is called when the query string is being parsed, as a term is added, the
     * corresponding weight is added too.
     * @param weight weight for the term just added.
     */
    public void appendWeight(double weight) {
        if (this.weights.size() != this.args.size()) {
            throw new IllegalArgumentException("Numbers of weights and of args didn't match.");
        }
        this.weightSum += weight;
        this.weights.add(weight);
    }
}
