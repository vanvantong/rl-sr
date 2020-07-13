/*
 * Copyright 2016-present Open Networking Foundation
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
package org.onosproject.provider.lldpcommon;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import io.netty.util.internal.StringUtil;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ONOSLLDP;
import org.onlab.util.Timer;
import org.onlab.util.Tools;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Link.Type;
import org.onosproject.net.LinkKey;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.DefaultLinkDescription;
import org.onosproject.net.link.LinkDescription;
import org.onosproject.net.link.ProbedLinkProvider;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.slf4j.Logger;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.onosproject.net.AnnotationKeys.PORT_NAME;
import static org.onosproject.net.PortNumber.portNumber;
import static org.onosproject.net.flow.DefaultTrafficTreatment.builder;
import static org.slf4j.LoggerFactory.getLogger;
import java.util.Arrays;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.lang.*;
import java.util.*;
import org.onosproject.net.device.PortStatistics;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList; 
import java.util.Queue; 
import org.onlab.packet.LLDPOrganizationalTLV;

import org.onosproject.persistence.PersistenceService;

/**
 * Run discovery process from a physical switch. Ports are initially labeled as
 * slow ports. When an LLDP is successfully received, label the remote port as
 * fast. Every probeRate milliseconds, loop over all fast ports and send an
 * LLDP, send an LLDP for a single slow port. Based on FlowVisor topology
 * discovery implementation.
 */
public class LinkDiscovery implements TimerTask {

    private static final String SCHEME_NAME = "linkdiscovery";
    private static final String ETHERNET = "ETHERNET";

    private final Logger log = getLogger(getClass());

    private final DeviceId deviceId;
    private final LinkDiscoveryContext context;

    private final Ethernet ethPacket;
    private final Ethernet bddpEth;

    private Timeout timeout;
    private volatile boolean isStopped;

    ArrayList<Timestamp> timePara = new ArrayList<Timestamp>();
    // Initializing a dictionary of link delay

    /*
    public Map<String, ArrayList<Float>> linkDelay = new HashMap<String, ArrayList<Float>>();
    public Map<String, ArrayList<Float>> linkPacketLoss = new HashMap<String, ArrayList<Float>>();
    public Map<String, ArrayList<Float>> linkRate = new HashMap<String, ArrayList<Float>>(); 
    */
    public Map<String, Queue<Float>> linkDelay = new HashMap<String, Queue<Float>>();
    public Map<String, Queue<Float>> linkPacketLoss = new HashMap<String, Queue<Float>>();
    public Map<String, Queue<Float>> linkRate = new HashMap<String, Queue<Float>>(); 

    public float link_capacity = 10;
    public int threshold_packet_loss = 3000;
    public static float lWeight = 0;
    public static String idLink = "";
    public int arrSize = 10;
    public static float plLink = 0;
    public static float rLink = 0;
    public static float dLink = 0;
    public static float plLLDP = 0;
    public static int validatedLink = 1;


    public static float rateQoE = 0;
    public static String de_link = "", pl_link = "", r_link = "";
    public static String srcLink = "", dstLink = "";

    public Map<String, Integer> countPara = new HashMap<String, Integer>();


    // Set of ports to be probed
    private final Map<Long, String> portMap = Maps.newConcurrentMap();
    /**
     * Instantiates discovery manager for the given physical switch. Creates a
     * generic LLDP packet that will be customized for the port it is sent out on.
     * Starts the the timer for the discovery process.
     *
     * @param deviceId  the physical switch
     * @param context discovery context
     */
    public LinkDiscovery(DeviceId deviceId, LinkDiscoveryContext context) {
        this.deviceId = deviceId;
        this.context = context;

        ethPacket = new Ethernet();
        ethPacket.setEtherType(Ethernet.TYPE_LLDP);
        ethPacket.setDestinationMACAddress(MacAddress.ONOS_LLDP);
        ethPacket.setPad(true);

        bddpEth = new Ethernet();
        bddpEth.setEtherType(Ethernet.TYPE_BSN);
        bddpEth.setDestinationMACAddress(MacAddress.BROADCAST);
        bddpEth.setPad(true);

        isStopped = true;
        start();
        log.debug("Started discovery manager for switch {}", deviceId);

    }

    public synchronized void stop() {
        if (!isStopped) {
            isStopped = true;
            timeout.cancel();
        } else {
            log.warn("LinkDiscovery stopped multiple times?");
        }
    }

    public synchronized void start() {
        if (isStopped) {
            isStopped = false;
            timeout = Timer.newTimeout(this, 0, MILLISECONDS);
        } else {
            log.warn("LinkDiscovery started multiple times?");
        }
    }

    public synchronized boolean isStopped() {
        return isStopped || timeout.isCancelled();
    }

    /**
     * Add physical port to discovery process.
     * Send out initial LLDP and label it as slow port.
     *
     * @param port the port
     */
    public void addPort(Port port) {
        Long portNum = port.number().toLong();
        String portName = port.annotations().value(PORT_NAME);
        if (portName == null) {
            portName = StringUtil.EMPTY_STRING;
        }

        boolean newPort = !containsPort(portNum);
        portMap.put(portNum, portName);

        boolean isMaster = context.mastershipService().isLocalMaster(deviceId);
        if (newPort && isMaster) {
            log.debug("Sending initial probe to port {}@{}", port.number().toLong(), deviceId);
            sendProbes(portNum, portName);
        }
    }

    /**
     * removed physical port from discovery process.
     * @param port the port number
     */
    public void removePort(PortNumber port) {
        portMap.remove(port.toLong());
    }

    /**
     * Handles an incoming LLDP packet. Creates link in topology and adds the
     * link for staleness tracking.
     *
     * @param packetContext packet context
     * @return true if handled
     */
    public boolean handleLldp(PacketContext packetContext) {
        Ethernet eth = packetContext.inPacket().parsed();
        if (eth == null) {
            return false;
        }

        if (processOnosLldp(packetContext, eth)) {
            return true;
        }

        if (processLldp(packetContext, eth)) {
            return true;
        }

        ONOSLLDP lldp = ONOSLLDP.parseLLDP(eth);

        if (lldp == null) {
            log.debug("Cannot parse the packet. It seems that it is not the lldp or bsn packet.");
        } else {
            log.debug("LLDP packet is dropped due to there are no handlers that properly handle this packet: {}",
                    lldp.toString());
        }

        return false;
    }




    public boolean saveArrPara(String id, Timestamp cts, float delay_link, float packet_loss, float rate_link){
        if(linkDelay.containsKey(id)){
            if(linkDelay.get(id).size() < arrSize){
                linkDelay.get(id).add(delay_link);
                linkPacketLoss.get(id).add(packet_loss);
                linkRate.get(id).add(rate_link);
            }else{
                //Remove para at the head of queue
                float removeLatencyPara = linkDelay.get(id).remove();
                float removePLPara = linkPacketLoss.get(id).remove();
                float removeLUPara = linkRate.get(id).remove();

                //Insert additional para
                linkDelay.get(id).add(delay_link);
                linkPacketLoss.get(id).add(packet_loss);
                linkRate.get(id).add(rate_link);
            }
        }else{
            linkDelay.put(id, new LinkedList<Float>());
            linkPacketLoss.put(id, new LinkedList<Float>());
            linkRate.put(id, new LinkedList<Float>());

            linkDelay.get(id).add(delay_link);
            linkPacketLoss.get(id).add(packet_loss);
            linkRate.get(id).add(rate_link);
        }

        if(linkDelay.get(id).size() == arrSize){
            de_link = linkDelay.get(id).toString().substring(1,linkDelay.get(id).toString().length()-1).replaceAll("\\s+","");
            pl_link = linkPacketLoss.get(id).toString().substring(1,linkPacketLoss.get(id).toString().length()-1).replaceAll("\\s+","");
            r_link =  linkRate.get(id).toString().substring(1,linkRate.get(id).toString().length()-1).replaceAll("\\s+","");
 
        }


        return true;
    }



    /*
     * Calculate the delay and packet loss on a link
     * 
     *
     * @param onoslldpDelay  The lldp packet on a link
     * @return the link delay
     */
    public String getDelayPL(ONOSLLDP onoslldpDelay, String id){

        //Calculate the link's delay using lldp
        long delay = 0;
        int count_packet_loss = 0;

        Timestamp current_timestamp = new Timestamp(System.currentTimeMillis());
        LLDPOrganizationalTLV tsLink = onoslldpDelay.getTimestampTLV();
        if(tsLink != null){
            ByteBuffer str_timestamp_tmp = ByteBuffer.allocate(8).put(tsLink.getInfoString());
            long current_ts_nano = (current_timestamp.getTime());
            str_timestamp_tmp.flip();
            long lldp_ts_nano = str_timestamp_tmp.getLong();
            delay = current_ts_nano - lldp_ts_nano;


            //Remove the timestamp when the lldp packet reaches the controller
            ONOSLLDP.removeElement(id, lldp_ts_nano);
            ArrayList<Long> arrPacketLossLLDP = ONOSLLDP.getArray(id);
            ArrayList<Long> arrTmpLoss = new ArrayList<Long>();

            if(arrPacketLossLLDP != null){
                if(arrPacketLossLLDP.size() >= 1){
                    for(int i = 0; i < arrPacketLossLLDP.size(); i++){
                        if(arrPacketLossLLDP.get(i) != null){
                            if(current_ts_nano - arrPacketLossLLDP.get(i) > threshold_packet_loss){
                                count_packet_loss  = count_packet_loss + 1;
                                arrTmpLoss.add(arrPacketLossLLDP.get(i));
                                
                            }
                        }

                    }
                    // Remove packets which are lost
                    if(arrTmpLoss.size() >= 1){
                        for(int j  = 0; j < arrTmpLoss.size(); j++){
                            ONOSLLDP.removeElement(id, arrTmpLoss.get(j));
                        }
                    }
                    arrTmpLoss.clear();

                }
            }

        }



        ////Disable the log
        //log.info("\nPackets loss of LLDP of {}: {}\n", id, count_packet_loss);

        return delay + "," + count_packet_loss;
    }



    //Get only link utilization on a link
    public float getLinkUtilization(DeviceId sDeviceId, PortNumber sPort, DeviceId dDeviceId, PortNumber dPort){
        float link_utilization = 0;

        float bSent_Src = 0;
        float bSent_Dst = 0;

        DeviceService deviceService = context.deviceService();
        if(deviceService != null){
            DeviceId dID = deviceService.getDevice(sDeviceId).id();
            if(dID != null){
                PortStatistics sPortSta = deviceService.getDeltaStatisticsForPort(dID, sPort);
                if(sPortSta != null){
                     bSent_Src = sPortSta.bytesSent();
                }
                PortStatistics dPortSta  = deviceService.getDeltaStatisticsForPort(dID, dPort);
                if(dPortSta != null){
                    bSent_Dst = dPortSta.bytesSent();
                }
                if(sPortSta != null && dPortSta != null){
                    link_utilization = (bSent_Src + bSent_Dst)/(link_capacity * 1000000);
                }
            }
            /*
            if(deviceService.getDeltaStatisticsForPort(deviceService.getDevice(sDeviceId).id(), sPort) != null){
            bSent_Src = deviceService.getDeltaStatisticsForPort(deviceService.getDevice(sDeviceId).id(), sPort).bytesSent();
            }
            if(deviceService.getDeltaStatisticsForPort(deviceService.getDevice(dDeviceId).id(), dPort) != null){
                bSent_Dst = deviceService.getDeltaStatisticsForPort(deviceService.getDevice(dDeviceId).id(), dPort).bytesSent();
            }
            */
        }

        

        return link_utilization;

    }

    private boolean processOnosLldp(PacketContext packetContext, Ethernet eth) {
        ONOSLLDP onoslldp = ONOSLLDP.parseONOSLLDP(eth);
        if (onoslldp != null) {
            Type lt;
            if (notMy(eth.getSourceMAC().toString())) {
                lt = Type.EDGE;
            } else {
                lt = eth.getEtherType() == Ethernet.TYPE_LLDP ?
                        Type.DIRECT : Type.INDIRECT;

                /* Verify MAC in LLDP packets */

                //Add 2s to maxDiscoveryDelay
                if (!ONOSLLDP.verify(onoslldp, context.lldpSecret(), context.maxDiscoveryDelay())) {
                    log.warn("LLDP Packet failed to validate, timestamp!");
                    validatedLink = 0;
                    return true;
                }
            }

            PortNumber srcPort = portNumber(onoslldp.getPort());
            PortNumber dstPort = packetContext.inPacket().receivedFrom().port();



            String idString = onoslldp.getDeviceString();
            if (!isNullOrEmpty(idString)) {
                try {
                    DeviceId srcDeviceId = DeviceId.deviceId(idString);
                    DeviceId dstDeviceId = packetContext.inPacket().receivedFrom().deviceId();

                    ConnectPoint src = new ConnectPoint(srcDeviceId, srcPort);
                    ConnectPoint dst = new ConnectPoint(dstDeviceId, dstPort);

                    LinkDescription ld = new DefaultLinkDescription(src, dst, lt);

                    srcLink = srcDeviceId.toString();
                    dstLink = dstDeviceId.toString();

                    //log.info("\nLink0 from {} to {}\n",srcLink,dstLink);
                    //if((srcLink.compareTo("of:0000000000000015") == 0 && dstLink.compareTo("of:0000000000000002") == 0) || (srcLink.compareTo("of:0000000000000002") == 0 && dstLink.compareTo("of:0000000000000016") == 0)){
                        //log.info("\nLink1 from {} to {}\n",srcLink,dstLink);
                        //Calculate the link utilization
                        rLink = getLinkUtilization(srcDeviceId, srcPort, dstDeviceId, dstPort);
                        if(rLink > 1){
                            rLink = 1;
                        }
                        //Tranh linkU = 0

                        //Calculate the delay and packet loss
                        Timestamp current_timestamp_para = new Timestamp(System.currentTimeMillis());
                        String idPort = srcDeviceId.toString()+"-"+srcPort.toString();
                        idLink = srcDeviceId.toString()+"-"+dstDeviceId.toString();
                        
                        String strDPL = getDelayPL(onoslldp, idPort);
                        if (strDPL != "" && strDPL != ","){
                            String[] s = strDPL.split(",");
                            dLink = Float.parseFloat(s[0]);
                            plLLDP = (Float.parseFloat(s[1]) > 1 ? 1 : Float.parseFloat(s[1]));
                        }
                        //log.info("\nLink from {}:{} to {}:{}, Delay: {} ms, Packet loss: {}, Link utilization: {}\n", srcDeviceId, srcPort, dstDeviceId, dstPort, dLink, plLLDP, rLink);
                        if(!((dLink == 0 && plLLDP == 0) || rLink == 0)){
                            //Save data to arr of link paras
                            saveArrPara(idPort, current_timestamp_para, dLink, plLLDP, rLink);
                        }else{
                            validatedLink = 0;
                        }
                    //}
/*
                    //Calculate the link utilization
                    rLink = getLinkUtilization(srcDeviceId, srcPort, dstDeviceId, dstPort);
                    if(rLink > 1){
                        rLink = 1;
                    }
                    //Tranh linkU = 0

                    //Calculate the delay and packet loss
                    Timestamp current_timestamp_para = new Timestamp(System.currentTimeMillis());
                    String idPort = srcDeviceId.toString()+"-"+srcPort.toString();
                    idLink = srcDeviceId.toString()+"-"+dstDeviceId.toString();
                    
                    String strDPL = getDelayPL(onoslldp, idPort);
                    if (strDPL != "" && strDPL != ","){
                        String[] s = strDPL.split(",");
                        dLink = Float.parseFloat(s[0]);
                        plLLDP = (Float.parseFloat(s[1]) > 1 ? 1 : Float.parseFloat(s[1]));
                    }
                    //log.info("\nLink from {}:{} to {}:{}, Delay: {} ms, Packet loss: {}, Link utilization: {}\n", srcDeviceId, srcPort, dstDeviceId, dstPort, dLink, plLLDP, rLink);
                    if(!((dLink == 0 && plLLDP == 0) || rLink == 0)){
                        //Save data to arr of link paras
                        saveArrPara(idPort, current_timestamp_para, dLink, plLLDP, rLink);
                    }else{
                        validatedLink = 0;
                    }
*/

                    

                    context.providerService().linkDetected(ld);
                    context.touchLink(LinkKey.linkKey(src, dst));

                } catch (IllegalStateException | IllegalArgumentException e) {
                    log.warn("There is a exception during link creation: {}", e.getMessage());
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private boolean processLldp(PacketContext packetContext, Ethernet eth) {
        ONOSLLDP onoslldp = ONOSLLDP.parseLLDP(eth);
        if (onoslldp != null) {
            Type lt = eth.getEtherType() == Ethernet.TYPE_LLDP ?
                    Type.DIRECT : Type.INDIRECT;

            DeviceService deviceService = context.deviceService();
            MacAddress srcChassisId = onoslldp.getChassisIdByMac();
            String srcPortName = onoslldp.getPortNameString();
            String srcPortDesc = onoslldp.getPortDescString();

            log.debug("srcChassisId:{}, srcPortName:{}, srcPortDesc:{}", srcChassisId, srcPortName, srcPortDesc);

            if (srcChassisId == null && srcPortDesc == null) {
                log.warn("there are no valid port id");
                return false;
            }

            Optional<Device> srcDevice = findSourceDeviceByChassisId(deviceService, srcChassisId);

            if (!srcDevice.isPresent()) {
                log.warn("source device not found. srcChassisId value: {}", srcChassisId);
                return false;
            }
            Optional<Port> sourcePort = findSourcePortByName(
                    srcPortName == null ? srcPortDesc : srcPortName,
                    deviceService,
                    srcDevice.get());

            if (!sourcePort.isPresent()) {
                log.warn("source port not found. sourcePort value: {}", sourcePort);
                return false;
            }

            PortNumber srcPort = sourcePort.get().number();
            PortNumber dstPort = packetContext.inPacket().receivedFrom().port();

            DeviceId srcDeviceId = srcDevice.get().id();
            DeviceId dstDeviceId = packetContext.inPacket().receivedFrom().deviceId();

            if (!sourcePort.get().isEnabled()) {
                log.debug("Ports are disabled. Cannot create a link between {}/{} and {}/{}",
                        srcDeviceId, sourcePort.get(), dstDeviceId, dstPort);
                return false;
            }

            ConnectPoint src = new ConnectPoint(srcDeviceId, srcPort);
            ConnectPoint dst = new ConnectPoint(dstDeviceId, dstPort);

            DefaultAnnotations annotations = DefaultAnnotations.builder()
                    .set(AnnotationKeys.PROTOCOL, SCHEME_NAME.toUpperCase())
                    .set(AnnotationKeys.LAYER, ETHERNET)
                    .build();

            LinkDescription ld = new DefaultLinkDescription(src, dst, lt, true, annotations);
            try {
                context.providerService().linkDetected(ld);
                context.setTtl(LinkKey.linkKey(src, dst), onoslldp.getTtlBySeconds());
            } catch (IllegalStateException e) {
                log.debug("There is a exception during link creation: {}", e);
                return true;
            }
            return true;
        }
        return false;
    }

    private Optional<Device> findSourceDeviceByChassisId(DeviceService deviceService, MacAddress srcChassisId) {
        Supplier<Stream<Device>> deviceStream = () ->
                StreamSupport.stream(deviceService.getAvailableDevices().spliterator(), false);
        Optional<Device> remoteDeviceOptional = deviceStream.get()
                .filter(device -> device.chassisId() != null
                        && MacAddress.valueOf(device.chassisId().value()).equals(srcChassisId))
                .findAny();

        if (remoteDeviceOptional.isPresent()) {
            log.debug("sourceDevice found by chassis id: {}", srcChassisId);
            return remoteDeviceOptional;
        } else {
            remoteDeviceOptional = deviceStream.get().filter(device ->
                    Tools.stream(deviceService.getPorts(device.id()))
                            .anyMatch(port -> port.annotations().keys().contains(AnnotationKeys.PORT_MAC)
                                    && MacAddress.valueOf(port.annotations().value(AnnotationKeys.PORT_MAC))
                                    .equals(srcChassisId)))
                    .findAny();
            if (remoteDeviceOptional.isPresent()) {
                log.debug("sourceDevice found by port mac: {}", srcChassisId);
                return remoteDeviceOptional;
            } else {
                return Optional.empty();
            }
        }
    }

    private Optional<Port> findSourcePortByName(String remotePortName,
                                                DeviceService deviceService,
                                                Device remoteDevice) {
        if (remotePortName == null) {
            return Optional.empty();
        }
        Optional<Port> remotePort = deviceService.getPorts(remoteDevice.id())
                .stream().filter(port -> Objects.equals(remotePortName,
                                                        port.annotations().value(AnnotationKeys.PORT_NAME)))
                .findAny();

        if (remotePort.isPresent()) {
            return remotePort;
        } else {
            return Optional.empty();
        }
    }

    // true if *NOT* this cluster's own probe.
    private boolean notMy(String mac) {
        // if we are using DEFAULT_MAC, clustering hadn't initialized, so conservative 'yes'
        String ourMac = context.fingerprint();
        if (ProbedLinkProvider.defaultMac().equalsIgnoreCase(ourMac)) {
            return true;
        }
        return !mac.equalsIgnoreCase(ourMac);
    }

    /**
     * Execute this method every t milliseconds. Loops over all ports
     * labeled as fast and sends out an LLDP. Send out an LLDP on a single slow
     * port.
     *
     * @param t timeout
     */
    @Override
    public void run(Timeout t) {
        if (isStopped()) {
            return;
        }

        if (context.mastershipService().isLocalMaster(deviceId)) {
            log.trace("Sending probes from {}", deviceId);
            ImmutableMap.copyOf(portMap).forEach(this::sendProbes);
        }

        if (!isStopped()) {
            timeout = t.timer().newTimeout(this, context.probeRate(), MILLISECONDS);
        }
    }

    /**
     * Creates packet_out LLDP for specified output port.
     *
     * @param portNumber the port
     * @param portDesc the port description
     * @return Packet_out message with LLDP data
     */
    private OutboundPacket createOutBoundLldp(Long portNumber, String portDesc) {
        if (portNumber == null) {
            return null;
        }
        ONOSLLDP lldp = getLinkProbe(portNumber, portDesc);
        if (lldp == null) {
            log.warn("Cannot get link probe with portNumber {} and portDesc {} for {} at LLDP packet creation.",
                    portNumber, portDesc, deviceId);
            return null;
        }
        ethPacket.setSourceMACAddress(context.fingerprint()).setPayload(lldp);
        return new DefaultOutboundPacket(deviceId,
                                         builder().setOutput(portNumber(portNumber)).build(),
                                         ByteBuffer.wrap(ethPacket.serialize()));
    }

    /**
     * Creates packet_out BDDP for specified output port.
     *
     * @param portNumber the port
     * @param portDesc the port description
     * @return Packet_out message with LLDP data
     */
    private OutboundPacket createOutBoundBddp(Long portNumber, String portDesc) {
        if (portNumber == null) {
            return null;
        }
        ONOSLLDP lldp = getLinkProbe(portNumber, portDesc);
        if (lldp == null) {
            log.warn("Cannot get link probe with portNumber {} and portDesc {} for {} at BDDP packet creation.",
                    portNumber, portDesc, deviceId);
            return null;
        }
        bddpEth.setSourceMACAddress(context.fingerprint()).setPayload(lldp);
        return new DefaultOutboundPacket(deviceId,
                                         builder().setOutput(portNumber(portNumber)).build(),
                                         ByteBuffer.wrap(bddpEth.serialize()));
    }

    private ONOSLLDP getLinkProbe(Long portNumber, String portDesc) {
        Device device = context.deviceService().getDevice(deviceId);
        if (device == null) {
            log.warn("Cannot find the device {}", deviceId);
            return null;
        }
        return ONOSLLDP.onosSecureLLDP(deviceId.toString(), device.chassisId(), portNumber.intValue(), portDesc,
                                       context.lldpSecret());
    }

    private void sendProbes(Long portNumber, String portDesc) {
        if (context.packetService() == null) {
            return;
        }
        log.trace("Sending probes out of {}@{}", portNumber, deviceId);
        //log.info("Sending probes out of {}@{}", portNumber, deviceId);
        OutboundPacket pkt = createOutBoundLldp(portNumber, portDesc);
        if (pkt != null) {
            context.packetService().emit(pkt);
        } else {
            log.warn("Cannot send lldp packet due to packet is null {}", deviceId);
        }
        if (context.useBddp()) {
            OutboundPacket bpkt = createOutBoundBddp(portNumber, portDesc);
            if (bpkt != null) {
                context.packetService().emit(bpkt);
            } else {
                log.warn("Cannot send bddp packet due to packet is null {}", deviceId);
            }
        }
    }

    public boolean containsPort(long portNumber) {
        return portMap.containsKey(portNumber);
    }
}