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
            FileWriter fileWriter = new FileWriter("./experimentos/html/hitech.html");
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
//Orden 0
            initialCentroids[0].add(Arrays.asList(337, 665, 723, 917, 871, 1106));
            initialCentroids[0].add(Arrays.asList(1816, 128, 363, 252, 1326, 2122));
            initialCentroids[0].add(Arrays.asList(2212, 802, 690, 1001, 1674, 514));
            initialCentroids[0].add(Arrays.asList(1695, 1509, 1523, 43, 849, 839));
            initialCentroids[0].add(Arrays.asList(1599, 1935, 1604, 2173, 1868, 1587));
//Orden 1
            initialCentroids[1].add(Arrays.asList(1550, 2115, 1638, 844, 2256, 1785));
            initialCentroids[1].add(Arrays.asList(944, 1858, 2032, 591, 1769, 1644));
            initialCentroids[1].add(Arrays.asList(25, 1039, 1622, 1346, 1852, 1413));
            initialCentroids[1].add(Arrays.asList(461, 1222, 2119, 2036, 2260, 1987));
            initialCentroids[1].add(Arrays.asList(730, 2078, 1099, 334, 1364, 988));
//Orden 2
            initialCentroids[2].add(Arrays.asList(908, 658, 2028, 1273, 1943, 1435));
            initialCentroids[2].add(Arrays.asList(600, 830, 1357, 2271, 1347, 1097));
            initialCentroids[2].add(Arrays.asList(521, 2272, 1600, 2248, 1912, 521));
            initialCentroids[2].add(Arrays.asList(1739, 1286, 1673, 1220, 1887, 1079));
            initialCentroids[2].add(Arrays.asList(36, 2024, 258, 461, 219, 1581));



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
                        out = new PrintStream(new FileOutputStream("./experimentos/tiempos/health2/" + algorithms[algoIterator].getAlgoName() + "_order_" + i +"_seed_"+ seed_idx + ".txt"));
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
