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

import org.uma.jmetal.solution.BinarySolution;
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute;
import problem.qualitymeasures.exceptions.InvalidRangeInMeasureException;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Abstract class that represents an statistical quality measure
 *
 * @author Angel Miguel Garcia Vico <agvico at ujaen.es>
 */
@Deprecated
public abstract class QualityMeasure extends GenericSolutionAttribute<BinarySolution, Double> implements Cloneable, Serializable, Comparable<QualityMeasure> {

    /**
     * Default constructor, it does nothing
     */
    public QualityMeasure(){

    }

    /**
     * Constructor that calculates its value from a contingency table.
     * @param table The contingency table
     */
    public QualityMeasure(ContingencyTable table){
        calculate(table);
    }


    /**
     * It checks whether the measure should be inversed or not (by default normal values are returned)
     */
    protected static boolean inverse = false;

    /**
     *  It returns whether the quality measures are returned inversed or not
     * @return
     */
    public static boolean areInversed(){return inverse;};

    /**
     * It return the value that represents the minimum possible value. I.e., if maximising it is negative infinity, while positive infinity is returned when inverse (minimising)
     * @return
     */
    public static double minimumValue(){
        if(inverse) return Double.POSITIVE_INFINITY;
        return Double.NEGATIVE_INFINITY;
    }

    /**
     * Sets that all the extracted measures should return its
     * @param flag
     */
    public static void setMeasuresReversed(boolean flag){
        inverse = flag;
    }

    /**
     * Threshold to check if a value is greater than or equal zero.
     */
    protected double THRESHOLD = 1E-13;
    
    /**
     * @return the short_name
     */
    public String getShort_name() {
        return short_name;
    }

    /**
     * @return the table
     */
    public ContingencyTable getTable() {
        return table;
    }

    /**
     * The value of the quality measure
     */
    protected double value;

    /**
     * The name of the quality measure
     */
    protected String name;

    /**
     * The acronim of the quality measure
     */
    protected String short_name;

    /**
     * The contingencyTable from the values are calculated
     */
    protected ContingencyTable table;

    /**
     * It calculates the value of the given quality measure by means of the
     * given contingency table
     *
     * @param t
     * @return
     */
    protected abstract double calculate(ContingencyTable t);

    /**
     * It returns the inverse value of the measure.
     * @return the inverse value (or NaN otherwise)
     */
    protected abstract double inverse();

    /**
     * It returns the value of the quality measure according to the given contingency table `t`.
     *
     * If inverse is set, it returns the inverse value
     * @param t the contingency value
     * @return The value of the measure, or the inverse if inverse values are active.
     */
    public double calculateValue(ContingencyTable t){
        value = calculate(t);
        if(inverse){
            value = inverse();
        }
        return value;
    }

    /**
     * Return the last calculated value of the measure
     *
     * @return
     */
    public double getValue() {
        return value;
    }

    /**
     * It returns the inversed value of the measure
     * @return
     */
    public double getValueInversed(){
        return inverse();
    }
    /**
     * @param value the value to set
     */
    public void setValue(double value) {
        this.value = value;
    }

    /**
     * It checks that the value of the measure is within the domain of the
     * measure
     *
     * @return
     */
    public abstract void validate() throws InvalidRangeInMeasureException;

    /**
     * Returns a copy of this object
     *
     * @return
     */
    @Override
    public abstract QualityMeasure clone();

    @Override
    public String toString() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        DecimalFormat sixDecimals = new DecimalFormat("0.000000", symbols);

        return short_name + " = " + sixDecimals.format(value);
    }

    /**
     * Returns the full name of the quality measure
     *
     * @return
     */
    public String getName() {
        return this.name;
    }

    public String getShortName() {
        return short_name;
    }

    @Override
    public abstract int compareTo(QualityMeasure o);

    
    /**
     * Method to check whether a double is zero or not due to the error produced in the double precision.
     * 
     * @param value The value to check if it is 
     * @return 
     */
    public boolean isZero(double value) {
        return value >= -THRESHOLD && value <= THRESHOLD;
    }
    
      /**
     * Method to check whether a double is greater than or equal zero with an error threshold for values near zero
     * 
     * @param value The value to check if it is zero
     * @return 
     */
    public boolean isGreaterTharOrEqualZero(double value) {
        return value >= -THRESHOLD;
    }





}
