# rl-sr
A novel service-centric segment routing for encrypted traffic

#**Implement the experiment with segment routing

- Download and run ONOS 2.4 at: xxx. This source code has the parameter measurement module and the segment routing algorithm.

*cd onos*

*bazel build onos*

*bazel run onos-local -- clean debug*

-Enable some application in ONOs:

*export ONOS_APPS=drivers,openflow-base,hostprovider,lldpprovider,netcfghostprovider,gui2,segmentrouting*

- Run mininet to create topo using the sr.py in Service-centric Remediation folder:

*sudo mn --custom sr.py --topo=sr '--controller=remote,ip=127.0.0.1,port=6653'*

