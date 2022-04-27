package ranking;

import org.uma.jmetal.solution.Solution;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.comparator.DominanceComparator;
import org.uma.jmetal.util.solutionattribute.Ranking;
import org.uma.jmetal.util.solutionattribute.impl.GenericSolutionAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BestOrderSortRanking<S extends Solution<?>> extends GenericSolutionAttribute<S, Integer> implements Ranking<S> {

    private Comparator<S> dominanceComparator;
    private List<ArrayList<S>> rankedSubPopulations;
    private boolean minimising;

    /**
     * Constructor
     * @param comparator The dominance comparator to be used
     */
    public BestOrderSortRanking(Comparator<S> comparator, boolean minimising){
        this.dominanceComparator = comparator;
        this.minimising = minimising;
        rankedSubPopulations = new ArrayList<>();
    }

    /**
     * Default constructor.
     */
    public BestOrderSortRanking(){
        this.dominanceComparator = new DominanceComparator<>();
        rankedSubPopulations = new ArrayList<>();
        this.minimising = false;
    }

    public BestOrderSortRanking(Object id) {
        super(id) ;
        rankedSubPopulations = new ArrayList<>();
        this.minimising = false;
    }

    @Override
    public Ranking<S> computeRanking(List<S> solutionList) {

        // Initialisation
        int numObjectives = solutionList.get(0).getNumberOfObjectives();
        int popSize = solutionList.size();

       ArrayList<S>[][] L = new ArrayList[popSize][numObjectives];
       for(int i = 0; i < popSize; i++){
           for(int j = 0; j < numObjectives; j++){
               L[i][j] = new ArrayList<>();
           }
       }

       ArrayList<ArrayList<Integer>> C = new ArrayList<>();
       for(int i = 0; i < popSize; i++){
           C.add(new ArrayList<>());
           for(int j = 0; j < numObjectives; j++){
               C.get(i).add(j);
           }
       }

       ArrayList<Boolean> isRanked = new ArrayList<>();
       for(int i = 0; i < popSize; i++) isRanked.add(false);
       int sc = 0;
       int rc = 1;
       int[] rank = new int[popSize];

       // Rank the individuals for each objective
        // Q store the indexes of the elements in solutionList
        ArrayList<ArrayList<Integer>> Q = new ArrayList<>();
        for(int i = 0; i < numObjectives; i++){
            final int obj = i;
            Q.add(new ArrayList<>());
            Comparator<Integer> objComparator = Comparator.comparingDouble( (Integer x) -> solutionList.get(x).getObjective(obj));
            if(!minimising) objComparator = objComparator.reversed();

            ArrayList<Integer> a = new ArrayList<>();
            for(int j = 0; j < popSize; j++){
                a.add(j);
            }

            Collections.sort(a,objComparator);
            Q.get(i).addAll(a);
        }

        for(int i = 0; i < popSize; i++)
            System.out.println(Q.get(0).get(i) + " -> " + solutionList.get(Q.get(0).get(i)).getObjective(0) + "   " + Q.get(1).get(i) + " -> " + solutionList.get(Q.get(1).get(i)).getObjective(1));


        // MAIN LOOP
        for(int i = 0; i < popSize; i++){
            for(int j = 0; j < numObjectives; j++){
                int s = Q.get(j).get(i);
                final int obj = j;
                C.get(s).removeIf(x -> x == obj);
                if(isRanked.get(s)){
                    L[rank[s]][j].add(solutionList.get(s));
                } else {
                    // FIND RANK function
                    boolean done = false;
                    for(int k = 0; k < rc; k++){
                        boolean check = false;
                        for(S t : L[k][j]){
                            boolean flag = dominationCheck(solutionList.get(s), t, C.get(solutionList.indexOf(t)));
                            if(flag){ // t dominates s
                                check = true;
                                break;
                            }
                        }

                        if(!check){
                            rank[s] = k;
                            done = true;
                            L[rank[s]][j].add(solutionList.get(s));
                            break;
                        }
                    }

                    if(!done){
                        rc++;
                        rank[s] = rc;
                        L[rank[s]][j].add(solutionList.get(s));
                    }
                    isRanked.set(s, true);
                    sc++;
                }
            }
            if(sc == popSize) break;
        }

        // Falta averiguar como pasar de los Ls a la lista de frentes
        return this;
    }

    @Override
    public List<S> getSubfront(int rank) {
        if (rank >= rankedSubPopulations.size()) {
            throw new JMetalException("Invalid rank: " + rank + ". Max rank = " + (rankedSubPopulations.size() -1)) ;
        }
        return rankedSubPopulations.get(rank);
    }

    @Override
    public int getNumberOfSubfronts() {
        return rankedSubPopulations.size();
    }


    /**
     * It returns wheter t dominates s.
     * @param s
     * @param t
     * @param Ct
     * @return
     */
    private boolean dominationCheck(S s, S t, ArrayList<Integer> Ct){
        for(Integer q : Ct){
            if(s.getObjective(q) > t.getObjective(q)){
                return false;
            }
        }
        return true;
    }
}
