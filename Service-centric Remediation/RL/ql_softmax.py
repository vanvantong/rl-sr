from math import exp
import random
import numpy as np
import gym
import gym_troubleshooting
import pickle
import pandas
from pandas import DataFrame
import time


def writeFile(s):
    f = open("CPU.csv", "a")
    f.write(str(s)+"\n")
    f.close()

# update q table:
def update(action, reward, alpha):
    # select action that yields greatest stored value for nextstate and store its value
    q[action]=(1-alpha)*q[action]+alpha*reward

# used to select action to take
def policy(t):
    p = np.array([q[x]/t for x in range(action_space)])
    prob_actions = np.exp(p) / np.sum(np.exp(p))
    cumulative_probability = 0.0
    choice = random.uniform(0,1)
    for a,pr in enumerate(prob_actions):
        cumulative_probability += pr
        if cumulative_probability > choice:
            return a

def logScore(episode, t, reward, R, action):
    f = open("logReward.csv", "a")
    f.write("Episode: "+str(episode)+ ", Timestep: " + str(t)+", Reward: "+ str(reward)+", Accumulate reward: "+ str(R) + ", Action: " + str(action) +"\n")
    f.close()
def saveQTable(episode, R, q):
    f = open("Q_Table.csv", "a")
    f.write("Episode: "+str(episode) +", Accumulate reward: "+ str(R) +", "+str(q) + "\n")
    f.close()   

alpha = 0.7
EPISODES = 1000
action_space = 5
temp = 3.0
q = {}
for a in range(action_space):
    q[a] = 0.0

#temp = 2.26
#q = {0: 3.999021776456909, 1: 3.511538381219561, 2: 3.8201280413039074, 3: 3.8357185962797153, 4: 3.8967999996199967}
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

        action = policy(temp)
        reward, done= env.step(action)

        R = R + reward
        update(action, reward, alpha)
        print("Episode: {}/{}, timestep:{}, reward: {}, accumulate reward: {}, action: {}".format(episode, EPISODES, t, reward, R, action))
        logScore(episode, reward, t, R, action)
    
        if done == True:
            break

    saveQTable(episode, R, q)
    if temp > 1.0:
        temp -= 0.01
    #writeFile("Episode " + str(episode))
print '\nFinish\n'
