package problem.qualitymeasures;

import problem.qualitymeasures.exceptions.InvalidMeasureComparisonException;
import problem.qualitymeasures.exceptions.InvalidRangeInMeasureException;

@Deprecated
public class OddsRatio extends QualityMeasure {


    public OddsRatio() {
        super.name = "Odds Ratio";
        super.short_name = "Odds";
        super.value = 0.0;
    }

    @Override
    protected double calculate(ContingencyTable t) {

        long num = (long) t.getTp() * (long) t.getTn();
        long den = (long) t.getFp() * (long) t.getFn();

        if (den == 0)
            value = 0;
        else
            value = (double) num / den;

        return value;
    }

    @Override
    protected double inverse() {
        return 1 / value;
    }

    @Override
    public void validate() throws InvalidRangeInMeasureException {
        if (value < 0.0 - THRESHOLD || Double.isNaN(value)) {
            throw new InvalidRangeInMeasureException(this);
        }
    }

    @Override
    public QualityMeasure clone() {
        OddsRatio a = new OddsRatio();
        a.name = this.name;
        a.setValue(this.value);

        return a;
    }

    @Override
    public int compareTo(QualityMeasure o) {
        try {
            if (!(o instanceof OddsRatio)) {
                throw new InvalidMeasureComparisonException(this, o);
            }

            return Double.compare(this.value, o.value);
        } catch (InvalidMeasureComparisonException ex) {
            ex.showAndExit(this);
        }
        return 0;
    }
}
