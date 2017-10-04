public class RetrievalModelBM25 extends RetrievalModel {
    private double b;
    private double k1;
    private double k3;

    public RetrievalModelBM25(double b, double k1, double k3) {
        this.b = b;
        this.k1 = k1;
        this.k3 = k3;
    }

    //default operator for unstructured query is #sum
    @Override
    public String defaultQrySopName() {
        return new String("#sum");
    }

    public void setK1(double k1) {
        this.k1 = k1;
    }

    public void setB(double b) {
        this.b = b;
    }

    public void setK3(double k3) { this.k3 = k3; }

    public double getK1() {
        return this.k1;
    }

    public double getB() {
        return this.b;
    }

    public double getK3() {
        return this.k3;
    }
}

