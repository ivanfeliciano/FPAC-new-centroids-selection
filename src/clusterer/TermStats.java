/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

import indexer.WMTIndexer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 *
 * @author Debasis
 */
public class TermStats implements Comparable<TermStats> {
    String term;
    int tf;
    float ntf;
    float idf;
    float wt;
    float wt_author;

    static final int MAX_NUM_QRY_TERMS = 50000;
    
    TermStats(String term, float wt) {
        this.term = term;
        this.wt = wt;
        this.wt_author = wt;
    }
    
    TermStats(String term, int tf, IndexReader reader) throws Exception {
        this.term = term;
        this.tf = tf;
        idf = (float)(
                Math.log(reader.numDocs()/
                (float)(reader.docFreq(new Term(WMTIndexer.FIELD_ANALYZED_CONTENT, term)))));
    }

    void computeWeight(int docLen, float lambda) {
        ntf = tf/(float)docLen;
        wt = (float)Math.log(1+ lambda/(1-lambda)*ntf*idf);
    }
    void computeTFIDF(int docLen) {
        wt = (tf/(float)docLen) * idf;
        ntf = tf/(float)docLen;
        wt_author = (float)Math.log(1+ .6/(1-.6)*ntf*idf);
    }

    @Override
    public int compareTo(TermStats that) {
        return -1*Float.compare(this.wt, that.wt); // descending
    }

}
