/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.util.BytesRef;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 *
 * @author dganguly
 */
public class FPACNU_SetCover extends LuceneClusterer {

    //Estos son los grupos de centroides
    public ArrayList<ArrayList<RelatedDocumentsRetriever>> DynamicCentroids;

    public ArrayList<ArrayList<TermVector>> dynamicTermVectorCentroids;
    public TermVector[][] termVectorCentroids;
    public Boolean useThreshold = false;
    public int coverThreshold;

    public FPACNU_SetCover(String propFile) throws Exception {
        super(propFile);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

        // Inicializa estructura que guarda los centroides
        DynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids =  new TermVector[K][numberOfCentroidsByGroup];
    }

    public FPACNU_SetCover(String propFile, int threshold) throws Exception {
        super(propFile);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

        coverThreshold = threshold;
        useThreshold = true;
        // Inicializa estructura que guarda los centroides
        DynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids =  new TermVector[K][numberOfCentroidsByGroup];
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
        int numClusterCentresAssigned = 1;

        // Mapa para guardar los documentos que son centroides
        centroidDocIds = new HashMap<>();


        for (int i = 0; i < K; i++) {
            DynamicCentroids.add(new ArrayList<>());
            dynamicTermVectorCentroids.add(new ArrayList<>());
        }

        System.out.println("El número de clusters es " + DynamicCentroids.size());

        int idxCentroidsGroup;

        // Se obtiene un centroide por cada cluster, usando la heurística del autor.
        do {

            // Obtiene la lista top para el documento que no aparece
            // en otras listas TOP.
            RelatedDocumentsRetriever rde = new RelatedDocumentsRetriever(reader, selectedDoc, prop, numClusterCentresAssigned);
            TopDocs topDocs = rde.getRelatedDocs(numDocs/K);
            System.out.println("Chosen doc " + selectedDoc + " as first centroid for cluster " + numClusterCentresAssigned);

            // Si no tiene lista TOP tomo otro documento
            if (topDocs == null) {
                selectedDoc = rde.getUnrelatedDocument(centroidDocIds);
                continue;
            }

            // Actualizo mapa
            centroidDocIds.put(selectedDoc, null);
            TermVector centroid = TermVector.extractAllDocTerms(reader, selectedDoc, contentFieldName, lambda);

            // Agrego a mis centroides
            DynamicCentroids.get(numClusterCentresAssigned - 1).add(rde);
            dynamicTermVectorCentroids.get(numClusterCentresAssigned - 1).add(centroid);

            // Actualizo el nuevo documento que puede ser un posible centroide
            selectedDoc = rde.getUnrelatedDocument(centroidDocIds);

            numClusterCentresAssigned++;

        } while (numClusterCentresAssigned <= K);
        
        numClusterCentresAssigned = 1;

        // Es esto necesario??? Para que inicializar con varios centroides por cluster??
        for (int clusterIdx = 0; clusterIdx < K; clusterIdx++) {
            RelatedDocumentsRetriever rde = DynamicCentroids.get(clusterIdx).get(0);
            TopDocs topDocs = rde.getRelatedDocs(numDocs / K);
            if (topDocs == null || topDocs.scoreDocs.length < numberOfCentroidsByGroup) {
                System.out.println("No pude encontrar doc relacionados D:");
                break;
            }
            idxCentroidsGroup = 1;
            for (int i = 1; i < numberOfCentroidsByGroup; i++) {
                ScoreDoc docFromTopDocs = topDocs.scoreDocs[i];
                centroidDocIds.put(docFromTopDocs.doc, null);
                DynamicCentroids.get(clusterIdx).add(new RelatedDocumentsRetriever(reader, docFromTopDocs.doc, prop, numClusterCentresAssigned));
                dynamicTermVectorCentroids.get(clusterIdx).add(TermVector.extractAllDocTerms(reader, docFromTopDocs.doc, contentFieldName, lambda));
                DynamicCentroids.get(clusterIdx).get(idxCentroidsGroup++).getRelatedDocs(numDocs / K);
            }
        }
    }
    
    @Override
    void showCentroids() {
        for (int i = 0, j; i < K; i++) {
            System.out.println("Cluster " +  (i + 1) + " has the centroids:");
            j = 0;
            for (RelatedDocumentsRetriever rde: DynamicCentroids.get(i)) {
                Document doc = rde.queryDoc;
                System.out.println("Centroid " + ++j + ": " + doc.get(WMTIndexer.FIELD_DOMAIN_ID) + ", " + doc.get(idFieldName));
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
        float maxScore = -100;
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
        if (maxScore < 0) {
            // Retrieved in none... Assign to a random cluster id
            //clusterId = (int)(Math.random()*K);

            // Obtiene el más cercano con la medida de similitud del coseno
            clusterId = getClosestClusterNotAssignedDoc(docId);
            
        }
        return clusterId;
    }
    int getClosestClusterNotAssignedDoc(int docId) throws Exception {
        TermVector docVec = TermVector.extractAllDocTerms(reader, docId, contentFieldName, lambda);
        if (docVec == null) {
            System.out.println("Skipping cluster assignment for empty doc, because the docs is empty: " + docId);
            numberOfDocsAssginedRandomly++;
            return (int)(Math.random()*K);
        }

        float maxSim = 0, sim = 0;
        int mostSimClusterId = 0;
        int clusterId = 0;
        for(int i = 0; i < K; i++)
            for (TermVector centroidVec : dynamicTermVectorCentroids.get(i)) {
                if (centroidVec == null) {
                    numberOfDocsAssginedRandomly++;
                    System.out.println("Skipping cluster assignment for empty doc because there is an empty centroid: " + docId);
                    return (int)(Math.random()*K);            
                }
                clusterId = i;
                sim = docVec.cosineSim(centroidVec);
                if (sim > maxSim) {
                    maxSim = sim;
                    mostSimClusterId = clusterId;
                }
            }
        
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

    public <T> int countIntersection(Set<T> small, Set<T> large){
        //assuming first argument to be smaller than the later;
        //however double checking to be sure
        if (small.size() > large.size()) {
            //swap the references;
            Set<T> tmp = small;
            small = large;
            large = tmp;
        }
        int result = 0;
        for (T item : small) {
            if (large.contains(item)){
                //item found in both the sets
                result++;
            }
        }
        return result;
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

    int coutEqualCentroids(ArrayList<Integer> oldCentroids, int i) {
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
        ArrayList<ArrayList<Integer>> oldCentroids = getOldCentroids();
        int clusterId;
        Terms tfvector;
        TermsEnum termsEnum;
        BytesRef term;
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
            System.out.println("Tamaño del vocaulario del cluster = " + clustersVocabulary.get(cluster).size());
            System.out.println("Número de documentos en el cluster = " + docsInEachCluster.get(cluster).size());
            HashSet<String> clusterVocabulary = clustersVocabulary.get(cluster);
            int idx = 0;
            int clusterVocabularyInitialSize = clusterVocabulary.size();
            Set<String> intersection;
            Set<String> bestDoc = new HashSet<>();
            int bestDocId = 0;
            HashMap <Integer, Byte> hasBeenSelected = new HashMap<>();
            float porcentajeCubierto = 0;
            while (!clusterVocabulary.isEmpty()) {
                int maxCover = 0;
                // Por cada documento en este cluster
                for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                    if(hasBeenSelected.containsKey(clusterDocsIdx)) continue;
                    Set<String> docVocabulary = new HashSet<>();
                    int docId = docsInEachCluster.get(cluster).get(clusterDocsIdx);
                    tfvector = reader.getTermVector(docId, contentFieldName);
                    if (tfvector == null || tfvector.size() == 0)
                        continue;
                    termsEnum = tfvector.iterator();
                    while ((term = termsEnum.next()) != null) { // explore the terms for this field
                        docVocabulary.add(term.utf8ToString());
                    }
                    intersection = new HashSet<>(docVocabulary); // use the copy constructor

                    int sizeOfIntersection = countIntersection(intersection, clusterVocabulary);
                    if (sizeOfIntersection > maxCover) {
                        maxCover = sizeOfIntersection;
                        bestDoc = intersection;
                        bestDocId = docId;
                    }
//                    intersection.retainAll(clusterVocabulary);
//                    if (intersection.size() > maxCover) {
//                        maxCover = intersection.size();
//                        bestDoc = intersection;
//                        bestDocId = docId;
//                    }
                }
                if (maxCover == 0) { System.out.println("No cubrí el vocabulario pero ya no había documentos que cumplieran la propiedad"); break;}
                porcentajeCubierto = 100.0f - (clusterVocabulary.size() * 100.0f) / clusterVocabularyInitialSize;
                System.out.println("He cubierto " + porcentajeCubierto  + "% del vocabulario del cluster con " + DynamicCentroids.get(cluster).size() + " centroides");
                hasBeenSelected.put(bestDocId, null);
                clusterVocabulary.removeAll(bestDoc);
                DynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, bestDocId, prop, cluster + 1));
                dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, bestDocId, contentFieldName, lambda));
                DynamicCentroids.get(cluster).get(idx++).getRelatedDocs(numDocs / K);
                if(useThreshold && porcentajeCubierto > coverThreshold) break;
            }
            if (clusterVocabulary.isEmpty()) { System.out.println("Cubrí el vocabulario con " + idx + " centroides"); }
            System.out.println(coutEqualCentroids(oldCentroids.get(cluster), cluster) + " centroides se mantuvieron igual");
        }
    }
    
    public static void main(String[] args) {
        PrintStream out;
        String propFileName = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/20News_init_20.properties";

        try {
//            out = new PrintStream(new FileOutputStream("./logs/logs_20News_20_algorithmFPACSetCover.txt"));
//            System.setOut(out);
            LuceneClusterer fkmc = new FPACNU_SetCover(propFileName);
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator(propFileName);
            System.out.println("Acc, Prec, recall, fscore: ");
            ceval.showNewMeasures();
            System.out.print("\nPurity: " + ceval.computePurity());
            System.out.print("\nNMI: " + ceval.computeNMI());
            System.out.print("\nRI: " + ceval.computeRandIndex());
            System.out.println();
        }catch (Exception e){
            System.out.println(e);
        }
    }
}
