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
public final class FPR extends QualityMeasure {

    public FPR() {
        super.name = "False Positive Rate";
        super.short_name = "FPR";
        super.value = 1.0;
    }

    @Override
    protected double calculate(ContingencyTable t) {
        table = t;
        if (t.getFp() + t.getTn() == 0 || t.getTp() == 0) {
            setValue(1.0);
        } else {
            setValue((double) t.getFp() / (double) (t.getFp() + t.getTn()));
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
        FPR a = new FPR();
        a.setValue(this.value);

        return a;
    }


    @Override
    public int compareTo(QualityMeasure o) {
        
        // THIS MEASURES MUST BE MINIMISED !!!!
        try {
            if (!(o instanceof FPR)) {
                throw new InvalidMeasureComparisonException(this, o);
            }
            return -1 *Double.compare(o.value, this.value);

        } catch (InvalidMeasureComparisonException ex) {
            ex.showAndExit(this);
        }
        return 0;
    }

}
