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
 *
 * @author angel
 */
@Deprecated
public final class GMean extends QualityMeasure {

    public GMean() {
        super.name = "Geometric Mean";
        super.short_name = "G-Mean";
        super.value = 0.0;
    }

    @Override
    protected double calculate(ContingencyTable t) {
        table = t;
        try {
            TPR tpr = new TPR();
            tpr.calculate(t);
            tpr.validate();

            TNR tnr = new TNR();
            tnr.calculate(t);
            tnr.validate();
            
            setValue(Math.sqrt(tpr.value * tnr.value));

        } catch (InvalidRangeInMeasureException ex) {
           ex.showAndExit(this);
        }
        return value;

    }

    @Override
    protected double inverse() {
        return 1.0 - value;
    }

    @Override
    public void validate() throws InvalidRangeInMeasureException {
        if (!(isGreaterTharOrEqualZero(value) && value <= 1.0) || Double.isNaN(value)) {
            throw new InvalidRangeInMeasureException(this);
        }
    }


    @Override
    public QualityMeasure clone() {
        GMean a = new GMean();
        a.name = this.name;
        a.setValue(this.value);

        return a;
    }



    @Override
    public int compareTo(QualityMeasure o) {
        try {
            if (!(o instanceof GMean)) {
                throw new InvalidMeasureComparisonException(this, o);
            }

            return Double.compare(this.value, o.value);
        } catch (InvalidMeasureComparisonException ex) {
            ex.showAndExit(this);
        }
        return 0;    }

}
