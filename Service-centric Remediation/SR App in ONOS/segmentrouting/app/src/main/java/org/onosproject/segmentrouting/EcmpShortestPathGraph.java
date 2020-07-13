/*
 * Copyright 2015-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.segmentrouting;

import org.onlab.graph.ScalarWeight;
import org.onosproject.net.DefaultLink;
import org.onosproject.net.DefaultPath;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link;
import org.onosproject.net.Path;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.io.*;
import java.util.Date;




import com.google.common.collect.ImmutableSet;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.ICMP6;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TCP;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.packet.VlanId;
import org.onlab.util.KryoNamespace;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.Event;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.criteria.EthCriterion;
import org.onosproject.net.flow.instructions.Instruction;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.link.LinkEvent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEvent;
import org.onosproject.net.topology.TopologyListener;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.MultiValuedTimestamp;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.LinkWeigher;
import java.util.ArrayList;
import java.sql.Timestamp;
import org.onosproject.net.topology.Topology;
import org.onosproject.segmentrouting.mcast.McastHandler;
import java.lang.Math; 
import java.io.*; 


/**
 * This class creates breadth-first-search (BFS) tree for a given root device
 * and returns paths from the root Device to leaf Devices (target devices).
 * The paths are snapshot paths at the point of the class instantiation.
 */
public class EcmpShortestPathGraph {
    LinkedList<DeviceId> deviceQueue = new LinkedList<>();
    LinkedList<Integer> distanceQueue = new LinkedList<>();
    HashMap<DeviceId, Integer> deviceSearched = new HashMap<>();
    HashMap<DeviceId, ArrayList<Link>> upstreamLinks = new HashMap<>();
    HashMap<DeviceId, ArrayList<Path>> paths = new HashMap<>();
    public HashMap<Integer, ArrayList<DeviceId>> distanceDeviceMap = new HashMap<>();
    DeviceId rootDevice;
    private SegmentRoutingManager srManager;
    private static final Logger log = LoggerFactory.getLogger(EcmpShortestPathGraph.class);

    public static Map<String, Float> link_Weight = new HashMap<String, Float>();

    /**
     * Constructor.
     *
     * @param rootDevice root of the BFS tree
     * @param srManager SegmentRoutingManager object
     */
    public EcmpShortestPathGraph(DeviceId rootDevice, SegmentRoutingManager srManager) {
        this.rootDevice = rootDevice;
        this.srManager = srManager;
        calcECMPShortestPathGraph();
    }

    /**
     * Calculates the BFS tree.
     */
   private void calcECMPShortestPathGraph() {

        deviceQueue.add(rootDevice);
        int currDistance = 0;
        distanceQueue.add(currDistance);
        deviceSearched.put(rootDevice, currDistance);
        while (!deviceQueue.isEmpty()) {
            DeviceId sw = deviceQueue.poll();
            Set<DeviceId> prevSw = Sets.newHashSet();
            currDistance = distanceQueue.poll();

            for (Link link : srManager.linkHandler.getDeviceEgressLinks(sw)) {
                if (srManager.linkHandler.avoidLink(link)) {
                    continue;
                }
                DeviceId reachedDevice = link.dst().deviceId();
                if (prevSw.contains(reachedDevice)) {
                    // Ignore LAG links between the same set of Devices
                    continue;
                } else  {
                    prevSw.add(reachedDevice);
                }

                Integer distance = deviceSearched.get(reachedDevice);
                if ((distance != null) && (distance < (currDistance + 1))) {
                    continue;
                }
                if (distance == null) {
                    // First time visiting this Device node
                    deviceQueue.add(reachedDevice);
                    distanceQueue.add(currDistance + 1);
                    deviceSearched.put(reachedDevice, currDistance + 1);

                    ArrayList<DeviceId> distanceSwArray = distanceDeviceMap
                            .get(currDistance + 1);
                    if (distanceSwArray == null) {
                        distanceSwArray = new ArrayList<>();
                        distanceSwArray.add(reachedDevice);
                        distanceDeviceMap.put(currDistance + 1, distanceSwArray);
                    } else {
                        distanceSwArray.add(reachedDevice);
                    }
                }

                ArrayList<Link> upstreamLinkArray =
                        upstreamLinks.get(reachedDevice);
                if (upstreamLinkArray == null) {
                    upstreamLinkArray = new ArrayList<>();
                    upstreamLinkArray.add(copyDefaultLink(link));
                    
                    upstreamLinks.put(reachedDevice, upstreamLinkArray);
                } else {
                    // ECMP links
                    upstreamLinkArray.add(copyDefaultLink(link));
                }
            }
        }
    }

    private void getDFSPaths(DeviceId dstDeviceDeviceId, Path path, ArrayList<Path> paths) {
        DeviceId rootDeviceDeviceId = rootDevice;
        if(upstreamLinks.get(dstDeviceDeviceId) != null){
            for (Link upstreamLink : upstreamLinks.get(dstDeviceDeviceId)) {
                /* Deep clone the path object */
                Path sofarPath;
                ArrayList<Link> sofarLinks = new ArrayList<>();
                if (path != null && !path.links().isEmpty()) {
                    sofarLinks.addAll(path.links());
                }
                sofarLinks.add(upstreamLink);
                sofarPath = new DefaultPath(ProviderId.NONE, sofarLinks, ScalarWeight.toWeight(0));
                if (upstreamLink.src().deviceId().equals(rootDeviceDeviceId)) {
                    paths.add(sofarPath);
                    return;
                } else {
                    getDFSPaths(upstreamLink.src().deviceId(), sofarPath, paths);
                }
            }
        }


    }

    /**
     * Return root Device for the graph.
     *
     * @return root Device
     */
    public DeviceId getRootDevice() {
        return rootDevice;
    }

        public String getDQLRoutingPath(){
            String tmpPath = "";
            try {
                BufferedReader brPath = new BufferedReader(new FileReader("/home/vantong/onos/apps/segmentrouting/app/src/main/java/org/onosproject/segmentrouting/RoutingPaths.csv"));
                String strCurrentLine = "";
                while ((strCurrentLine = brPath.readLine()) != null){
                    tmpPath = strCurrentLine;
                }
                brPath.close();
            }catch (Exception e) {
                e.printStackTrace();
            }                    
            if(tmpPath.compareTo("") == 0){
                tmpPath = "1";
            }
            return String.valueOf(Integer.parseInt(tmpPath) + 1);
        }

        public Set<Path> DQLRouting(DeviceId src, DeviceId dst){
            Set<Path> paths  = null;

            String routingPath = "of:0000000000000006-of:0000000000000007;of:0000000000000006-";
            try {
                BufferedReader brPath = new BufferedReader(new FileReader("/home/vantong/onos/apps/segmentrouting/app/src/main/java/org/onosproject/segmentrouting/RoutingPaths.csv"));
                String strCurrentLine = "";
                while ((strCurrentLine = brPath.readLine()) != null){

                    if(src.toString().compareTo("of:0000000000000006") == 0 && dst.toString().compareTo("of:0000000000000007") == 0 ){
                        
                        if(strCurrentLine.toString().compareTo("0")==0){
                            McastHandler.idPath = 1;
                            routingPath = routingPath + "of:0000000000000001,of:0000000000000001-of:0000000000000007;0";
                            log.info("\n**********Paths: {}**********\n", routingPath);
                        }else if(strCurrentLine.toString().compareTo("1")==0){
                            McastHandler.idPath = 2;
                            routingPath = routingPath + "of:0000000000000002,of:0000000000000002-of:0000000000000007;0";
                            log.info("\n**********Paths: {}**********\n", routingPath);
                        }else if(strCurrentLine.toString().compareTo("2")==0){
                            McastHandler.idPath = 3;
                            routingPath = routingPath + "of:0000000000000003,of:0000000000000003-of:0000000000000007;0";
                            log.info("\n**********Paths: {}**********\n", routingPath);
                        }else if(strCurrentLine.toString().compareTo("3")==0){
                            McastHandler.idPath = 4;
                            routingPath = routingPath + "of:0000000000000004,of:0000000000000004-of:0000000000000007;0";
                            log.info("\n**********Paths: {}**********\n", routingPath);
                        }else if(strCurrentLine.toString().compareTo("4")==0){
                            McastHandler.idPath = 5;
                            routingPath = routingPath + "of:0000000000000005,of:0000000000000005-of:0000000000000007;0";
                            log.info("\n**********Paths: {}**********\n", routingPath);
                        }
                    }

                }
                brPath.close();
            }catch (Exception e) {
                e.printStackTrace();
            }                    

            return paths;
        }


        //************************************
        //QoS Routing
        public Set<Path> weightRouting(DeviceId src, DeviceId dst){
            try {
                BufferedReader brWeight = new BufferedReader(new FileReader("/home/vantong/onos/providers/lldpcommon/src/main/java/org/onosproject/provider/lldpcommon/link_para.csv"));
                String strCurrentLine = "";
                while ((strCurrentLine = brWeight.readLine()) != null){
                    String[] tmp = strCurrentLine.split(";");
                    for(int i = 0; i < tmp.length; i++){
                        String[] s = tmp[i].split("\\*");
                        if(s.length == 4){
                            String[] d = s[1].split(",");
                            String[] p = s[2].split(","); 
                            String[] r = s[3].split(",");
                            float w = 0;
                            float delay = 0;
                            float packetloss = 0;
                            float rate = 0;
                            float tmp_delay = 0;
                            float tmp_packetloss = 0;
                            float tmp_rate = 0;


                            
                            if(d.length == 10 && p.length == 10 && r.length == 10){
                                
                                if(Float.parseFloat(d[9]) > 1000){
                                    delay = 1;
                                }else{
                                    delay = Float.parseFloat(d[9])/1000;
                                }
                                if(Float.parseFloat(p[9]) > 0.01){
                                    packetloss = 1;
                                }else{
                                    packetloss = Float.parseFloat(p[9]) * 100; // divide 0.01
                                }
                                if(Float.parseFloat(r[9]) > 1){
                                    rate = 1;
                                }else{
                                    rate = Float.parseFloat(r[9]);
                                }
                                w = (float)0.33 * (delay + packetloss + rate) * 1000;
                                
                            }
                            if(!link_Weight.keySet().contains(s[0])){
                                link_Weight.put(s[0], w);
                            }else{
                                link_Weight.replace(s[0], w);
                            }
                        }

                    }
                }  
                brWeight.close();
            }catch (Exception e) {
                e.printStackTrace();
            }
            Set<Link> linksToEnforce = new HashSet<Link>();
            LinkWeigher weightTopology = new SRLinkWeigher(srManager, src, linksToEnforce);
            Set<TopologyEdge> egdes = McastHandler.topologyService.getGraph(McastHandler.topologyService.currentTopology()).getEdges();
            for(TopologyEdge egde: egdes){
                weightTopology.weight(egde);
            }

            Set<Path> paths = McastHandler.topologyService.getPaths(McastHandler.topologyService.currentTopology(), src, dst, weightTopology);

            
            String routingPath = "of:0000000000000006-of:0000000000000007;of:0000000000000006-";
            if(src.toString().compareTo("of:0000000000000006") == 0 && dst.toString().compareTo("of:0000000000000007") == 0 ){
                
                //QoS-routing           
                if(paths.toString().contains("of:0000000000000001")){
                    McastHandler.idPath = 1;
                    routingPath = routingPath + "of:0000000000000001,of:0000000000000001-of:0000000000000007;0";
                    log.info("\n**********Paths: {}**********\n", paths);
                }else if(paths.toString().contains("of:0000000000000002")){
                    McastHandler.idPath = 2;
                    routingPath = routingPath + "of:0000000000000002,of:0000000000000002-of:0000000000000007;0";
                    log.info("\n**********Paths: {}**********\n", paths);
                }else if(paths.toString().contains("of:0000000000000003")){
                    McastHandler.idPath = 3;
                    routingPath = routingPath + "of:0000000000000003,of:0000000000000003-of:0000000000000007;0";
                    log.info("\n**********Paths: {}**********\n", paths);
                }else if(paths.toString().contains("of:0000000000000004")){
                    McastHandler.idPath = 4;
                    routingPath = routingPath + "of:0000000000000004,of:0000000000000004-of:0000000000000007;0";
                    log.info("\n**********Paths: {}**********\n", paths);
                }else if(paths.toString().contains("of:0000000000000005")){
                    McastHandler.idPath = 5;
                    routingPath = routingPath + "of:0000000000000005,of:0000000000000005-of:0000000000000007;0";
                    log.info("\n**********Paths: {}**********\n", paths);
                }
                
                //Normal routing
                //routingPath = "of:0000000000000006-of:0000000000000007;of:0000000000000006-of:0000000000000003,of:0000000000000003-of:0000000000000007;0";

                
                //Write the routing paths to file in order to estimate QoE
                
                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter("/home/vantong/onos/apps/segmentrouting/app/src/main/java/org/onosproject/segmentrouting/SRPaths.csv"));
                    writer.write(routingPath+'\n');
                    writer.close();
                }catch (Exception e) {
                    e.printStackTrace();
                }
                
            }                     
            return paths;
        }     


    /**
     * Return the computed ECMP paths from the root Device to a given Device in
     * the network.
     *
     * @param targetDevice the target Device
     * @return the list of ECMP Paths from the root Device to the target Device
     */
    public ArrayList<Path> getECMPPaths(DeviceId targetDevice) {
        
        ArrayList<Path> pathArray = paths.get(targetDevice);
        ArrayList<Path> pathArrayTmp = new ArrayList<Path>();
        if (pathArray == null && deviceSearched.containsKey(
                targetDevice)) {
            pathArray = new ArrayList<>();
            DeviceId sw = targetDevice;

            /*
            //Change to QoS routing algorithm
            Set<Path> tmpArr = weightRouting(rootDevice, targetDevice);
            */

            /*
            //DQL Routing
            Set<Path> tmpArr = DQLRouting(rootDevice, targetDevice);
            */
            
                        
            //Identifying the routing paths using DFS
            getDFSPaths(sw, null, pathArray);
            paths.put(targetDevice, pathArray);
        }



        
        String strPath = getDQLRoutingPath();
        if(pathArray.size() > 0){
            if(targetDevice.toString().contains("of:0000000000000007")){
                for(int i = 0; i < pathArray.size(); i++){
                    String tmpStr = pathArray.get(i).src().toString();
                    tmpStr = tmpStr.substring(0,tmpStr.length()-1);
                    if(tmpStr.contains(strPath)){
                        pathArrayTmp.add(pathArray.get(i));
                        break;
                    }
                    
                }
                if(pathArrayTmp.size() > 0){
                    pathArray = pathArrayTmp;
                }
            }
        }
        
        
        return pathArray;
    }

    /**
     * Return the complete info of the computed ECMP paths for each Device
     * learned in multiple iterations from the root Device.
     *
     * @return the hash table of Devices learned in multiple Dijkstra
     *         iterations and corresponding ECMP paths to it from the root
     *         Device
     */
    public HashMap<Integer, HashMap<DeviceId,
            ArrayList<Path>>> getCompleteLearnedDeviceesAndPaths() {

        HashMap<Integer, HashMap<DeviceId, ArrayList<Path>>> pathGraph = new HashMap<>();

        for (Integer itrIndx : distanceDeviceMap.keySet()) {
            HashMap<DeviceId, ArrayList<Path>> swMap = new HashMap<>();
            for (DeviceId sw : distanceDeviceMap.get(itrIndx)) {
                swMap.put(sw, getECMPPaths(sw));
            }
            pathGraph.put(itrIndx, swMap);
        }

        return pathGraph;
    }

    /**
     * Returns the complete info of the computed ECMP paths for each target device
     * learned in multiple iterations from the root Device. The computed info
     * returned is per iteration (Integer key of outer HashMap). In each
     * iteration, for the target devices reached (DeviceId key of inner HashMap),
     * the ECMP paths are detailed (2D array).
     *
     * @return the hash table of target Devices learned in multiple Dijkstra
     *         iterations and corresponding ECMP paths in terms of Devices to
     *         be traversed (via) from the root Device to the target Device
     */
    public HashMap<Integer, HashMap<DeviceId,
            ArrayList<ArrayList<DeviceId>>>> getAllLearnedSwitchesAndVia() {

        HashMap<Integer, HashMap<DeviceId, ArrayList<ArrayList<DeviceId>>>> deviceViaMap = new HashMap<>();

        for (Integer itrIndx : distanceDeviceMap.keySet()) {
            HashMap<DeviceId, ArrayList<ArrayList<DeviceId>>> swMap = new HashMap<>();

            for (DeviceId sw : distanceDeviceMap.get(itrIndx)) {
                ArrayList<ArrayList<DeviceId>> swViaArray = new ArrayList<>();
                for (Path path : getECMPPaths(sw)) {
                    ArrayList<DeviceId> swVia = new ArrayList<>();
                    for (Link link : path.links()) {
                        if (link.src().deviceId().equals(rootDevice)) {
                            /* No need to add the root Device again in
                             * the Via list
                             */
                            continue;
                        }
                        swVia.add(link.src().deviceId());
                    }
                    swViaArray.add(swVia);
                }
                swMap.put(sw, swViaArray);
            }
            deviceViaMap.put(itrIndx, swMap);
        }
        return deviceViaMap;
    }


    private Link copyDefaultLink(Link link) {
        DefaultLink src = (DefaultLink) link;
        DefaultLink defaultLink = DefaultLink.builder()
                .providerId(src.providerId())
                .src(src.src())
                .dst(src.dst())
                .type(src.type())
                .annotations(src.annotations())
                .build();

        return defaultLink;
    }

    @Override
    public String toString() {
        StringBuilder sBuilder = new StringBuilder();
        for (Device device: srManager.deviceService.getDevices()) {
            if (!device.id().equals(rootDevice)) {
                sBuilder.append("\r\n  Paths from " + rootDevice + " to "
                                + device.id());
                ArrayList<Path> paths = getECMPPaths(device.id());
                if (paths != null) {
                    for (Path path : paths) {
                        sBuilder.append("\r\n       == "); // equal cost paths delimiter
                        for (int i = path.links().size() - 1; i >= 0; i--) {
                            Link link = path.links().get(i);
                            sBuilder.append(" : " + link.src() + " -> " + link.dst());
                        }
                    }
                } else {
                    sBuilder.append("\r\n       == no paths");
                }
            }
        }
        return sBuilder.toString();
    }
}