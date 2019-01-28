/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package clusterer;

/**
 *
 * @author dganguly
 */
public class FastKMedoidsClusterer_TrueCentroid extends FastKMedoidsClusterer {
    
    public FastKMedoidsClusterer_TrueCentroid(String propFile) throws Exception {
        super(propFile);
    }
    
	// compute true centroid instead of the heuristics 
    TermVector computeCentroid(int centroidId) throws Exception {
        TermVector centroidVec = TermVector.extractAllDocTerms(reader, centroidId, contentFieldName, lambda);
		TermVector newCentroidVec = null;

		if (centroidVec==null || centroidVec.termStatsList == null)
			newCentroidVec = new TermVector();
		else
        	newCentroidVec = new TermVector(centroidVec.termStatsList);

        for (int i=0; i < numDocs; i++) {
            if (i == centroidId)
                continue;

            int clusterId = getClusterId(i);
            if (clusterId != centroidId)
                continue;
            
            TermVector docVec = TermVector.extractAllDocTerms(reader, i, contentFieldName, lambda);
			if (docVec != null)
            	newCentroidVec = TermVector.add(newCentroidVec, docVec);
        }
        return newCentroidVec;
    }
        
    @Override
    void recomputeCentroids() throws Exception {        
        int k = 0;
        for (int centroidId : centroidDocIds.keySet()) {
            TermVector newCentroidVec = computeCentroid(centroidId);            
            centroidVecs[k++] = newCentroidVec;            
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
            
            boolean eval = Boolean.parseBoolean(fkmc.getProperties().getProperty("eval", "false"));
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
