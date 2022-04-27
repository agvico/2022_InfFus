package genetic;


import org.uma.jmetal.algorithm.impl.AbstractGeneticAlgorithm;
import org.uma.jmetal.operator.CrossoverOperator;
import org.uma.jmetal.operator.MutationOperator;
import org.uma.jmetal.operator.SelectionOperator;
import org.uma.jmetal.problem.Problem;

import java.util.List;

public abstract class MyAbstractGeneticAlgorithm<S, Result> extends AbstractGeneticAlgorithm<S,Result> {

    public MyAbstractGeneticAlgorithm(Problem<S> problem,
                                      SelectionOperator<List<S>,S> selectionOperator,
                                      CrossoverOperator<S> crossoverOperator,
                                      MutationOperator<S> mutationOperator){
        super(problem);
        this.crossoverOperator = crossoverOperator;
        this.mutationOperator = mutationOperator;
        this.selectionOperator = selectionOperator;
    }

}
