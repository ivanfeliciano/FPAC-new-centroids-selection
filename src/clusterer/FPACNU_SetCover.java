/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopDocsCollector;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author dganguly
 */
public class FPACNU_SetCover extends LuceneClusterer {
    private IndexSearcher searcher;
    
    //Estos son los grupos de centroides
    public ArrayList<ArrayList<RelatedDocumentsRetriever>> DynamicCentroids;

    public ArrayList<ArrayList<TermVector>> dynamicTermVectorCentroids;
    private TermVector[][] termVectorCentroids;
    private boolean useStopThresholdCriteria = false;
    private float stopThresholdCritera;
    int initialSelectedDoc;
    boolean shouldUseRandom = false;

    public FPACNU_SetCover(String propFile) throws Exception {

        super(propFile);
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());
        initialSelectedDoc = 100;
        // Inicializa estructura que guarda los centroides
        DynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids =  new TermVector[K][numberOfCentroidsByGroup];
    }

    public FPACNU_SetCover(String propFile, float threshold) throws Exception {
        super(propFile);

        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

        // Inicializa estructura que guarda los centroides
        DynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids =  new TermVector[K][numberOfCentroidsByGroup];

        stopThresholdCritera = threshold;
        useStopThresholdCriteria = true;
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
    // Same but with several centroids for each cluster
    // With the top list we select the most similar docs from an initial selected doc
    // at each iteration
    @Override
    void initCentroids() throws Exception {
        int selectedDoc = (int)(Math.random() * numDocs);

//        List<Integer> initialCentroids = Arrays.asList(2, 15076, 4503, 6205, 7694, 14206, 7569, 16363, 1533, 17118, 17092, 4764, 144, 10827, 15704, 896, 12168, 7925, 4734, 13700);
//        List<Integer> initialCentroids = Arrays.asList(6877, 17952);
//        List<Integer> initialCentroids = Arrays.asList(466, 7590);
//        List<Integer> initialCentroids = Arrays.asList(16256, 20532);
//        List<Integer> initialCentroids = Arrays.asList(27,34,28,25);
//        int selectedDoc;
//        List<Integer> initialCentroids = Arrays.asList(0, 49, 20, 26, 12);//, 10, 25, 42, 30, 35);

        List<Integer> initialCentroids = Arrays.asList(12, 25);
        int numClusterCentresAssigned = 0;

        // Mapa para guardar los documentos que selecciono como centroides
        centroidDocIds = new HashMap<>();


        for (int i = 0; i < K; i++) {
            DynamicCentroids.add(new ArrayList<>());
            dynamicTermVectorCentroids.add(new ArrayList<>());
        }

        System.out.println("El número de clusters es " + DynamicCentroids.size());

        int idxCentroidsGroup;

        // Se obtiene un centroide por cada cluster, usando la heurística del autor.
        while(numClusterCentresAssigned < K) {
//            selectedDoc = initialCentroids.get(numClusterCentresAssigned);
            // Obtiene la lista top para el documento que no aparece
            // en otras listas TOP.
            selectedDoc = initialCentroids.get(numClusterCentresAssigned);
            System.out.println("El documento " + selectedDoc + " se elige como centroide para el cluster " + numClusterCentresAssigned);
            RelatedDocumentsRetriever rde = new RelatedDocumentsRetriever(reader, selectedDoc, prop, numClusterCentresAssigned + 1);
            clusterIdMap.put(selectedDoc, numClusterCentresAssigned);
            TopDocs topDocs = rde.getRelatedDocs(numDocs/K);
//            System.out.println("Chosen doc " + selectedDoc + " as first centroid for cluster " + numClusterCentresAssigned);
//             Si no tiene lista TOP tomo otro documento
            if (topDocs == null) {
                selectedDoc = rde.getUnrelatedDocument(centroidDocIds);
                continue;
            }
            // Actualizo mapa
            centroidDocIds.put(selectedDoc, null);
            TermVector centroid = TermVector.extractAllDocTerms(reader, selectedDoc, contentFieldName, lambda);
            // Agrego a mis centroides
            DynamicCentroids.get(numClusterCentresAssigned).add(rde);
            dynamicTermVectorCentroids.get(numClusterCentresAssigned).add(centroid);

            // Actualizo el nuevo documento que puede ser un posible centroide
            selectedDoc = rde.getUnrelatedDocument(centroidDocIds);

            numClusterCentresAssigned++;

        }
//        while (numClusterCentresAssigned <= K);
        
        numClusterCentresAssigned = 1;

//        // Es esto necesario??? Para que inicializar con varios centroides por cluster??
//        for (int clusterIdx = 0; clusterIdx < K; clusterIdx++) {
//            RelatedDocumentsRetriever rde = DynamicCentroids.get(clusterIdx).get(0);
//            TopDocs topDocs = rde.getRelatedDocs(numDocs / K);
//            if (topDocs == null || topDocs.scoreDocs.length < numberOfCentroidsByGroup) {
//                System.out.println("No pude encontrar doc relacionados D:");
//                break;
//            }
//            idxCentroidsGroup = 1;
//            for (int i = 1; i < numberOfCentroidsByGroup; i++) {
//                ScoreDoc docFromTopDocs = topDocs.scoreDocs[i];
//                centroidDocIds.put(docFromTopDocs.doc, null);
//                DynamicCentroids.get(clusterIdx).add(new RelatedDocumentsRetriever(reader, docFromTopDocs.doc, prop, numClusterCentresAssigned));
//                dynamicTermVectorCentroids.get(clusterIdx).add(TermVector.extractAllDocTerms(reader, docFromTopDocs.doc, contentFieldName, lambda));
//                DynamicCentroids.get(clusterIdx).get(idxCentroidsGroup++).getRelatedDocs(numDocs / K);
//            }
//        }
    }
    
    @Override
    void showCentroids() throws Exception {
        for (int i = 0, j = 0; i < K; i++) {
            System.out.println("Cluster " +  i + " has the centroids:");
            for (RelatedDocumentsRetriever rde: DynamicCentroids.get(i)) {
                Document doc = rde.queryDoc;
                System.out.println("Centroid " + (j++ % numberOfCentroidsByGroup) + ": " + doc.get(WMTIndexer.FIELD_DOMAIN_ID) + ", " + doc.get(idFieldName));
            }
        }
    }
    
    @Override
    boolean isCentroid(int docId) {
        for (int i=0; i < K; i++) {
            for (RelatedDocumentsRetriever rde : DynamicCentroids.get(i)) {
                if (rde.docId == docId)
                    return true;
            }
        }
        return false;
    }
    
    @Override
    int getClosestCluster(int docId) throws Exception { // O(K) computation...
        float maxScore = 0;
        int clusterId = 0;
        for (int i = 0; i < K; i++) {
            float localScore = 0;
            for (RelatedDocumentsRetriever rde : DynamicCentroids.get(i)) {
                if (rde.docScoreMap == null)
                    continue;
                ScoreDoc sd = rde.docScoreMap.get(docId);
                if (sd != null)
                    localScore += sd.score;
            }
            if (localScore > maxScore) {
                maxScore = localScore;
                clusterId = i;
            }
        }
        if (maxScore == 0) {
            // Retrieved in none... Assign to a random cluster id
            if (shouldUseRandom && Math.random() > 0.9) {
                System.out.println("El documento " + docId  + " se asignó aleatoriamente al cluster " + clusterId);
                clusterId = (int)(Math.random()*K);
                numberOfDocsAssginedRandomly++;
            }
            // Obtiene el más cercano con la medida de similitud del coseno
            else clusterId = getClosestClusterNotAssignedDoc(docId);
//            shouldUseRandom = !shouldUseRandom;
        }
        return clusterId;
    }
    int getClosestClusterNotAssignedDoc(int docId) throws Exception {
        TermVector docVec = TermVector.extractAllDocTerms(reader, docId, contentFieldName, lambda);
        if (docVec == null) {
//            System.out.println("Skipping cluster assignment for empty doc, because the docs is empty: " + docId);
            numberOfDocsAssginedRandomly++;
            return (int)(Math.random()*K);
        }

        float maxSim = 0, sim;
        int mostSimClusterId = 0;
        int clusterId = 0;
        for(int i = 0; i < K; i++)
            for (TermVector centroidVec : dynamicTermVectorCentroids.get(i)) {
                if (centroidVec == null) {
                    numberOfDocsAssginedRandomly++;
                    System.out.println("Skipping cluster assignment for empty doc because there is an empty centroid: " + docId);
                    System.out.println("El documento " + docId  + " se asignó aleatoriamente al cluster " + clusterId);
                    return (int)(Math.random()*K);
                }
                clusterId = i;
                sim = docVec.cosineSim(centroidVec);
                if (sim > maxSim) {
                    maxSim = sim;
                    mostSimClusterId = clusterId;
                }
            }
        if(Float.compare(maxSim, 0) == 0){
            numberOfDocsAssginedRandomly++;
            clusterId = (int)(Math.random()*K);
            System.out.println("El documento " + docId  + " se asignó aleatoriamente al cluster " + clusterId);
            return clusterId;
        }
        numberOfAssignedByCosineSim++;
        System.out.println("El documento " + docId  + " se asignó al cluster " + clusterId + " con similitud = " + maxSim);
        return mostSimClusterId;
    }
    // Returns true if the cluster id is changed...
    @Override
    boolean assignClusterId(int docId, int clusterId) throws Exception {
        return super.assignClusterId(docId, clusterId);
    }
        
    
    ArrayList<ArrayList<Integer>> ListOfDocsForEachCluster() throws Exception {
        ArrayList<ArrayList<Integer>> docsIdForThisCluster = new ArrayList<>(K);
        for (int i = 0; i < numDocs; i++)
            docsIdForThisCluster.get(getClusterId(i)).add(i);
        return docsIdForThisCluster;
    }

    ArrayList<ArrayList<Integer>> getOldCentroids() {
        ArrayList<ArrayList<Integer>> oldCentroids = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            oldCentroids.add(new ArrayList<>());
            for (int j = 0; j < DynamicCentroids.get(i).size(); j++) {
                oldCentroids.get(i).add(DynamicCentroids.get(i).get(j).docId);
            }
        }
        return oldCentroids;
    }

    int countEqualCentroids(ArrayList<Integer> oldCentroids, int i) {
        int equals = 0;
        for (RelatedDocumentsRetriever rde : DynamicCentroids.get(i)) {
            if (Collections.binarySearch(oldCentroids, rde.docId) > 0) {
                equals++;
            }
        }
        return equals;
    }



    @Override
    void recomputeCentroids() throws Exception {
        System.out.println("Recalculando centroides");
        ArrayList<HashSet<String>> clustersVocabulary = new ArrayList<>();
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        
        int clusterId;
        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
        ArrayList<ArrayList<Integer>> oldCentroids = getOldCentroids();
        // Por cada cluster se crea un conjunto donde
        // se va a guardar el vocabulario del cluster
        // y se limpian las estructuras que guardan los centroides
        // de cada cluster
        for (int i = 0; i < K; i++) {
            clustersVocabulary.add(new HashSet<>());
            docsInEachCluster.add(new ArrayList<>());
            DynamicCentroids.get(i).clear();
            dynamicTermVectorCentroids.get(i).clear();
        }
        
        // Por cada documento obtengo el el id del cluster al
        // que pertence y lo agrego a mi arreglo que agrupa
        // los ids de los documentos por cluster.
        // Mientras, en cada iteración se va llenando
        // el vocabulario de cada cluster.

        System.out.println("Generando vocabulario del cluster");
        for (int docId = 0; docId < numDocs; docId++) {
            clusterId = getClusterId(docId);
            if (clusterId == INITIAL_CLUSTER_ID) continue;
            docsInEachCluster.get(getClusterId(docId)).add(docId);
            tfvector = reader.getTermVector(docId, contentFieldName);
            if (tfvector == null || tfvector.size() == 0)
                continue;
            termsEnum = tfvector.iterator();
            while ((term = termsEnum.next()) != null) { // explore the terms for this field
                clustersVocabulary.get(clusterId).add(term.utf8ToString());
            }
        }
        
        // Cubrimiento del vocabulario

        for (int cluster = 0; cluster < K; cluster++) {
            System.out.println("Cubriendo el vocabulario del cluster " + cluster);
            HashSet<String> clusterVocabulary = clustersVocabulary.get(cluster);
            int idx = 0;
            int clusterVocabularyInitialSize = clusterVocabulary.size();
            Set<String> intersection;
            Set<String> bestDoc = new HashSet<>();
            int bestDocId = 0;
            HashMap <Integer, Byte> hasBeenSelected = new HashMap<>();
            float porcentajeCubierto = 0.0f;
            int documentosCubiertos = 0;
            int initialNumberOfRelatedDocs = numDocs / K;
            int remaining = 0;
            /////////////////////

            HashSet[] docVocabulary = new HashSet[docsInEachCluster.get(cluster).size()];
            for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                tfvector = reader.getTermVector(docId, contentFieldName);
                if (tfvector == null || tfvector.size() == 0) {
                    continue;
                }
                docVocabulary[clusterDocsIdx] = new HashSet<>();
                termsEnum = tfvector.iterator();

                while ((term = termsEnum.next()) != null) { // explore the terms for this field
                    docVocabulary[clusterDocsIdx].add(term.utf8ToString());
                }

            }
            ///////////////////////

            while (!clusterVocabulary.isEmpty()) {
                int maxCover = 0;
                for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                    if(hasBeenSelected.containsKey(clusterDocsIdx)) continue;
                    int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                    if (docVocabulary[clusterDocsIdx] == null) continue;
                    intersection = new HashSet<>(docVocabulary[clusterDocsIdx]);
                    intersection.retainAll(clusterVocabulary);
                    if (intersection.size() > maxCover) {
                        maxCover = intersection.size();
                        bestDoc = intersection;
                        bestDocId = docId;
                        //                        docVocabulary[clusterDocsIdx] = null;
                    }
                }
                if (maxCover == 0) { System.out.println("No cubrí el vocabulario pero ya no había documentos que cumplieran la propiedad"); break;}
                hasBeenSelected.put(bestDocId, null);
                clusterVocabulary.removeAll(bestDoc);
                porcentajeCubierto = 100.0f - (clusterVocabulary.size() * 100.0f) / clusterVocabularyInitialSize;
                System.out.println("He cubierto " + porcentajeCubierto  + "% del vocabulario del cluster con " + idx + " centroides");
                DynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, bestDocId, prop, cluster + 1));

//                System.out.println("Con " + DynamicCentroids.get(cluster).size() + " centroides");
                dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, bestDocId, contentFieldName, lambda));
                TopDocs relatedDocs = DynamicCentroids.get(cluster).get(idx++).getRelatedDocs(initialNumberOfRelatedDocs);
//                documentosCubiertos += relatedDocs.scoreDocs.length;
//                if (documentosCubiertos > (numDocs / K)) {
//                    System.out.println("He cubierto " + documentosCubiertos + " documentos");
//                    break;
//                }
                if (useStopThresholdCriteria && porcentajeCubierto > stopThresholdCritera) break;
            }
//            if (clusterVocabulary.isEmpty()){ System.out.println("Cubrí el vocabulario con " + idx + " centroides"); }
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
            LuceneClusterer fkmc = new FPACNU_SetCover(args[0]);
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
