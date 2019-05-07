package clusterer;

import indexer.WMTIndexer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public void recomputeCentroids() throws IOException {
        numberOfCentroidsByGroup = 10;
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
            System.out.println("Calculando centroides para el cluster " + cluster);

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
            LuceneClusterer fkmc = new FPAC_TC_with_L_centroids("/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties");
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator("/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties");
            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
