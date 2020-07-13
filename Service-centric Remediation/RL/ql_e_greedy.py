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
def policy(epsilon):
    if random.uniform(0,1) < epsilon:
        a = random.randint(0,4)
    else:
        a = max(q, key = q.get)
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
q = {}
Epsilon = 1
epsilon_min = 0.01
epsilon_decay = 0.995

#q = {0: 1.0725895368298697, 1: 1.2445215308677855, 2: 1.244113001132594, 3: 3.1033930588926504, 4: 1.2413238320233253}
for a in range(action_space):
    q[a] = 0.0

env = gym.make('troubleshooting-v0')



#env.reset()

for episode in range(0,EPISODES,1):

    epsilon = Epsilon*(0.5+np.log10(2-np.arctan(episode/10 - 2))) 
    #Accumulate reward
    R = 0
    #Reset each episode
    env.reset()

    for t in range(100):

        time.sleep(4)

        action = policy(epsilon)

        reward, done= env.step(action)

        R = R + reward
        update(action, reward, alpha)
        print("Episode: {}/{}, timestep:{}, reward: {}, accumulate reward: {}, action: {}, epsilon: {}".format(episode, EPISODES, t, reward, R, action, epsilon))
        logScore(episode,t, reward, R, action)
    
        if epsilon > epsilon_min:
            epsilon = epsilon * epsilon_decay
        if done == True:
            break

    saveQTable(episode, R, q)


print '\nFinish\n'
