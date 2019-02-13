package clusterer;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toMap;

public class FPAC_TermWeightSelection extends FPACNU_SetCover {
    public FPAC_TermWeightSelection(String propFile) throws Exception {
        super(propFile);
    }
    @Override
    public void recomputeCentroids() throws IOException {
        System.out.println("Recalculando centroides");
        int bestDocId = 0;
        int numOfDocsCapacity = 10;
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        RelatedDocumentsRetriever rde;
        TermVector termVectorAux;
        for (int i = 0; i < K; i++) {
            rde = DynamicCentroids.get(i).get(0);
            termVectorAux = dynamicTermVectorCentroids.get(i).get(0);
            docsInEachCluster.add(new ArrayList<>());
            DynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
            try {
                DynamicCentroids.get(i).add(rde);
                dynamicTermVectorCentroids.get(i).add(termVectorAux);
                DynamicCentroids.get(i).get(0).getRelatedDocs(numDocs / K);
            } catch (Exception e) {
                e.printStackTrace();
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
        HashMap<Integer, Float> puntajes = new HashMap<>();
        for (int cluster = 0; cluster < K; cluster++) {
            double puntajeLocal = 0.0f;
            double maxPuntaje = 0.0f;

            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                try {
                    for (TermStats st : TermVector.extractAllDocTermsTF(reader, docId, contentFieldName, lambda).termStatsList) {
                        puntajeLocal += st.wt;
                    puntajes.put(docId, (float) puntajeLocal);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Map<Integer, Float> sorted = puntajes
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                    LinkedHashMap::new));

            int counter = 1;
            for (Map.Entry<Integer, Float> entry : sorted.entrySet()) {
                if (counter == numOfDocsCapacity) break;
                bestDocId = entry.getKey();
                maxPuntaje = entry.getValue();
                System.out.println(bestDocId + " con puntaje " + maxPuntaje);
                DynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, bestDocId, prop, cluster + 1));
                try {
                    dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, bestDocId, contentFieldName, lambda));
                    DynamicCentroids.get(cluster).get(counter++).getRelatedDocs(numDocs / K);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        }

    }
    public static void main(String[] args) {
        String propFile = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";
        try {
            LuceneClusterer fkmc = new FPAC_TermWeightSelection(propFile);
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator(propFile);
            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
