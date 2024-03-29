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
package problem.qualitymeasures.exceptions;

import problem.qualitymeasures.QualityMeasure;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author agvico
 */
@Deprecated
public class InvalidRangeInMeasureException extends Exception {

    public InvalidRangeInMeasureException(QualityMeasure q) {
        super("Invalid Range in measure \"" + q.getShort_name() + "\": " + q.getValue() + " -> Contingency Table: " + q.getTable().toString());
    }

    /**
     * It shows the message in the estandard error and the stack trace and exit
     * the program with error code = 2.
     * 
     * @param clas The class where the error ocurred, in order to be shown in the stack trace
     */
    public void showAndExit(Object clas) {
        Logger.getLogger(clas.getClass().getName()).log(Level.SEVERE, null, this);
        System.err.println(super.getMessage());
        System.exit(2);
    }
}
