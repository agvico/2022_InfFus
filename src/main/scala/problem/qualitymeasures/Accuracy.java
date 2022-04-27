/* 
 * The MIT License
 *
 * Copyright 2018 Ángel Miguel García Vico.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package problem.qualitymeasures;


import problem.qualitymeasures.exceptions.InvalidMeasureComparisonException;
import problem.qualitymeasures.exceptions.InvalidRangeInMeasureException;

/**
 * Accuracy. It measures the precision of the model
 *
 * @author Angel Miguel Garcia-Vico (agvico@ujaen.es)
 */
@Deprecated
public final class Accuracy extends QualityMeasure {

    public Accuracy() {
        super();
        super.name = "Accuracy";
        super.short_name = "Acc";
        super.value = 0.0;
    }

    @Override
    protected double calculate(ContingencyTable t) {
        table = t;
        if (t.getTotalExamples() != 0) {
            setValue((double) (t.getTp() + t.getTn()) / (double) t.getTotalExamples());
        } else {
            value = 0;
        }
        return value;
    }

    @Override
    protected double inverse() {
        return 1.0 - value;
    }

    @Override
    public void validate() throws InvalidRangeInMeasureException {
        if (!(value <= 1.0 && isGreaterTharOrEqualZero(value)) || Double.isNaN(value)) {
            throw new InvalidRangeInMeasureException(this);
        }
    }


    @Override
    public QualityMeasure clone() {
        Accuracy a = new Accuracy();
        a.name = this.name;

        return a;
    }


    @Override
    public int compareTo(QualityMeasure o) {
        try {
            if (!(o instanceof Accuracy)) {
                throw new InvalidMeasureComparisonException(this, o);
            }

            return Double.compare(this.value, o.value);
        } catch (InvalidMeasureComparisonException ex) {
            ex.showAndExit(this);
        }
        return 0;
    }

}
