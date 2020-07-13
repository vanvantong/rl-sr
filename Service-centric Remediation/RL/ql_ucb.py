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



# update q table:
def update(action, reward, alpha):
    # select action that yields greatest stored value for nextstate and store its value
    q[action]=(1-alpha)*q[action]+alpha*reward

# used to select action to take
def policy():

    #Play each action once
    a = None
    for i in range(len(action_count)):
        if action_count[i] == 0:
            a = i
            break
    #Select action
    if a is None:
        values = {}
        for i in range(action_space):
            values[i] = q[i] + np.sqrt(2 * np.log(step_count) / action_count[i])
            a = max(values, key = values.get)   

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

# = {0: 1.7981655599970512, 1: 1.2331511570997886, 2: 1.0724039017595974, 3: 1.2395005827618344, 4: 1.239398513781559}
q = {}
for a in range(action_space):
    q[a] = 0.0

env = gym.make('troubleshooting-v0')

#env.reset()


for episode in range(0,EPISODES,1):


    step_count = 0
    action_count = {}
    for a in range(action_space):
        action_count[a] = 0.0

    #Accumulate reward
    R = 0
    #Reset each episode
    env.reset()

    for t in range(100):

        time.sleep(4)

        action = policy()

        reward, done= env.step(action)

        R = R + reward
        update(action, reward, alpha)
        print("Episode: {}/{}, timestep:{}, reward: {}, accumulate reward: {}, action: {}".format(episode, EPISODES, t, reward, R, action))
        logScore(episode, t, reward, R, action)

        step_count =  step_count + 1
        action_count[action] = action_count[action] + 1

        if done == True:
            break


    saveQTable(episode, R, q)


print '\nFinish\n'
