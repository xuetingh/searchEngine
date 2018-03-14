public class RetrievalModelIndri extends RetrievalModel {
    private int mu;
    private double lambda;

    //default operator for Indri is #AND
    @Override
    public String defaultQrySopName() {
        return new String("#and");
    }

    public RetrievalModelIndri(int mu, double lambda) {
        this.mu = mu;
        this.lambda = lambda;
    }

//    public void setMu(int mu) {
//        this.mu = mu;
//    }
//
//    public void setLambda(double lambda) {
//        this.lambda = lambda;
//    }

    public double getMu() {
        return this.mu;
    }

    public double getLambda() {
        return this.lambda;
    }
}