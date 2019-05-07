import os
import sys
import pandas as pd
from scipy.stats import wilcoxon


BASE_DIR_DATA = sys.argv[1] if sys.argv[1] else "data_FPAC/"
list_of_files = os.listdir(BASE_DIR_DATA + '/')

measures = ["RI", "Recall", "Precision", "FScore", "Purity", "NMI"]
for file in list_of_files:
	dataset_set_id = file.strip().split('.')[0]
	print("Calculando p-values para el conjunto: {}".format(dataset_set_id))
	dataset_df = pd.read_csv(BASE_DIR_DATA + file)
	i = 0
	N = len(measures)
	for m in measures:
		print("Evaluando: {}".format(m))
		fpac = dataset_df.iloc[:, i].to_numpy()
		fpac_tc = dataset_df.iloc[:, i + N].to_numpy()
		fpac_tc_l = dataset_df.iloc[:, i + 2 * N].to_numpy()
		print("FPAC vs FPAC_TC_L")
		print(wilcoxon(fpac, fpac_tc_l))
		print("FPAC_TC vs FPAC_TC_L")
		print(wilcoxon(fpac, fpac_tc_l))
		i += 1


