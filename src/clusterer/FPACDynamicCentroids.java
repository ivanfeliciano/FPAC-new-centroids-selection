/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import static indexer.WMTIndexer.FIELD_ANALYZED_CONTENT;
import static indexer.WMTIndexer.FIELD_DOMAIN_ID;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
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
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.BytesRef;

/**
 *
 * @author dganguly
 */
public class FPACDynamicCentroids extends LuceneClusterer {
    private IndexSearcher searcher;
    
    //Estos son los grupos de centroides
    private ArrayList<ArrayList<RelatedDocumentsRetriever>> DynamicCentroids;

    private ArrayList<ArrayList<TermVector>> dynamicTermVectorCentroids;
    private TermVector[][] termVectorCentroids;


    public FPACDynamicCentroids(String propFile) throws Exception {
        super(propFile);
        
        searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());

        // Inicializa estructura que guarda los centroides
        DynamicCentroids = new ArrayList<>();
        dynamicTermVectorCentroids = new ArrayList<>();
        termVectorCentroids =  new TermVector[K][numberOfCentroidsByGroup];
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
        int numClusterCentresAssigned = 1;

        // Mapa para guardar los documentos que selecciono como centroides
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
    void showCentroids() throws Exception {
        for (int i = 0, j = 1; i < K; i++) {
            System.out.println("Cluster " +  (i + 1) + " has the centroids:");
            j = 1;
            for (RelatedDocumentsRetriever rde: DynamicCentroids.get(i)) {
                Document doc = rde.queryDoc;
                System.out.println("Centroid " + ((j++) % (numberOfCentroidsByGroup + 1)) + ": " + doc.get(WMTIndexer.FIELD_DOMAIN_ID) + ", " + doc.get(idFieldName));
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
    
    @Override
    void recomputeCentroids() throws Exception {
        System.out.println("Recalculando centroides");
        ArrayList<HashSet<String>> clustersVocabulary = new ArrayList<>();
        ArrayList<ArrayList<Integer>> docsInEachCluster = new ArrayList<>(K);
        
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
            Boolean hasBeenSelected[] = new Boolean[docsInEachCluster.get(cluster).size()];
            while (!clusterVocabulary.isEmpty()) {
                int maxCover = 0;
                // Por cada documento en este cluster
                for (int clusterDocsIdx = 0; clusterDocsIdx < docsInEachCluster.get(cluster).size(); clusterDocsIdx++) {
                    if(hasBeenSelected[clusterDocsIdx]) continue;
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
                    intersection.retainAll(clusterVocabulary);
                    if (intersection.size() > maxCover) {
                        maxCover = intersection.size();
                        bestDoc = intersection;
                        bestDocId = docId;
                    }
                }
                if (maxCover == 0) { System.out.println("No cubrí el vocabulario pero ya no había documentos que cumplieran la propiedad"); break;}

                hasBeenSelected[bestDocId] = true;
                clusterVocabulary.removeAll(bestDoc);
                float porcentajeCubierto = 100 - clusterVocabulary.size() * 100 / clusterVocabularyInitialSize;
                System.out.println("He cubierto " + porcentajeCubierto  + " del vocabulario del cluster");
                DynamicCentroids.get(cluster).add(new RelatedDocumentsRetriever(reader, bestDocId, prop, cluster + 1));
                System.out.println("Con " + DynamicCentroids.get(cluster).size() + " centroides");
                dynamicTermVectorCentroids.get(cluster).add(TermVector.extractAllDocTerms(reader, bestDocId, contentFieldName, lambda));
                DynamicCentroids.get(cluster).get(idx++).getRelatedDocs(numDocs / K);
                if(porcentajeCubierto > 50) break;
            }
            if (clusterVocabulary.isEmpty()) { System.out.println("Cubrí el vocabulario con " + idx + " centroides"); }
        }
        
    }
    
    public static void main(String[] args) {
        PrintStream out;
//        try {
//            out = new PrintStream(new FileOutputStream("logscFPACMCentroidsCheckClusterCoverage.txt"));
//            System.setOut(out);
//        } catch (FileNotFoundException ex) {
//            Logger.getLogger(FPACWithMCentroids.class.getName()).log(Level.SEVERE, null, ex);
//        }
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java FastKMedoidsClusterer <prop-file>");
            args[0] = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";
        }
        
        try {
            LuceneClusterer fkmc = new FPACDynamicCentroids(args[0]);
            fkmc.cluster();
            boolean eval = Boolean.parseBoolean(fkmc.getProperties().getProperty("eval", "true"));
            if (eval) {
                ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
                System.out.println("Acc, prec, recall, fscore: ");
                ceval.showNewMeasures();
                System.out.println("Purity: " + ceval.computePurity());
                System.out.println("NMI: " + ceval.computeNMI());            
                System.out.println("RI: " + ceval.computeRandIndex());            
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
