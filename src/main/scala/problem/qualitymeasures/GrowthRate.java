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
 * @author Ángel Miguel García Vico (agvico@ujaen.es)
 * @since JDK 8.0
 */
@Deprecated
public final class GrowthRate extends QualityMeasure {
    
    public GrowthRate(){
        this.name = "Growth Rate";
        this.short_name = "GR";
        this.value = 0.0;
    }

    @Override
    public double calculate(ContingencyTable t) {
        try {
            TPR tpr = new TPR();
            FPR fpr = new FPR();

            tpr.calculate(t);
            fpr.calculate(t);
            tpr.validate();
            fpr.validate();

            if (fpr.getValue() == 0 && tpr.getValue() == 0) {
                setValue(0.0);
            } else if (tpr.getValue() != 0 && fpr.getValue() == 0) {
                setValue(Double.POSITIVE_INFINITY);
            } else {
                setValue(tpr.getValue() / fpr.getValue());
            }

        } catch (InvalidRangeInMeasureException ex) {
            ex.showAndExit(this);
        }
        return value;
    }

    @Override
    protected double inverse() {
        if(Double.isInfinite(value)){
            return 0;
        } else {
            return 1.0 / value;
        }
    }

    @Override
    public void validate() throws InvalidRangeInMeasureException {
        if (value < 0 - THRESHOLD) {
            throw new InvalidRangeInMeasureException(this);
        }
    }

    @Override
    public QualityMeasure clone() {
        GrowthRate a = new GrowthRate();
        a.name = this.name;
        a.setValue(this.value);

        return a;
    }




    @Override
    public int compareTo(QualityMeasure o) {
        try {
            if (!(o instanceof GrowthRate)) {
                throw new InvalidMeasureComparisonException(this, o);
            }

            return Double.compare(this.value, o.value);
        } catch (InvalidMeasureComparisonException ex) {
            ex.showAndExit(this);
        }
        return 0;
    }

}
