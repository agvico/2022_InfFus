package problem.qualitymeasures;

import problem.qualitymeasures.exceptions.InvalidMeasureComparisonException;
import problem.qualitymeasures.exceptions.InvalidRangeInMeasureException;

@Deprecated
public final class InverseFPR  extends QualityMeasure{

    public InverseFPR() {
        super.name = "Inverse False Positive Rate";
        super.short_name = "Inv_FPR";
        super.value = 0.0;
    }

    @Override
    protected double calculate(ContingencyTable t) {
        FPR val = new FPR();
        val.calculate(t);

            value = 1.0 - val.getValue();

        return value;
    }

    @Override
    protected double inverse() {
        return 1.0 - value;
    }

    @Override
    public void validate() throws InvalidRangeInMeasureException {
        if (!(isGreaterTharOrEqualZero(value) && value <= Double.POSITIVE_INFINITY) || Double.isNaN(value)) {
            throw new InvalidRangeInMeasureException(this);
        }
    }


    @Override
    public QualityMeasure clone() {
        InverseFPR a = new InverseFPR();
        a.setValue(this.value);

        return a;
    }


    @Override
    public int compareTo(QualityMeasure o) {

        // THIS MEASURES MUST BE MINIMISED !!!!
        try {
            if (!(o instanceof InverseFPR)) {
                throw new InvalidMeasureComparisonException(this, o);
            }
            return Double.compare(o.value, this.value);

        } catch (InvalidMeasureComparisonException ex) {
            ex.showAndExit(this);
        }
        return 0;
    }
}
