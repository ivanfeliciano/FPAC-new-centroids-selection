package clusterer;

import indexer.WMTIndexer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.out;

public class FPAC_TC_with_L_centroids extends FPACNU_SetCover {

    @Override
    public String getAlgoName() {
        return "FPAC_TC_with_L_centroids";
    }
    public FPAC_TC_with_L_centroids(String propFile) throws Exception {
        super(propFile);
    }
    public FPAC_TC_with_L_centroids(String propFile,  List<Integer> initCentroids) throws Exception {
        super(propFile, initCentroids);
    }
    public FPAC_TC_with_L_centroids(String propFile,  List<Integer> initCentroids, int numberOfCentroidsByGroupĹ) throws Exception {
        super(propFile, initCentroids);
        numberOfCentroidsByGroup = numberOfCentroidsByGroupĹ;
    }

    @Override
    public void recomputeCentroids() throws IOException {
//        numberOfCentroidsByGroup = 10;
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            docsInEachCluster.add(new ArrayList<>());
            DynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
            for (int j = 0; j < numberOfCentroidsByGroup; j++) {
                DynamicCentroids.get(i).add(new RelatedDocumentsRetriever(reader, 0, prop, i));
            }
        }
        int clusterId;
        for (int docId = 0; docId < numDocs; docId++) {
            try {
                clusterId = getClusterId(docId);
                if (clusterId == INITIAL_CLUSTER_ID) continue;
                docsInEachCluster.get(getClusterId(docId)).add(docId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        int tf;
        float wt;
        float idf;
        String termText;
        for (int cluster = 0; cluster < K; cluster++) {
            HashMap<String, Float> vocabularySetStats[] = new HashMap[numberOfCentroidsByGroup];
            for (int c = 0; c < vocabularySetStats.length; c++)
                vocabularySetStats[c] = new HashMap<>();
            out.println("Calculando centroides para el cluster " + cluster);

            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {

                List<TermStats> termStats = new ArrayList<>();
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                try {
                    tfvector = reader.getTermVector(docId, contentFieldName);
                    if (tfvector == null || tfvector.size() == 0) {
                        continue;
                    }
                    termsEnum = tfvector.iterator();
                    int docLen = 0;
                    while ((term = termsEnum.next()) != null) {
                        tf = (int) termsEnum.totalTermFreq();
                        termText = term.utf8ToString();
                        if (vocabularySetStats[clusterDocsIdx % numberOfCentroidsByGroup].containsKey(termText)) continue;
                        vocabularySetStats[clusterDocsIdx % numberOfCentroidsByGroup].put(termText,null);
                        termStats.add(new TermStats(termText, 1, reader));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (DynamicCentroids.get(cluster).get(clusterDocsIdx % numberOfCentroidsByGroup).termVectorForTrueCentroids == null ) {
                    DynamicCentroids.get(cluster).get(clusterDocsIdx % numberOfCentroidsByGroup).termVectorForTrueCentroids = new TermVector(termStats);
                    dynamicTermVectorCentroids.get(cluster).add(new TermVector(termStats));
                }
                else {
                    for (TermStats ts: termStats) {
                        DynamicCentroids.get(cluster).get(clusterDocsIdx % numberOfCentroidsByGroup).termVectorForTrueCentroids.add(ts);
                    }
                }
            }
        }
        for(int cluster = 0; cluster < K; cluster++) {
            for (int i = 0; i < numberOfCentroidsByGroup; i++) {
//                System.out.println(DynamicCentroids.get(cluster).get(i).termVectorForTrueCentroids.termStatsList.size());
//                for (TermStats ts : DynamicCentroids.get(cluster).get(i).termVectorForTrueCentroids.termStatsList)
//                    System.out.println(ts.term);
                try {
                    DynamicCentroids.get(cluster).get(i).getRelatedDocs(numDocs);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }
    public static void main(String[] args) {
        try {
            //Para el archivo HTML
            //////////////////////////////////////////////
            List<Integer> initialCentroids;
            initialCentroids = new ArrayList(Arrays.asList(1098, 3147, 1419, 2264));

            List<Integer> numberOfCentroids;
            numberOfCentroids = new ArrayList<>(Arrays.asList(5,10,15,20,25,30));
//            numberOfCentroids = new ArrayList<>(Arrays.asList(5,10,15));
            String dataName = "webkb";
            String properties_paths = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_webkd.properties";
            FileWriter fileWriter = new FileWriter("./experimentos/html/tc_l_ms/" + dataName + ".html");
            PrintWriter printWriter = new PrintWriter(fileWriter);
            String initialHTMLString = "<!DOCTYPE html><html><head><style>table, th, td {  border: 1px solid black;  border-collapse: collapse;}th, td {  padding: 15px;  text-align: left;}</style></head><body>";
            String endHTMLString = "</body></html>";

            printWriter.printf(initialHTMLString);
            ///////////////////////////////////////////////
            printWriter.printf("<h2>Data %s</h2>", dataName);
            LuceneClusterer algorithms;
            printWriter.printf("<table>");
            printWriter.printf("<tr><th>L</th><th>Tiempo</th><th>Iteraciones</th><th>Docs asignados aleatoriamente</th><th>RI</th><th>Recall </th><th>Precision</th><th>FScore</th><th>Purity</th><th>NMI</th></tr>");
            printWriter.printf("<tr>");
            for (int L: numberOfCentroids) {
                System.out.println("L = " + L);
                try {
                    algorithms = new FPAC_TC_with_L_centroids(properties_paths, initialCentroids, L);
                    LuceneClusterer fkmc = algorithms;
                    ArrayList<String> resultsToBePrinted = fkmc.cluster();
                    ClusterEvaluator ceval = new ClusterEvaluator(properties_paths);
                    ArrayList<String> measures = ceval.showNewMeasures();
                    printWriter.printf("<tr>");
                    printWriter.printf("<td>%d</td>", L);
                    for (String s:resultsToBePrinted) {
                        printWriter.printf("<td>%s</td>", s);
                    }
                    for (String s: measures) {
                        printWriter.printf("<td>%s</td>", s);
                    }
                    printWriter.printf("</tr>");
                    algorithms = null;
                } catch (Exception e) {
                    System.out.println("Error " + e.getMessage());
                }

            }
            printWriter.printf("</tr>");
            printWriter.printf("</table>");
            printWriter.printf("</body></html>");
            printWriter.close();

//
//
//            PrintStream out;
//            String properties_paths[] = {"/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden1.properties",
//                    "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden2.properties",
//                    "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden3.properties"};
//
//
//            LuceneClusterer fkmc = new FPAC_TC_with_L_centroids("/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties");
//            fkmc.cluster();
//            ClusterEvaluator ceval = new ClusterEvaluator("/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties");
//            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
