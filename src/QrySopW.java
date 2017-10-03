import java.util.ArrayList;

abstract class QrySopW extends QrySop {

    ArrayList<Double> weights = new ArrayList<>();

    double weightSum = 0;

    public void appendWeight(double weight) {
        if (this.weights.size() != this.args.size()) {
            throw new IllegalArgumentException("Numbers of weights and of args didn't match.");
        }
        weightSum += weight;
        this.weights.add(weight);
    }
}
