package clusterer;

import org.apache.lucene.search.ScoreDoc;

public class FPAC_NoRandom extends FastKMedoidsClusterer {
    public FPAC_NoRandom(String propFile) throws Exception {
        super(propFile);
    }
    @Override
    int getClosestCluster(int docId) throws Exception { // O(K) computation...
        float maxScore = 0;
        int clusterId = 0;
        for (int i=0; i < K; i++) {
            if (rdes[i].docScoreMap == null)
                continue;
            ScoreDoc sd = rdes[i].docScoreMap.get(docId);
            if (sd != null) {
                if (sd.score > maxScore) {
                    maxScore = sd.score;
                    clusterId = i;
                }
            }
        }
        if (maxScore == 0) {
            // Retrieved in none... Assign to a random cluster id
            clusterId = getClosestClusterNotAssignedDoc(docId);
            //numberOfDocsAssginedRandomly++;
        }
        return clusterId;
    }

    int getClosestClusterNotAssignedDoc(int docId) throws Exception {
        TermVector docVec = TermVector.extractAllDocTerms(reader, docId, contentFieldName, lambda);
        if (docVec == null) {
            System.out.println("Random assigment because the docs is empty: " + docId);
            numberOfDocsAssginedRandomly++;
            return (int) (Math.random() * K);
        }
        float maxSim = 0;
        float sim = 0;
        int mostSimClusterId = 0;
        int clusterId = 0;
        for (int i = 0; i < K; i++) {
            int centroidId = rdes[i].docId;
            TermVector centroidVec = TermVector.extractAllDocTerms(reader, centroidId, contentFieldName, lambda);
            clusterId = i;
            sim = docVec.cosineSim(centroidVec);
            if (sim > maxSim) {
                maxSim = sim;
                mostSimClusterId = clusterId;
            }
        }
        return mostSimClusterId;
    }

    public static void main(String[] args) {
        String propsFile = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";
        try {
            LuceneClusterer fkmc = new FPAC_NoRandom(propsFile);
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator(propsFile);
            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}