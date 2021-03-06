/*
 * Copyright 2014-2015 Open Networking Laboratory
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
package org.onosproject.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.onlab.packet.ChassisId;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.HostLocation;
import org.onosproject.net.Link;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.Port;
import org.onosproject.net.SparseAnnotations;
import org.onosproject.net.device.DefaultDeviceDescription;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceProvider;
import org.onosproject.net.device.DeviceProviderRegistry;
import org.onosproject.net.device.DeviceProviderService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.host.DefaultHostDescription;
import org.onosproject.net.host.HostProvider;
import org.onosproject.net.host.HostProviderRegistry;
import org.onosproject.net.host.HostProviderService;
import org.onosproject.net.link.DefaultLinkDescription;
import org.onosproject.net.link.LinkProvider;
import org.onosproject.net.link.LinkProviderRegistry;
import org.onosproject.net.link.LinkProviderService;
import org.onosproject.net.provider.ProviderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onosproject.net.DeviceId.deviceId;
import static org.onosproject.net.PortNumber.portNumber;
import static org.onosproject.net.device.DeviceEvent.Type.DEVICE_ADDED;
import static org.onosproject.net.device.DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED;

/**
 * Provider of devices and links parsed from a JSON configuration structure.
 */
class ConfigProvider implements DeviceProvider, LinkProvider, HostProvider {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ProviderId PID =
            new ProviderId("cfg", "org.onosproject.rest", true);

    private static final String UNKNOWN = "unknown";

    private CountDownLatch deviceLatch;

    private final JsonNode cfg;
    private final DeviceService deviceService;

    private final DeviceProviderRegistry deviceProviderRegistry;
    private final LinkProviderRegistry linkProviderRegistry;
    private final HostProviderRegistry hostProviderRegistry;

    private DeviceProviderService deviceProviderService;
    private LinkProviderService linkProviderService;
    private HostProviderService hostProviderService;

    private DeviceListener deviceEventCounter = new DeviceEventCounter();
    private List<ConnectPoint> connectPoints = Lists.newArrayList();

    /**
     * Creates a new configuration provider.
     *
     * @param cfg                    JSON configuration
     * @param deviceService          device service
     * @param deviceProviderRegistry device provider registry
     * @param linkProviderRegistry   link provider registry
     * @param hostProviderRegistry   host provider registry
     */
    ConfigProvider(JsonNode cfg,
                   DeviceService deviceService,
                   DeviceProviderRegistry deviceProviderRegistry,
                   LinkProviderRegistry linkProviderRegistry,
                   HostProviderRegistry hostProviderRegistry) {
        this.cfg = checkNotNull(cfg, "Configuration cannot be null");
        this.deviceService = checkNotNull(deviceService, "Device service cannot be null");
        this.deviceProviderRegistry = checkNotNull(deviceProviderRegistry, "Device provider registry cannot be null");
        this.linkProviderRegistry = checkNotNull(linkProviderRegistry, "Link provider registry cannot be null");
        this.hostProviderRegistry = checkNotNull(hostProviderRegistry, "Host provider registry cannot be null");
    }

    /**
     * Parses the given JSON and provides links as configured.
     */
    void parse() {
        try {
            register();
            parseDevices();
            parseLinks();
            parseHosts();
            addMissingPorts();
        } finally {
            unregister();
        }
    }

    private void register() {
        deviceProviderService = deviceProviderRegistry.register(this);
        linkProviderService = linkProviderRegistry.register(this);
        hostProviderService = hostProviderRegistry.register(this);
    }

    private void unregister() {
        deviceProviderRegistry.unregister(this);
        linkProviderRegistry.unregister(this);
        hostProviderRegistry.unregister(this);
    }

    // Parses the given JSON and provides devices.
    private void parseDevices() {
        try {
            JsonNode nodes = cfg.get("devices");
            if (nodes != null) {
                prepareForDeviceEvents(nodes.size());
                for (JsonNode node : nodes) {
                    parseDevice(node);

                    // FIXME: hack to make sure device attributes take
                    // This will be fixed when GossipDeviceStore uses ECM
                    parseDevice(node);
                }
            }
        } finally {
            waitForDeviceEvents();
        }
    }

    // Parses the given node with device data and supplies the device.
    private void parseDevice(JsonNode node) {
        URI uri = URI.create(get(node, "uri"));
        Device.Type type = Device.Type.valueOf(get(node, "type", "SWITCH"));
        String mfr = get(node, "mfr", UNKNOWN);
        String hw = get(node, "hw", UNKNOWN);
        String sw = get(node, "sw", UNKNOWN);
        String serial = get(node, "serial", UNKNOWN);
        ChassisId cid = new ChassisId(get(node, "mac", "000000000000"));
        SparseAnnotations annotations = annotations(node.get("annotations"));

        DeviceDescription desc =
                new DefaultDeviceDescription(uri, type, mfr, hw, sw, serial,
                                             cid, annotations);
        DeviceId deviceId = deviceId(uri);
        deviceProviderService.deviceConnected(deviceId, desc);

        JsonNode ports = node.get("ports");
        if (ports != null) {
            parsePorts(deviceId, ports);
        }
    }

    // Parses the given node with list of device ports.
    private void parsePorts(DeviceId deviceId, JsonNode nodes) {
        List<PortDescription> ports = new ArrayList<>();
        for (JsonNode node : nodes) {
            ports.add(parsePort(node));
        }
        deviceProviderService.updatePorts(deviceId, ports);
    }

    // Parses the given node with port information.
    private PortDescription parsePort(JsonNode node) {
        Port.Type type = Port.Type.valueOf(node.path("type").asText("COPPER"));
        return new DefaultPortDescription(portNumber(node.path("port").asLong(0)),
                                          node.path("enabled").asBoolean(true),
                                          type, node.path("speed").asLong(1_000));
    }

    // Parses the given JSON and provides links as configured.
    private void parseLinks() {
        JsonNode nodes = cfg.get("links");
        if (nodes != null) {
            for (JsonNode node : nodes) {
                parseLink(node, false);
                if (!node.has("halfplex")) {
                    parseLink(node, true);
                }
            }
        }
    }

    // Parses the given node with link data and supplies the link.
    private void parseLink(JsonNode node, boolean reverse) {
        ConnectPoint src = connectPoint(get(node, "src"));
        ConnectPoint dst = connectPoint(get(node, "dst"));
        Link.Type type = Link.Type.valueOf(get(node, "type", "DIRECT"));
        SparseAnnotations annotations = annotations(node.get("annotations"));

        DefaultLinkDescription desc = reverse ?
                new DefaultLinkDescription(dst, src, type, annotations) :
                new DefaultLinkDescription(src, dst, type, annotations);
        linkProviderService.linkDetected(desc);

        connectPoints.add(src);
        connectPoints.add(dst);
    }

    // Parses the given JSON and provides hosts as configured.
    private void parseHosts() {
        try {
            JsonNode nodes = cfg.get("hosts");
            if (nodes != null) {
                for (JsonNode node : nodes) {
                    parseHost(node);

                    // FIXME: hack to make sure host attributes take
                    // This will be fixed when GossipHostStore uses ECM
                    parseHost(node);
                }
            }
        } finally {
            hostProviderRegistry.unregister(this);
        }
    }

    // Parses the given node with host data and supplies the host.
    private void parseHost(JsonNode node) {
        MacAddress mac = MacAddress.valueOf(get(node, "mac"));
        VlanId vlanId = VlanId.vlanId((short) node.get("vlan").asInt(VlanId.UNTAGGED));
        HostId hostId = HostId.hostId(mac, vlanId);
        SparseAnnotations annotations = annotations(node.get("annotations"));
        HostLocation location = new HostLocation(connectPoint(get(node, "location")), 0);

        String[] ipStrings = get(node, "ip", "").split(",");
        Set<IpAddress> ips = new HashSet<>();
        for (String ip : ipStrings) {
            ips.add(IpAddress.valueOf(ip.trim()));
        }

        DefaultHostDescription desc =
                new DefaultHostDescription(mac, vlanId, location, ips, annotations);
        hostProviderService.hostDetected(hostId, desc);

        connectPoints.add(location);
    }

    // Adds any missing device ports for configured links and host locations.
    private void addMissingPorts() {
        deviceService.getDevices().forEach(this::addMissingPorts);
    }

    // Adds any missing device ports.
    private void addMissingPorts(Device device) {
        List<Port> ports = deviceService.getPorts(device.id());
        Set<ConnectPoint> existing = ports.stream()
                .map(p -> new ConnectPoint(device.id(), p.number()))
                .collect(Collectors.toSet());
        Set<ConnectPoint> missing = connectPoints.stream()
                .filter(cp -> !existing.contains(cp))
                .collect(Collectors.toSet());

        if (!missing.isEmpty()) {
            List<PortDescription> newPorts = Lists.newArrayList();
            ports.forEach(p -> newPorts.add(description(p)));
            missing.forEach(cp -> newPorts.add(description(cp)));
            deviceProviderService.updatePorts(device.id(), newPorts);
        }
    }

    // Creates a port description from the specified port.
    private PortDescription description(Port p) {
        return new DefaultPortDescription(p.number(), p.isEnabled(), p.type(), p.portSpeed());
    }

    // Creates a port description from the specified connection point.
    private PortDescription description(ConnectPoint cp) {
        return new DefaultPortDescription(cp.port(), true);
    }


    // Produces set of annotations from the given JSON node.
    private SparseAnnotations annotations(JsonNode node) {
        if (node == null) {
            return null;
        }

        DefaultAnnotations.Builder builder = DefaultAnnotations.builder();
        Iterator<String> it = node.fieldNames();
        while (it.hasNext()) {
            String k = it.next();
            builder.set(k, node.get(k).asText());
        }
        return builder.build();
    }

    // Produces a connection point from the specified uri/port text.
    private ConnectPoint connectPoint(String text) {
        int i = text.lastIndexOf("/");
        return new ConnectPoint(deviceId(text.substring(0, i)),
                                portNumber(text.substring(i + 1)));
    }

    // Returns string form of the named property in the given JSON object.
    private String get(JsonNode node, String name) {
        return node.path(name).asText();
    }

    // Returns string form of the named property in the given JSON object.
    private String get(JsonNode node, String name, String defaultValue) {
        return node.path(name).asText(defaultValue);
    }

    @Override
    public void roleChanged(DeviceId device, MastershipRole newRole) {
        deviceProviderService.receivedRoleReply(device, newRole, newRole);
    }

    @Override
    public void triggerProbe(DeviceId deviceId) {
    }

    @Override
    public void triggerProbe(Host host) {
    }

    @Override
    public ProviderId id() {
        return PID;
    }

    @Override
    public boolean isReachable(DeviceId device) {
        return true;
    }

    /**
     * Prepares to count device added/available/removed events.
     *
     * @param count number of events to count
     */
    protected void prepareForDeviceEvents(int count) {
        deviceLatch = new CountDownLatch(count);
        deviceService.addListener(deviceEventCounter);
    }

    /**
     * Waits for all expected device added/available/removed events.
     */
    protected void waitForDeviceEvents() {
        try {
            deviceLatch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.warn("Device events did not arrive in time");
        }
        deviceService.removeListener(deviceEventCounter);
    }

    // Counts down number of device added/available/removed events.
    private class DeviceEventCounter implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceEvent.Type type = event.type();
            if (type == DEVICE_ADDED || type == DEVICE_AVAILABILITY_CHANGED) {
                deviceLatch.countDown();
            }
        }
    }

}
