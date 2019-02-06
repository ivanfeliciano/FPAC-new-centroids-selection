/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

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
            
            String inputPropsFilePath = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";

            LuceneClusterer algorithms [];
            algorithms = new LuceneClusterer[7];;
            algorithms[0] = new FastKMedoidsClusterer(inputPropsFilePath);
            algorithms[1] = new FastKMedoidsClusterer_TrueCentroid(inputPropsFilePath);
            algorithms[2] = new FPACNU_NotSimilarHeuristics(inputPropsFilePath);
            algorithms[3] = new FPACNU_SetCover(inputPropsFilePath, 20);
            algorithms[4] = new FPACNU_SetCover(inputPropsFilePath, 50);
            algorithms[5] = new FPACNU_SetCover(inputPropsFilePath, 80);
            algorithms[6] = new FPACNU_SetCover(inputPropsFilePath);

            for (int algoIterator = 0; algoIterator < algorithms.length; algoIterator++) {
                out = new PrintStream(new FileOutputStream("./experimentos/logs_sentiment140_algorithm" + algoIterator + ".txt"));
                System.setOut(out);
                LuceneClusterer fkmc = algorithms[algoIterator];
                fkmc.cluster();
                ClusterEvaluator ceval = new ClusterEvaluator(inputPropsFilePath);
                ceval.showNewMeasures();
                algorithms[algoIterator] = null;
            }

                
        } catch (Exception ex) {
            Logger.getLogger(MainExperiments.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
