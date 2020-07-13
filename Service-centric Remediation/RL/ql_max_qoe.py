from math import exp
import random
import numpy as np
import gym
import gym_troubleshooting
import pickle
import pandas
from pandas import DataFrame
import time
from math import log



def writeFile(s):
    f = open("CPU.csv", "a")
    f.write(str(s)+"\n")
    f.close()

def logScore(episode, t, reward, R, qoeState, action):
    f = open("logReward.csv", "a")
    f.write("Episode: "+str(episode)+ ", Timestep: " + str(t)+", Reward: "+ str(reward)+", Accumulate reward: "+ str(R) + ", QoE: " + str(qoeState) + ", Action: " + str(action) +"\n")
    f.close()
def saveQTable(episode, R):
    f = open("Q_Table.csv", "a")
    f.write("Episode: "+str(episode) +", Accumulate reward: "+ str(R) + "\n")
    f.close()   

EPISODES = 1000
#5
action_space = 20

env = gym.make('troubleshooting-v0')



#env.reset()

for episode in range(0,EPISODES,1):

    #Accumulate reward
    R = 0
    #Reset each episode
    env.reset()
    #writeFile("Episode " + str(episode))
    for t in range(100):

        time.sleep(4)
        action = 0
        #Get qoe of all paths in network topo
        qoeState = env.getQoEState()
        #print qoeState
        if(len(qoeState) ==  action_space):
            action = np.argmax(qoeState)

        reward, done= env.step(action)

        R = R + reward
        print("Episode: {}/{}, timestep:{}, reward: {}, accumulate reward: {}, QoE: {}, action: {}".format(episode, EPISODES, t, reward, R, qoeState, action))
        logScore(episode,t, reward, R, qoeState, action)
    
        if done == True:
            break

    saveQTable(episode, R)
    #writeFile("Episode " + str(episode))

print '\nFinish\n'
