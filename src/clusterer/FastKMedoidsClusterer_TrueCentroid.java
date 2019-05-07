/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

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
public class FastKMedoidsClusterer_TrueCentroid extends FastKMedoidsClusterer {

    @Override
    public String getAlgoName() {
        return "FPAC_TC";
    }
    public FastKMedoidsClusterer_TrueCentroid(String propFile) throws Exception {
        super(propFile);
    }
    public FastKMedoidsClusterer_TrueCentroid(String propFile,  List<Integer> initCentroids) throws Exception {
        super(propFile, initCentroids);
    }


    // compute true centroid instead of the heuristics
    TermVector computeCentroid(int clusterId) throws Exception {
        TermVector newCentroidVec = null;
        newCentroidVec = new TermVector();
        HashMap<String, Float> vocabularySetStats = new HashMap<>();
		int numberOfDocsInThisCluster = 0;
        for (int i=0; i < numDocs; i++) {
            int clusterIdCurrentDoc = getClusterId(i);
            if (clusterIdCurrentDoc != clusterId)
                continue;
            numberOfDocsInThisCluster += 1;
            TermVector docVec = TermVector.extractAllDocTerms(reader, i, contentFieldName, lambda);
			if (docVec != null) {
//                newCentroidVec = TermVector.add(newCentroidVec, docVec);
                for(TermStats termStat: docVec.termStatsList) {
                    if (newCentroidVec.termStatsList == null) newCentroidVec.termStatsList = new ArrayList<>();
//                    for (TermStats termStatsCentroid : newCentroidVec.termStatsList) {
//                        if (termStat.term.equals(termStatsCentroid.term)) {
//                            termStatsCentroid.wt += termStat.wt;
//                            isAlreadyInTheVector = true;
//                            break;
//                        }
//                    }
                    if (vocabularySetStats.containsKey(termStat.term)) continue;
                    vocabularySetStats.put(termStat.term,null);
                    newCentroidVec.add(termStat);
                }

            }
        }

        for (TermStats termStatsCentroid: newCentroidVec.termStatsList) {
            termStatsCentroid.wt /= numberOfDocsInThisCluster;
        }

        return newCentroidVec;
    }
        
    @Override
    void recomputeCentroids() throws Exception {        
        int k = 0;
//        System.out.println(centroidDocIds);
        for (int cluster = 0; cluster < K; cluster++) {
            TermVector newCentroidVec = computeCentroid(cluster);
//            System.out.println("Mi nuevo centroide es ");
//            for(TermStats ts: newCentroidVec.termStatsList){
////                System.out.print(ts.term + " ");
//            }
//            System.out.println();
            rdes[cluster].termVectorForTrueCentroids = newCentroidVec;
            rdes[cluster].getRelatedDocs(numDocs);
        }
    }
 
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java FastKMedoidsClusterer_TrueCentroid <prop-file>");
            args[0] = "/home/ivan/Documentos/FPAC-new-centroids-selection/run_properties/init_0.properties";
        }
        
        try {
            LuceneClusterer fkmc = new FastKMedoidsClusterer_TrueCentroid(args[0]);
            fkmc.cluster();
            ClusterEvaluator ceval = new ClusterEvaluator(args[0]);
            ceval.showNewMeasures();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
