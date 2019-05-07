/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author ivan
 */
public class MainExperiments {
    public static void main(String[] args) {
        try {

            //Para el archivo HTML
            //////////////////////////////////////////////
            FileWriter fileWriter = new FileWriter("./experimentos/html/cade.html");
            PrintWriter printWriter = new PrintWriter(fileWriter);
            String initialHTMLString = "<!DOCTYPE html><html><head><style>table, th, td {  border: 1px solid black;  border-collapse: collapse;}th, td {  padding: 15px;  text-align: left;}</style></head><body>";
            String endHTMLString = "</body></html>";

            printWriter.printf(initialHTMLString);
            ///////////////////////////////////////////////


            PrintStream out;
            String properties_paths[] = {"/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden1.properties",
                    "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden2.properties",
                    "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden3.properties"};

            List<List<Integer>> initialCentroids[] = new ArrayList[3];
            initialCentroids[0] = new ArrayList<>();
            initialCentroids[1] = new ArrayList<>();
            initialCentroids[2] = new ArrayList<>();
//|Orden 0
            initialCentroids[0].add(Arrays.asList(4279, 13719, 40849, 11170, 11565, 19486, 28004, 35480, 39210, 2832, 4859, 37632));
            initialCentroids[0].add(Arrays.asList(11896, 25468, 14095, 5278, 39772, 499, 20728, 40060, 19767, 8145, 10474, 14829));
            initialCentroids[0].add(Arrays.asList(23320, 17779, 20747, 36142, 32819, 38246, 3637, 3162, 17903, 29863, 15111, 39880));
            initialCentroids[0].add(Arrays.asList(40447, 35439, 14052, 5165, 19770, 5230, 15574, 24166, 2667, 12590, 24011, 2477));
            initialCentroids[0].add(Arrays.asList(884, 35933, 38177, 7132, 20524, 1850, 34418, 38773, 4217, 1335, 21081, 19791));
//Orden 1
            initialCentroids[1].add(Arrays.asList(28407, 9670, 2980, 35650, 35085, 35421, 32323, 16939, 21811, 28228, 11393, 19526));
            initialCentroids[1].add(Arrays.asList(32957, 2750, 28479, 31903, 13310, 3991, 34844, 31166, 20724, 32217, 30129, 17323));
            initialCentroids[1].add(Arrays.asList(36787, 12405, 35523, 28088, 37161, 33746, 37090, 10379, 13709, 18909, 11834, 3685));
            initialCentroids[1].add(Arrays.asList(815, 36615, 34282, 29955, 24873, 33251, 27231, 5814, 25308, 16487, 28005, 17444));
            initialCentroids[1].add(Arrays.asList(14636, 2025, 22511, 29710, 30366, 30391, 16168, 844, 32724, 12261, 3034, 7351));
//Orden 2
            initialCentroids[2].add(Arrays.asList(12163, 28417, 9022, 27660, 39675, 22023, 10025, 11224, 7642, 21519, 24319, 17604));
            initialCentroids[2].add(Arrays.asList(1960, 32888, 9252, 28358, 40169, 7174, 1114, 31642, 30281, 40747, 22056, 15457));
            initialCentroids[2].add(Arrays.asList(11488, 2828, 2791, 24612, 21263, 38182, 16162, 15633, 39944, 31327, 27005, 23799));
            initialCentroids[2].add(Arrays.asList(15335, 1047, 33546, 24815, 15407, 14367, 15673, 18304, 20770, 19679, 15319, 29794));
            initialCentroids[2].add(Arrays.asList(10888, 25907, 33236, 33025, 8911, 13869, 22, 32412, 22353, 14173, 17994, 8961));

            for (int i = 0; i < properties_paths.length; i++) {
                printWriter.printf("<h2>Order %d</h2>", i + 1);
                for (int seed_idx = 0; seed_idx < initialCentroids[i].size(); seed_idx++) {
                    printWriter.printf("<h2>Seed %d</h2>", seed_idx + 1);
                    LuceneClusterer algorithms [];
                    algorithms = new LuceneClusterer[3];;
                    algorithms[0] = new FastKMedoidsClusterer(properties_paths[i], initialCentroids[i].get(seed_idx));
                    algorithms[1] = new FastKMedoidsClusterer_TrueCentroid(properties_paths[i], initialCentroids[i].get(seed_idx));
                    algorithms[2] = new FPAC_TC_with_L_centroids(properties_paths[i], initialCentroids[i].get(seed_idx));
                    printWriter.printf("<table>");
                    printWriter.printf("<tr><th>Algoritmo</th><th>Tiempo</th><th>Iteraciones</th><th>Docs asignados aleatoriamente</th><th>RI</th><th>Recall </th><th>Precision</th><th>FScore</th><th>Purity</th><th>NMI</th></tr>");
                    printWriter.printf("<tr>");
                    for (int algoIterator = 0; algoIterator < algorithms.length; algoIterator++) {
                        out = new PrintStream(new FileOutputStream("./experimentos/tiempos/cade/" + algorithms[algoIterator].getAlgoName() + "_order_" + i +"_seed_"+ seed_idx + ".txt"));
                        System.setOut(out);
                        LuceneClusterer fkmc = algorithms[algoIterator];
                        ArrayList<String> resultsToBePrinted = fkmc.cluster();

                        ClusterEvaluator ceval = new ClusterEvaluator(properties_paths[i]);
                        ArrayList<String> measures = ceval.showNewMeasures();

                        printWriter.printf("<tr>");
                        printWriter.printf("<td>%s</td>", algorithms[algoIterator].getAlgoName());
                        for (String s:resultsToBePrinted) {
                            printWriter.printf("<td>%s</td>", s);
                        }
                        for (String s: measures) {
                            printWriter.printf("<td>%s</td>", s);
                        }
                        printWriter.printf("</tr>");
                        algorithms[algoIterator] = null;
                    }
                    printWriter.printf("</tr>");
                    printWriter.printf("</table>");
//                    break;
                }
//                break;
            }
            printWriter.printf("</body></html>");
            printWriter.close();
        } catch (Exception ex) {
            Logger.getLogger(MainExperiments.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
