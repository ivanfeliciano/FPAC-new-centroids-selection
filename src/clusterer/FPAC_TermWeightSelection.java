package clusterer;

import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

public class FPAC_TermWeightSelection extends FPACNU_SetCover {
    public FPAC_TermWeightSelection(String propFile) throws Exception {
        super(propFile);
    }
    @Override
    public void recomputeCentroids() throws IOException {
        System.out.println("Recalculando centroides");
        int bestDocId = 0;
        ArrayList<ArrayList<Integer>> oldCentroids = getOldCentroids();
        // Por cada cluster se crea un conjunto donde
        // se va a guardar el vocabulario del cluster
        // y se limpian las estructuras que guardan los centroides
        // de cada cluster
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            docsInEachCluster.add(new ArrayList<>());
            DynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
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
        for (int cluster = 0; cluster < K; cluster++) {
            double puntajeLocal = 0.0f;
            double maxPuntaje = 0.0f;

            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                try {
                    for (TermStats st : TermVector.extractAllDocTerms(reader, docId, contentFieldName, lambda).termStatsList) {
                        puntajeLocal += st.wt;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (puntajeLocal > maxPuntaje) {
                    bestDocId = docId;
                    maxPuntaje = puntajeLocal;
                }
            }
            System.out.println(bestDocId + " con puntaje " + maxPuntaje);
            DynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, bestDocId, prop, cluster + 1));
            try {
                dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, bestDocId, contentFieldName, lambda));
                DynamicCentroids.get(cluster).get(0).getRelatedDocs(numDocs / K);
            } catch (Exception e) {
                e.printStackTrace();
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
