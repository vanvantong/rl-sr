import gym
from gym import error, spaces, utils
from gym.utils import seeding
import time
import math
from datetime import datetime
import numpy as np
from collections import deque
import pickle
import pandas
from pandas import DataFrame

# 1: Video streaming, 2: File transfer, 3: VoIP
def qoe_model(service, latency, loss, rate):
  if service == 1: 
    f = open('RFmodelVIDEO.pkl', 'rb')
    RFmodelVS = pickle.load(f)
    f.close()
    lables = ['delay','lossrate','bwd']
    VS_InformationSample = {'delay': [latency],'lossrate': [loss],'bwd': [rate]}
    VS_DataFrame = DataFrame(VS_InformationSample, columns=lables)
    QoE_VS = RFmodelVS.predict(VS_DataFrame)
    return float(str(QoE_VS).strip("[").strip("]").strip("\n"))
  elif service == 2:
    f = open('RFmodelFTP.pkl', 'rb')
    RFmodelFT = pickle.load(f)
    f.close()
    lables = ['delay','lossrate','bwd']
    FT_InformationSample = {'delay': [latency],'lossrate': [loss],'bwd': [rate]}
    FT_DataFrame = DataFrame(FT_InformationSample, columns=lables)
    QoE_FT = RFmodelFT.predict(FT_DataFrame)
    return float(str(QoE_FT).strip("[").strip("]").strip("\n"))       
  elif service == 3:
    f = open('RFmodelVOIP.pkl', 'rb')
    RFmodelVOIP = pickle.load(f)
    f.close()
    lables = ['delay','lossrate','bwd']
    VOIP_InformationSample = {'delay': [latency],'lossrate': [loss],'bwd': [rate]}
    VOIP_DataFrame = DataFrame(VOIP_InformationSample, columns=lables)
    QoE_VOIP = RFmodelVOIP.predict(VOIP_DataFrame)
    return float(str(QoE_VOIP).strip("[").strip("]").strip("\n"))


def is_number(s):
  try:
    float(s)
    return float(s)
  except ValueError:
    return 0


def convert(n):
  s = '%s'% format(n,'02x')
  return str(s)


def getStateFlex():
  latency = 0
  packetLoss = 0
  rate = 0
  qoeArr = []
  service = 1
  action_state = 20
  src = "of:0000000000000015"
  dst = "of:0000000000000016"
  f = open('/home/vantong/onos/providers/lldpcommon/src/main/java/org/onosproject/provider/lldpcommon/link_para.csv','r')
  for line in f:
    link = line.split(";")
    for i in range(action_state):
      #print convert(i+1)
      Link1 = src+"-of:00000000000000"+convert(i+1)
      Link2 = "of:00000000000000"+convert(i+1)+"-"+dst
      if line.find(Link1)!= -1 and line.find(Link2)!= -1:
        for i in range(len(link)):
          if link[i].find(Link1) != -1 or  link[i].find(Link2)!= -1:
            #print link[i]
            part = link[i].split("*")
            if len(part) == 4:
              l = part[1].split(",")
              if len(l) == 10:
                latency = latency + float(l[9])
              pl = part[2].split(",")
              if len(pl) == 10:
                packetLoss = packetLoss + float(pl[9])
              r = part[3].split(",")
              if len(r) == 10:
                rate = max(rate, is_number(r[9]))
        packetLoss = packetLoss/2
        latency = latency / 1000
        #qoe = vs_qoe_model(latency, packetLoss)
        qoe = qoe_model(service, latency, packetLoss, 1-rate)
        qoeArr.append(qoe)
        latency = 0
        packetLoss = 0
        rate = 0

  qoeArr = np.array(qoeArr)
  return qoeArr



 
class TroubleshootingEnv(gym.Env):

  metadata = {'render.modes': ['human']}
  #Initial state of the DRL problem
  def __init__(self):
    self.tenCurrentReward = deque()
    self.reward = 0
    self.done = False
    #print '\nInitiate\n'
  #Take an action and return a list of four things
  
  def getQoEState(self):
    qoeState = getStateFlex()
    return qoeState

  def step(self, action):
    f = open("/home/vantong/onos/apps/segmentrouting/app/src/main/java/org/onosproject/segmentrouting/RoutingPaths.csv","w+")
    f.write(str(action))
    f.close()

    action_state = 20
    time.sleep(1)

    qoeState = getStateFlex()
    #print "State: "+  str(qoeState) + ", Len: "+ str(len(qoeState))+"\n"

    while (len(qoeState) != action_state):
      time.sleep(3)
      qoeState = getStateFlex()
    self.reward = float(qoeState[action])
    self.tenCurrentReward.append(self.reward)
    if len(self.tenCurrentReward) > 10:
      tmp = self.tenCurrentReward.popleft()
    # Stopping criteria
    if np.mean(self.tenCurrentReward) <= 1.4 and len(self.tenCurrentReward) >= 10:
      self.done = True
    else:
      self.done = False
    return self.reward, self.done

  # Resets the state and other variables of the environment
  def reset(self):
    f = open("/home/vantong/Desktop/Pcap-Data/stop.csv", "w+")
    f.write("1")
    f.close()
    self.tenCurrentReward = deque()
    self.reward = 0
    self.done = False
    time.sleep(90)




