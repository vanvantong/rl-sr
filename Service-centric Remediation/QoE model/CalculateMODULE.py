

import pickle
import pandas

from pandas import DataFrame





f = open('RFmodelVOIP.pkl', 'rb')
RFmodelFTP = pickle.load(f)
f.close()

f2 = open('Network_Parameter/voip_normal.csv','r')
for line in f2:
	tmp = line.split(',')
	if len(tmp) == 3:
		delayValue = float(tmp[0])/1000
		lossrateValue = float(tmp[1])
		bwdValue = 1 - float(tmp[2])
		FTP_InformationSample = {'delay': [delayValue],'lossrate': [lossrateValue],'bwd': [bwdValue]}
		lables = ['delay','lossrate','bwd']
		FTP_DataFrame = DataFrame(FTP_InformationSample, columns=lables)
		QoE_FTP = RFmodelFTP.predict(FTP_DataFrame)
		QoE_FTP = str(QoE_FTP[0])
		QoE_FTP = QoE_FTP.strip("[").strip("]").strip("\n")
		f3 = open("voip_normal_qoe.csv","a+")
		tmpStr = tmp[0] + "," + tmp[1] + "," + tmp[2].strip("\n") + "," + str(QoE_FTP)
		f3.write(tmpStr+"\n")
		f3.close()
print "Finish\n"
