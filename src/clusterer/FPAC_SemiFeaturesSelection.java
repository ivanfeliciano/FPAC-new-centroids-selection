package clusterer;

import indexer.WMTIndexer;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FPAC_SemiFeaturesSelection extends FPACNU_SetCover {

    public FPAC_SemiFeaturesSelection(String propFile) throws Exception {
        super(propFile);
    }

    @Override
    public void recomputeCentroids() throws IOException {
        numberOfCentroidsByGroup = 10;
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
        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        int tf;
        float wt;
        float idf;
        String termText;
        for (int cluster = 0; cluster < K; cluster++) {
            HashMap<String, Float> vocabularySetStats = new HashMap<>();
            System.out.println("Calculando centroides para el cluster " + cluster);
            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                try {
                    tfvector = reader.getTermVector(docId, contentFieldName);
                    if (tfvector == null || tfvector.size() == 0)
                        continue;
                    termsEnum = tfvector.iterator();
                    int docLen = 0;
                    while ((term = termsEnum.next()) != null) {
                        tf = (int) termsEnum.totalTermFreq();
                        termText = term.utf8ToString();
                        docLen += tf;
                        idf = (float) (
                                Math.log(reader.numDocs() /
                                        (float) (reader.docFreq(new Term(WMTIndexer.FIELD_ANALYZED_CONTENT, term)))));
                        wt = (tf / (float) docLen) * idf;

                        if (vocabularySetStats.containsKey(termText)) {
                            if (wt > vocabularySetStats.get(termText)) {
                                vocabularySetStats.replace(termText, wt);
                            }
                        } else vocabularySetStats.put(termText, wt);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            HashMap<String, Float> sorted;
            sorted = vocabularySetStats
                    .entrySet()
                    .stream()
                    .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                    .collect(
                            Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                    LinkedHashMap::new));
            int numberOfTermsForCentroid = 0;
            List<TermStats> termStats = null;
            int idxNumberOfCentroids = 0;
            int numberOfTermsPerQuery = 100;
            for (String termTxt: sorted.keySet()) {
//                System.out.println(termTxt);
                if (numberOfTermsForCentroid == 0){
                    termStats = new ArrayList<>();
                    idxNumberOfCentroids += 1;
                }
                if (numberOfTermsForCentroid < numberOfTermsPerQuery) {
                    try {
                        termStats.add(new TermStats(termTxt, 1, reader));
                        numberOfTermsForCentroid += 1;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                else {
                    DynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, 0, prop, cluster));
                    DynamicCentroids.get(cluster).get(idxNumberOfCentroids - 1).termVectorForTrueCentroids = new TermVector(termStats);
                    dynamicTermVectorCentroids.get(cluster).add(new TermVector(termStats));
                    try {
                        DynamicCentroids.get(cluster).get(idxNumberOfCentroids - 1).getRelatedDocs(numDocs);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    numberOfTermsForCentroid = 0;
                    if (idxNumberOfCentroids == numberOfCentroidsByGroup) break;
                }
            }
        }

    }
    public static void main(String[] args) {
        try {
            LuceneClusterer fkmc = new FPAC_SemiFeaturesSelection("/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties");
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator("/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties");
            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
