package clusterer;

import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;

public class FPACNU_NotSimilarHeuristics extends FPACNU_SetCover {
    public FPACNU_NotSimilarHeuristics(String propFile) throws Exception {
        super(propFile);
    }

    @Override
    void recomputeCentroids() throws Exception {

        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        ArrayList<RelatedDocumentsRetriever> initialLocalClusterCentroids = new ArrayList<>();
        ArrayList<ArrayList<Integer>> oldCentroids = getOldCentroids();
        int clusterId;
        for (int i = 0; i < K; i++) {
            docsInEachCluster.add(new ArrayList<>());
            initialLocalClusterCentroids.add(DynamicCentroids.get(i).get(0));
            DynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
        }


        for (int docId = 0; docId < numDocs; docId++) {
            clusterId = getClusterId(docId);
            if (clusterId == INITIAL_CLUSTER_ID) continue;
            docsInEachCluster.get(getClusterId(docId)).add(docId);
        }


        for (int cluster = 0; cluster < K; cluster++) {
            int counter = 0;
            HashMap <Integer, Byte> hasBeenSelected = new HashMap<>();
            System.out.println("Generando nuevos centroides del cluster " + cluster);
            int newInitialCentroidForThisCluster = initialLocalClusterCentroids.get(cluster).recomputeCentroidDoc(null);


            RelatedDocumentsRetriever initialRDE = new RelatedDocumentsRetriever(reader, newInitialCentroidForThisCluster, prop, cluster + 1);
            TopDocs relatedDocsInitialRDE = initialRDE.getRelatedDocs(docsInEachCluster.get(cluster).size());
            int numRelatedDocs = relatedDocsInitialRDE == null ? 0 : relatedDocsInitialRDE.scoreDocs.length;
            if (numRelatedDocs == 0){
                System.out.println("El centroide inicial del cluster no tiene TOP list");
                continue;
            }
            DynamicCentroids.get(cluster).add(initialRDE);
            dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, newInitialCentroidForThisCluster, contentFieldName, lambda));
//            System.out.println("Nuevo centroide " + newInitialCentroidForThisCluster + " para el cluster " + cluster);
            for (ScoreDoc sd : relatedDocsInitialRDE.scoreDocs) {
                hasBeenSelected.put(sd.doc, null);
            }
            counter++;


            for (int docId : docsInEachCluster.get(cluster)) {
                int numWanted = docsInEachCluster.size() - hasBeenSelected.size();
                numWanted = numWanted <= 0? numDocs / K : numWanted;
                if (!hasBeenSelected.containsKey(docId)) {
                    RelatedDocumentsRetriever newCentroid = new RelatedDocumentsRetriever(reader, docId, prop, cluster + 1);
                    TopDocs relatedDocs = newCentroid.getRelatedDocs(numWanted);
                    numRelatedDocs = relatedDocs == null ? 0 : relatedDocs.scoreDocs.length;
                    if (numRelatedDocs == 0) continue;
                    DynamicCentroids.get(cluster).add(newCentroid);
                    dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, docId, contentFieldName, lambda));
                    counter++;
                    for (ScoreDoc sd : relatedDocs.scoreDocs) {
                        hasBeenSelected.put(sd.doc, null);
                    }
                }
            }
            System.out.println("# centroides del cluster " + counter);
            System.out.println(countEqualCentroids(oldCentroids.get(cluster), cluster) + " centroides se mantuvieron igual");

        }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java FastKMedoidsClusterer <prop-file>");
            args[0] = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";
        }

        try {
            LuceneClusterer fkmc = new FPACNU_NotSimilarHeuristics(args[0]);
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
            ceval.showNewMeasures();

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
