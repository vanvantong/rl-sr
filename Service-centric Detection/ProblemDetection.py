# -*- coding: utf-8 -*-
import random
import gym
import numpy as np
from keras.preprocessing import sequence
from keras.models import Sequential
from keras.layers.core import Dense, Dropout, Activation
from keras.layers.embeddings import Embedding
from keras.layers.recurrent import LSTM, GRU
from sklearn.model_selection import StratifiedShuffleSplit
from sklearn.metrics import precision_score, recall_score, classification_report,accuracy_score, f1_score
from datetime import datetime
from keras.layers import Conv1D, MaxPooling1D, AveragePooling1D, Conv2D, Flatten, AveragePooling2D
from keras.layers import Permute
import time
import datetime
import pandas as pd






#Model1
def build_CNN_model(time_step, feature):
	model = Sequential()
	#model.add(Conv1D(nb_filter=550, filter_length=5, input_shape=(feature, time_step)))
	#model.add(Activation('relu'))
	#model.add(Conv1D(nb_filter=100, filter_length=4,padding='valid'))
	model.add(Conv2D(64,kernel_size=(2, 2), input_shape=(time_step, feature, 1)))
	model.add(Activation('relu'))
	model.add(AveragePooling2D(pool_size=(2, 2), strides=(2, 2)))
	model.add(Flatten())
	model.add(Dropout(0.25))
	model.add(Dense(100, activation='relu'))
	model.add(Dropout(0.25))
	model.add(Dense(50, activation='relu'))
	model.add(Dropout(0.25))
	model.add(Dense(2))
	model.add(Activation('softmax'))
	model.compile(loss='sparse_categorical_crossentropy', optimizer='rmsprop')
	return model

#Model2
def build_CNN_model2(time_step, feature):
	model = Sequential()
	model.add(Conv2D(32,kernel_size=(2, 2), input_shape=(time_step, feature, 1)))
	model.add(Conv2D(24, kernel_size=(2, 2)))
	model.add(Activation('relu'))
	model.add(AveragePooling2D(pool_size=(2, 2), strides=(2, 2)))
	model.add(Flatten())
	model.add(Dropout(0.25))
	model.add(Dense(64, activation='relu'))
	model.add(Dropout(0.25))
	model.add(Dense(32, activation='relu'))
	model.add(Dropout(0.25))
	model.add(Dense(2))
	model.add(Activation('softmax'))
	model.compile(loss='sparse_categorical_crossentropy', optimizer='rmsprop')
	return model

def classifaction_report_csv(report,precision,recall,f1_score,fold):
	"""Generate the report to data processing"""
	with open('classification_report_troubleshooting_cnn.csv', 'a') as f:
		report_data = []
		lines = report.split('\n')
		row = {}
		row['class'] =  "fold %u" % (fold+1)
		report_data.append(row)
		for line in lines[2:44]:
			row = {}
			line = " ".join(line.split())
			row_data = line.split(' ')
			#print row_data
			if(len(row_data)>2):
				if(row_data[1]!='avg'):
					row['class'] = row_data[0]
					row['precision'] = float(row_data[1])
					row['recall'] = float(row_data[2])
					row['f1_score'] = float(row_data[3])
					row['support'] = row_data[4]
					report_data.append(row)
				else:
					row['class'] = row_data[0]+row_data[1]
					row['precision'] = float(row_data[2])
					row['recall'] = float(row_data[3])
					row['f1_score'] = float(row_data[4])
					row['support'] = row_data[5]
					report_data.append(row)
		row = {}
		row['class'] = 'macro'
		row['precision'] = float(precision)
		row['recall'] = float(recall)
		row['f1_score'] = float(f1_score)
		row['support'] = 0
		report_data.append(row)
		dataframe = pd.DataFrame.from_dict(report_data)
		dataframe.to_csv(f, index = False)

time_step = 10
feature = 4

X = []
y = []
arrLabel = []

#Processing data
f = open('TroubleshootingData.csv','r')
for line in f:
	arrDelay = []
	arrPL = []
	arrRate = []
	arrTypeTraffic = []
	X_tmp = []

	tmp = line.split('*')
	if len(tmp) == 5:

		s = tmp[0].split(',')
		if len(s) == 10:
			for i in range(10):
				arrDelay.append(float(s[i]))
		s = tmp[1].split(',')
		if len(s) == 10:
			for i in range(10):
				arrPL.append(float(s[i]))
		s = tmp[2].split(',')
		if len(s) == 10:
			for i in range(10):
				arrRate.append(float(s[i]))
		s = tmp[3].split(',')
		if len(s) == 10:
			for i in range(10):
				arrTypeTraffic.append(float(s[i]))

		arrLabel.append([float(tmp[4])])

		
		X_tmp = [[arrDelay[i], arrPL[i], arrRate[i], arrTypeTraffic[i]] for i in range(len(arrDelay))]
		X_tmp = np.array(X_tmp)
		X.append(X_tmp)
		

X = np.array(X)
X = X.reshape(len(X), time_step, feature,1)
y = [arrLabel[i] for i in range(len(arrLabel))]
y = np.array(y)

#Split data into training and testing dataset
max_epoch = 20
batch_size = 128
nfolds = 20

for fold in range(nfolds):
	print "fold %u/%u" % (fold+1, nfolds)
	sss1 = StratifiedShuffleSplit(n_splits=5, test_size=0.2, random_state=0)
	for train, test in sss1.split(X, y):
		X_tmp, X_test, y_tmp, y_test = X[train], X[test], y[train], y[test]
	sss2 = StratifiedShuffleSplit(n_splits=1, test_size=0.05, random_state=0)
	for train, test in sss2.split(X_tmp, y_tmp):
		X_train, X_holdout, y_train, y_holdout = X_tmp[train], X_tmp[test], y_tmp[train], y_tmp[test]
	
	best_auc = 0.0
	#Initiate CNN model
	model_dqn = build_CNN_model2(10,4)
	print model_dqn.summary()  
	#print X_train
	#print y_train

	for ep in range(max_epoch):
		model_dqn.fit(X_train, y_train, batch_size=batch_size, nb_epoch=1)
		t_probs= model_dqn.predict_proba(X_holdout)
		t_result = [np.argmax(x) for x in t_probs]
		t_acc = accuracy_score(y_holdout, t_result)
		#Get the model with highest accuracy
		if t_acc > best_auc:
			best_model = model_dqn
			best_auc = t_acc
			#Calculate the final result
			y_pred = model_dqn.predict_proba(X_test)
			y_result = [np.argmax(x) for x in y_pred]
			score = f1_score(y_test, y_result,average="macro")
			precision = precision_score(y_test, y_result,average="macro")
			recall = recall_score(y_test, y_result,average="macro")
			report = classification_report(y_test,y_result,digits=4)
			acc= accuracy_score(y_test, y_result)
			print '\n clasification report:\n', report
			print 'F1 score:', score
			print 'Recall:', recall
			print 'Precision:', precision
			print 'Acc:', acc
			classifaction_report_csv(report,precision,recall,score,fold)

# serialize model to JSON
model_json = best_model.to_json()
with open("TroubleshootingModel.json", "w") as json_file:
    json_file.write(model_json)
# serialize weights to HDF5
best_model.save_weights("TroubleshootingWeiight.h5")
print("Saved model to disk")			
print '\nFinish\n'				