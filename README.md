# rl-sr
A novel service-centric segment routing for encrypted traffic

# **Implement the experiment with segment routing**

- Download and run ONOS 2.4 at: https://drive.google.com/file/d/1bWP5ad1TK6_3VwD--BB1ZlZdHviXrWMR/view?usp=sharing. This source code has the parameter measurement module and the segment routing algorithm.

*cd onos*

*bazel build onos*

*bazel run onos-local -- clean debug*

- Enable some application in ONOs:

*export ONOS_APPS=drivers,openflow-base,hostprovider,lldpprovider,netcfghostprovider,gui2,segmentrouting*

- Run mininet to create topo using the sr.py in Service-centric Remediation folder:

*sudo mn --custom sr.py --topo=sr '--controller=remote,ip=127.0.0.1,port=6653'*

# **Instructions for segment routing (SR) using reinforcement learning (RL)**

- The path in SR app is stored at https://github.com/vanvantong/rl-sr/blob/master/Service-centric%20Remediation/SR%20App%20in%20ONOS/segmentrouting/app/src/main/java/org/onosproject/segmentrouting/RoutingPaths.csv. To implement SR using RL, you need to run agent in https://github.com/vanvantong/rl-sr/tree/master/Service-centric%20Remediation/RL to update the routing paths in RoutingPaths.csv.
