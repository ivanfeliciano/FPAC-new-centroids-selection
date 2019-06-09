/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.List;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.ScoreDoc;

/**
 *
 * @author dganguly
 */
public class FastKMedoidsClusterer extends LuceneClusterer {
    IndexSearcher searcher;
    RelatedDocumentsRetriever[] rdes;
    List<Integer> initialCentroids;
    // Un conjunto de términos para cada cluster
    
    Set<String>[] listSetOfTermsForEachCluster;

    @Override
    public String getAlgoName() {
        return "FastKMedoidsClusterer";
    }

    public FastKMedoidsClusterer(String propFile, List<Integer> initialCentroidsSeeds) throws Exception {
        super(propFile);
        
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());        
        rdes = new RelatedDocumentsRetriever[K];
        listSetOfTermsForEachCluster = new HashSet[K];
        initialCentroids = initialCentroidsSeeds;
    }
    public FastKMedoidsClusterer(String propFile) throws Exception {
        super(propFile);

        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        rdes = new RelatedDocumentsRetriever[K];
        listSetOfTermsForEachCluster = new HashSet[K];
    }
    
    int selectDoc(HashSet<String> queryTerms) throws IOException {
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (String qterm : queryTerms) {
            TermQuery tq = new TermQuery(new Term(contentFieldName, qterm));
            b.add(new BooleanClause(tq, BooleanClause.Occur.MUST_NOT));
        }
        
        TopDocsCollector collector = TopScoreDocCollector.create(1);
        searcher.search(b.build(), collector);
        TopDocs topDocs = collector.topDocs();
        return topDocs.scoreDocs == null || topDocs.scoreDocs.length == 0? -1 :
                topDocs.scoreDocs[0].doc;
    }
    
    // Initialize centroids
    // The idea is to select a random document. Grow a region around it and choose
    // as the next candidate centroid a document that does not belong to this region.
    @Override
    void initCentroids() throws Exception {
        int selectedDoc = (int)(Math.random()*numDocs);
        int numClusterCentresAssigned = 0;
        centroidDocIds = new HashMap<>();
        ArrayList<Integer> centroidsIdsForPrint = new ArrayList<>();
        while (numClusterCentresAssigned < K) {
            selectedDoc = initialCentroids == null ? selectedDoc : initialCentroids.get(numClusterCentresAssigned);
            RelatedDocumentsRetriever rde = new RelatedDocumentsRetriever(reader, selectedDoc, prop, numClusterCentresAssigned );
            System.out.println("El documento " + selectedDoc + " se elige como centroide para el cluster " + (numClusterCentresAssigned));
            TopDocs topDocs = rde.getRelatedDocs(numDocs );
            if (topDocs == null) {
                System.out.println("No obtuve lista top para este centroide");
                selectedDoc = rde.getUnrelatedDocument(centroidDocIds, rdes);
                continue;
            }
            centroidsIdsForPrint.add(selectedDoc);
            clusterIdMap.put(selectedDoc, numClusterCentresAssigned);
            centroidDocIds.put(selectedDoc, null);
            rdes[numClusterCentresAssigned] = rde;
            selectedDoc = rde.getUnrelatedDocument(centroidDocIds, rdes);
            System.out.println("selected Doc " + selectedDoc);
            numClusterCentresAssigned++;
        }
        System.out.print("initialCentroids[].add(Arrays.asList(");
        for (int idx = 0; idx < centroidsIdsForPrint.size(); idx++) {
            System.out.print(centroidsIdsForPrint.get(idx));
            if (idx < centroidsIdsForPrint.size() - 1) System.out.print(", ");
        }
        System.out.println("));");
    }
    
    void showCentroids() throws Exception {
        int i = 0;
        for (RelatedDocumentsRetriever rde: rdes) {
            Document doc = rde.queryDoc;
            System.out.println("Centroid " + (i++) + ": " + doc.get(WMTIndexer.FIELD_DOMAIN_ID) + ", " + doc.get(idFieldName));
        }
    }
    
    @Override
    boolean isCentroid(int docId) {
        for (int i=0; i < K; i++) {
            if (rdes[i].docId == docId)
                return true;
        }
        return false;
    }
    
    @Override
    int getClosestCluster(int docId) throws Exception { // O(K) computation...
        float maxScore = -10;
        boolean notInAnyTopList = true;
        int clusterId = 0;
        for (int i=0; i < K; i++) {
            if (rdes[i].docScoreMap == null) {
                System.out.println("El centroide " + i + " no tiene top list");
                continue;
            }
            ScoreDoc sd = rdes[i].docScoreMap.get(docId);
            if (sd != null) {
                notInAnyTopList = false;
                if (sd.score > maxScore) {
                    maxScore = sd.score;
                    clusterId = i;
                }
            }
        }
        if (notInAnyTopList) {
            // Retrieved in none... Assign to a random cluster id
            clusterId = (int)(Math.random()*K);
//            System.out.println("El documento " + docId  + " se asignó aleatoriamente al cluster " + clusterId);
            numberOfDocsAssginedRandomly++;
        }
        return clusterId;
    }
    
    // Returns true if the cluster id is changed...
    @Override
    boolean assignClusterId(int docId, int clusterId) throws Exception {
        rdes[clusterId].addDocId(docId);        
        return super.assignClusterId(docId, clusterId);
    }
        
    @Override
    void recomputeCentroids() throws Exception {
        centroidDocIds.clear();
        int clusterId;
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            docsInEachCluster.add(new ArrayList<>());
        }
        for (int docId = 0; docId < numDocs; docId++) {
            clusterId = getClusterId(docId);
            if (clusterId == INITIAL_CLUSTER_ID) continue;
            docsInEachCluster.get(clusterId).add(docId);
        }
        for (int i=0; i < K; i++) {
            int newCentroidDocId = docsInEachCluster.get(0).get(0);
            int maxUniqueTerms = -1;
            int numberOfUniqueTerms;
            for (int docId: docsInEachCluster.get(i)) {
                numberOfUniqueTerms = rdes[i].getNumberOfUniqueTerms(docId);
                if (numberOfUniqueTerms > maxUniqueTerms)
                {
                    newCentroidDocId = docId;
                    maxUniqueTerms = numberOfUniqueTerms;
                }
            }
//            newCentroidDocId = rdes[i].recomputeCentroidDoc(centroidDocIds);
            centroidDocIds.put(newCentroidDocId, null);
            if (rdes[i].docId != newCentroidDocId) {
                String oldCentroidURL = rdes[i].queryDoc.get(idFieldName);
                rdes[i] = new RelatedDocumentsRetriever(reader, newCentroidDocId, prop, i);
                String newCentroidURL = rdes[i].queryDoc.get(idFieldName);
                System.out.println("Changed centroid document " + oldCentroidURL + " to " + newCentroidURL);
                rdes[i].getRelatedDocs(numDocs);
            }
            else {
                System.out.println("Me quedo con el centroide " + newCentroidDocId);
            }
        }
    }
    
    public static void main(String[] args) {
//        if (args.length == 0) {
//            args = new String[1];
//            System.out.println("Usage: java FastKMedoidsClusterer <prop-file>");
//            args[0] = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";
//        }
        String properties_paths[] = {"/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden1.properties",
                "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden2.properties",
                "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_orden3.properties"};
//        try {
//            LuceneClusterer fkmc = new FastKMedoidsClusterer(args[0]);
//            fkmc.cluster();
//            ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
//            ceval.showNewMeasures();
//        }
//        catch (Exception ex) {
//            ex.printStackTrace();
//        }

        for (int i = 0; i < properties_paths.length; i++) {
            System.out.println("Orden " + i);
            for (int j = 0; j < 5; j++) {
//                System.out.println("Semilla " + j);
                try {
                    LuceneClusterer fkmc = new FastKMedoidsClusterer(properties_paths[i]);
                    fkmc.initCentroids();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

    }
}
