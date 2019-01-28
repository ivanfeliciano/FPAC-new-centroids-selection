/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ivan
 */
public class MainExperiments {
    public static void main(String[] args) {
        try {
            PrintStream out;
            System.out.println("Ejecución usando 20 Newsgroups");
            int numberOfClusters [] = {20, 40};

            for (int k : numberOfClusters) {
                String propFileName = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/20News_init_" + k + ".properties";
                LuceneClusterer algorithms [];
                algorithms = new LuceneClusterer[5];
                algorithms[0] = new FastKMedoidsClusterer(propFileName);
                algorithms[1] = new FastKMedoidsClusterer_TrueCentroid(propFileName);
                algorithms[2] = new FPACNU_NotSimilarHeuristics(propFileName);
                algorithms[3] = new FPACNU_SetCover(propFileName, 25);
                algorithms[4] = new FPACNU_SetCover(propFileName, 50);
//                algorithms[5] = new FPACNU_SetCover(propFileName, 75);
//                algorithms[2] = new FPACNU_SetCover(propFileName);
                for (int algoIterator = 0; algoIterator < 5; algoIterator++) {
                    out = new PrintStream(new FileOutputStream("./logs/logs_20News_" + k + "_algorithm" + algoIterator + ".txt"));
                    System.setOut(out);

                    try {
                        algorithms[algoIterator].cluster();
                        ClusterEvaluator ceval = new ClusterEvaluator(propFileName);
                        System.out.println("Acc, Prec, recall, fscore: ");
                        ceval.showNewMeasures();
                        System.out.print("\nPurity: " + ceval.computePurity());
                        System.out.print("\nNMI: " + ceval.computeNMI());
                        System.out.print("\nRI: " + ceval.computeRandIndex());
                        System.out.println();
                    }catch (Exception e){
                        System.out.println(e);
                    }

                }
                break;
            }
                
        } catch (Exception ex) {
            Logger.getLogger(MainExperiments.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
