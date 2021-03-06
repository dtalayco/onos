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
package org.onosproject.net.device.impl;

import com.google.common.collect.Lists;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.NodeId;
import org.onosproject.event.AbstractListenerRegistry;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.mastership.MastershipEvent;
import org.onosproject.mastership.MastershipListener;
import org.onosproject.mastership.MastershipService;
import org.onosproject.mastership.MastershipTerm;
import org.onosproject.mastership.MastershipTermService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.MastershipRole;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DefaultDeviceDescription;
import org.onosproject.net.device.DefaultPortDescription;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceClockProviderService;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceProvider;
import org.onosproject.net.device.DeviceProviderRegistry;
import org.onosproject.net.device.DeviceProviderService;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.DeviceStore;
import org.onosproject.net.device.DeviceStoreDelegate;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.provider.AbstractProviderRegistry;
import org.onosproject.net.provider.AbstractProviderService;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.MastershipRole.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provides implementation of the device SB &amp; NB APIs.
 */
@Component(immediate = true)
@Service
public class DeviceManager
        extends AbstractProviderRegistry<DeviceProvider, DeviceProviderService>
        implements DeviceService, DeviceAdminService, DeviceProviderRegistry {

    private static final String DEVICE_ID_NULL = "Device ID cannot be null";
    private static final String PORT_NUMBER_NULL = "Port number cannot be null";
    private static final String DEVICE_DESCRIPTION_NULL = "Device description cannot be null";
    private static final String PORT_DESCRIPTION_NULL = "Port description cannot be null";
    private static final String ROLE_NULL = "Role cannot be null";

    private final Logger log = getLogger(getClass());

    protected final AbstractListenerRegistry<DeviceEvent, DeviceListener> listenerRegistry =
            new AbstractListenerRegistry<>();

    private final DeviceStoreDelegate delegate = new InternalStoreDelegate();

    private final MastershipListener mastershipListener = new InternalMastershipListener();

    private ScheduledExecutorService backgroundService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceStore store;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EventDeliveryService eventDispatcher;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ClusterService clusterService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipTermService termService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceClockProviderService deviceClockProviderService;

    @Activate
    public void activate() {
        backgroundService = newSingleThreadScheduledExecutor(groupedThreads("onos/device", "manager-background"));

        store.setDelegate(delegate);
        eventDispatcher.addSink(DeviceEvent.class, listenerRegistry);
        mastershipService.addListener(mastershipListener);

        backgroundService.scheduleWithFixedDelay(new Runnable() {

            @Override
            public void run() {
                try {
                    mastershipCheck();
                } catch (Exception e) {
                    log.error("Exception thrown during integrity check", e);
                }
            }
        }, 1, 1, TimeUnit.MINUTES);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        backgroundService.shutdown();

        store.unsetDelegate(delegate);
        mastershipService.removeListener(mastershipListener);
        eventDispatcher.removeSink(DeviceEvent.class);
        log.info("Stopped");
    }

    @Override
    public int getDeviceCount() {
        return store.getDeviceCount();
    }

    @Override
    public Iterable<Device> getDevices() {
        return store.getDevices();
    }

    @Override
    public Iterable<Device> getAvailableDevices() {
        return store.getAvailableDevices();
    }

    @Override
    public Device getDevice(DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        return store.getDevice(deviceId);
    }

    @Override
    public MastershipRole getRole(DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        return mastershipService.getLocalRole(deviceId);
    }

    @Override
    public List<Port> getPorts(DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        return store.getPorts(deviceId);
    }

    @Override
    public List<PortStatistics> getPortStatistics(DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        return store.getPortStatistics(deviceId);
    }

    @Override
    public Port getPort(DeviceId deviceId, PortNumber portNumber) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        checkNotNull(portNumber, PORT_NUMBER_NULL);
        return store.getPort(deviceId, portNumber);
    }

    @Override
    public boolean isAvailable(DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        return store.isAvailable(deviceId);
    }

    // Check a device for control channel connectivity.
    private boolean isReachable(DeviceId deviceId) {
        if (deviceId == null) {
            return false;
        }
        DeviceProvider provider = getProvider(deviceId);
        if (provider != null) {
            return provider.isReachable(deviceId);
        } else {
            log.debug("Provider not found for {}", deviceId);
            return false;
        }
    }

    @Override
    public void removeDevice(DeviceId deviceId) {
        checkNotNull(deviceId, DEVICE_ID_NULL);
        DeviceEvent event = store.removeDevice(deviceId);
        if (event != null) {
            log.info("Device {} administratively removed", deviceId);
            post(event);
        }
    }

    @Override
    public void addListener(DeviceListener listener) {
        listenerRegistry.addListener(listener);
    }

    @Override
    public void removeListener(DeviceListener listener) {
        listenerRegistry.removeListener(listener);
    }

    @Override
    protected DeviceProviderService createProviderService(
            DeviceProvider provider) {
        return new InternalDeviceProviderService(provider);
    }

    /**
     * Checks if all the reachable devices have a valid mastership role.
     */
    private void mastershipCheck() {
        log.debug("Checking mastership");
        for (Device device : getDevices()) {
            final DeviceId deviceId = device.id();
            log.trace("Checking device {}", deviceId);

            if (!isReachable(deviceId)) {
                continue;
            }

            if (mastershipService.getLocalRole(deviceId) != NONE) {
                continue;
            }

            log.info("{} is reachable but did not have a valid role, reasserting", deviceId);

            // isReachable but was not MASTER or STANDBY, get a role and apply
            // Note: NONE triggers request to MastershipService
            reassertRole(deviceId, NONE);
        }
    }

    // Personalized device provider service issued to the supplied provider.
    private class InternalDeviceProviderService
            extends AbstractProviderService<DeviceProvider>
            implements DeviceProviderService {

        InternalDeviceProviderService(DeviceProvider provider) {
            super(provider);
        }

        /**
         * Apply role in reaction to provider event.
         *
         * @param deviceId  device identifier
         * @param newRole   new role to apply to the device
         * @return true if the request was sent to provider
         */
        private boolean applyRole(DeviceId deviceId, MastershipRole newRole) {

            if (newRole.equals(MastershipRole.NONE)) {
                //no-op
                return true;
            }

            DeviceProvider provider = provider();
            if (provider == null) {
                log.warn("Provider for {} was not found. Cannot apply role {}", deviceId, newRole);
                return false;
            }
            provider.roleChanged(deviceId, newRole);
            // not triggering probe when triggered by provider service event

            return true;
        }


        @Override
        public void deviceConnected(DeviceId deviceId,
                                    DeviceDescription deviceDescription) {
            checkNotNull(deviceId, DEVICE_ID_NULL);
            checkNotNull(deviceDescription, DEVICE_DESCRIPTION_NULL);
            checkValidity();

            log.info("Device {} connected", deviceId);
            final NodeId myNodeId = clusterService.getLocalNode().id();

            // check my Role
            mastershipService.requestRoleFor(deviceId);
            final MastershipTerm term = termService.getMastershipTerm(deviceId);
            if (term == null || !myNodeId.equals(term.master())) {
                log.info("Role of this node is STANDBY for {}", deviceId);
                // TODO: Do we need to explicitly tell the Provider that
                // this instance is not the MASTER
                applyRole(deviceId, MastershipRole.STANDBY);
            } else {
                log.info("Role of this node is MASTER for {}", deviceId);
                // tell clock provider if this instance is the master
                deviceClockProviderService.setMastershipTerm(deviceId, term);
                applyRole(deviceId, MastershipRole.MASTER);
            }

            DeviceEvent event = store.createOrUpdateDevice(provider().id(),
                                                           deviceId, deviceDescription);

            // If there was a change of any kind, tell the provider
            // that this instance is the master.
            if (event != null) {
                log.trace("event: {} {}", event.type(), event);
                post(event);
            }
        }

        @Override
        public void deviceDisconnected(DeviceId deviceId) {
            checkNotNull(deviceId, DEVICE_ID_NULL);
            checkValidity();

            log.info("Device {} disconnected from this node", deviceId);

            DeviceEvent event = null;
            List<Port> ports = store.getPorts(deviceId);
            List<PortDescription> descs = Lists.newArrayList();
            ports.forEach(port ->
                descs.add(new DefaultPortDescription(port.number(),
                                                     false, port.type(),
                                                     port.portSpeed())));
            store.updatePorts(this.provider().id(), deviceId, descs);
            try {
                event = store.markOffline(deviceId);
            } catch (IllegalStateException e) {
                log.warn("Failed to mark {} offline", deviceId);
                // only the MASTER should be marking off-line in normal cases,
                // but if I was the last STANDBY connection, etc. and no one else
                // was there to mark the device offline, this instance may need to
                // temporarily request for Master Role and mark offline.

                //there are times when this node will correctly have mastership, BUT
                //that isn't reflected in the ClockManager before the device disconnects.
                //we want to let go of the device anyways, so make sure this happens.

                // FIXME: Store semantics leaking out as IllegalStateException.
                //  Consider revising store API to handle this scenario.

                MastershipRole role = mastershipService.requestRoleFor(deviceId);
                MastershipTerm term = termService.getMastershipTerm(deviceId);
                final NodeId myNodeId = clusterService.getLocalNode().id();
                // TODO: Move this type of check inside device clock manager, etc.
                if (myNodeId.equals(term.master())) {
                    log.info("Retry marking {} offline", deviceId);
                    deviceClockProviderService.setMastershipTerm(deviceId, term);
                    event = store.markOffline(deviceId);
                } else {
                    log.info("Failed again marking {} offline. {}", deviceId, role);
                }
            } finally {
                //relinquish master role and ability to be backup.
                mastershipService.relinquishMastership(deviceId);

                if (event != null) {
                    log.info("Device {} disconnected from cluster", deviceId);
                    post(event);
                }
            }
        }

        @Override
        public void updatePorts(DeviceId deviceId,
                                List<PortDescription> portDescriptions) {
            checkNotNull(deviceId, DEVICE_ID_NULL);
            checkNotNull(portDescriptions,
                         "Port descriptions list cannot be null");
            checkValidity();
            if (!deviceClockProviderService.isTimestampAvailable(deviceId)) {
                // Never been a master for this device
                // any update will be ignored.
                log.trace("Ignoring {} port updates on standby node. {}", deviceId, portDescriptions);
                return;
            }

            List<DeviceEvent> events = store.updatePorts(this.provider().id(),
                                                         deviceId, portDescriptions);
            for (DeviceEvent event : events) {
                post(event);
            }
        }

        @Override
        public void portStatusChanged(DeviceId deviceId,
                                      PortDescription portDescription) {
            checkNotNull(deviceId, DEVICE_ID_NULL);
            checkNotNull(portDescription, PORT_DESCRIPTION_NULL);
            checkValidity();

            if (!deviceClockProviderService.isTimestampAvailable(deviceId)) {
                // Never been a master for this device
                // any update will be ignored.
                log.trace("Ignoring {} port update on standby node. {}", deviceId, portDescription);
                return;
            }

            final DeviceEvent event = store.updatePortStatus(this.provider().id(),
                                                             deviceId, portDescription);
            if (event != null) {
                log.info("Device {} port {} status changed", deviceId, event
                        .port().number());
                post(event);
            }
        }

        @Override
        public void receivedRoleReply(
                DeviceId deviceId, MastershipRole requested, MastershipRole response) {
            // Several things can happen here:
            // 1. request and response match
            // 2. request and response don't match
            // 3. MastershipRole and requested match (and 1 or 2 are true)
            // 4. MastershipRole and requested don't match (and 1 or 2 are true)
            //
            // 2, 4, and 3 with case 2 are failure modes.

            // FIXME: implement response to this notification

            log.info("got reply to a role request for {}: asked for {}, and got {}",
                    deviceId, requested, response);

            if (requested == null && response == null) {
                // something was off with DeviceProvider, maybe check channel too?
                log.warn("Failed to assert role [{}] onto Device {}", requested, deviceId);
                mastershipService.relinquishMastership(deviceId);
                return;
            }

            if (requested.equals(response)) {
                if (requested.equals(mastershipService.getLocalRole(deviceId))) {

                    return;
                } else {
                    return;
                    // FIXME roleManager got the device to comply, but doesn't agree with
                    // the store; use the store's view, then try to reassert.
                }
            } else {
                // we didn't get back what we asked for. Reelect someone else.
                log.warn("Failed to assert role [{}] onto Device {}", response, deviceId);
                if (response == MastershipRole.MASTER) {
                    mastershipService.relinquishMastership(deviceId);
                    // TODO: Shouldn't we be triggering event?
                    //final Device device = getDevice(deviceId);
                    //post(new DeviceEvent(DEVICE_MASTERSHIP_CHANGED, device));
                }
            }

        }

        @Override
        public void updatePortStatistics(DeviceId deviceId, Collection<PortStatistics> portStatistics) {
            checkNotNull(deviceId, DEVICE_ID_NULL);
            checkNotNull(portStatistics,
                    "Port statistics list cannot be null");
            checkValidity();

            DeviceEvent event = store.updatePortStatistics(this.provider().id(),
                    deviceId, portStatistics);

            post(event);
        }
    }

    // Posts the specified event to the local event dispatcher.
    private void post(DeviceEvent event) {
        if (event != null && eventDispatcher != null) {
            eventDispatcher.post(event);
        }
    }

    // Applies the specified role to the device; ignores NONE
    /**
     * Apply role to device and send probe if MASTER.
     *
     * @param deviceId  device identifier
     * @param newRole   new role to apply to the device
     * @return true if the request was sent to provider
     */
    private boolean applyRoleAndProbe(DeviceId deviceId, MastershipRole newRole) {
        if (newRole.equals(MastershipRole.NONE)) {
            //no-op
            return true;
        }

        DeviceProvider provider = getProvider(deviceId);
        if (provider == null) {
            log.warn("Provider for {} was not found. Cannot apply role {}", deviceId, newRole);
            return false;
        }
        provider.roleChanged(deviceId, newRole);

        if (newRole.equals(MastershipRole.MASTER)) {
            // only trigger event when request was sent to provider
            provider.triggerProbe(deviceId);
        }
        return true;
    }

    /**
     * Reaasert role for specified device connected to this node.
     *
     * @param did         device identifier
     * @param nextRole    role to apply. If NONE is specified,
     *        it will ask mastership service for a role and apply it.
     */
    private void reassertRole(final DeviceId did,
                              final MastershipRole nextRole) {

        final NodeId myNodeId = clusterService.getLocalNode().id();
        MastershipRole myNextRole = nextRole;
        if (myNextRole == NONE) {
            mastershipService.requestRoleFor(did);
            MastershipTerm term = termService.getMastershipTerm(did);
            if (term != null && myNodeId.equals(term.master())) {
                myNextRole = MASTER;
            } else {
                myNextRole = STANDBY;
            }
        }

        switch (myNextRole) {
        case MASTER:
            final Device device = getDevice(did);
            if ((device != null) && !isAvailable(did)) {
                //flag the device as online. Is there a better way to do this?
                DefaultDeviceDescription deviceDescription
                    = new DefaultDeviceDescription(did.uri(),
                                                   device.type(),
                                                   device.manufacturer(),
                                                   device.hwVersion(),
                                                   device.swVersion(),
                                                   device.serialNumber(),
                                                   device.chassisId());
                DeviceEvent devEvent =
                        store.createOrUpdateDevice(device.providerId(), did,
                                                   deviceDescription);
                post(devEvent);
            }
            // TODO: should apply role only if there is mismatch
            log.info("Applying role {} to {}", myNextRole, did);
            if (!applyRoleAndProbe(did, MASTER)) {
                // immediately failed to apply role
                mastershipService.relinquishMastership(did);
                // FIXME disconnect?
            }
            break;
        case STANDBY:
            log.info("Applying role {} to {}", myNextRole, did);
            if (!applyRoleAndProbe(did, STANDBY)) {
                // immediately failed to apply role
                mastershipService.relinquishMastership(did);
                // FIXME disconnect?
            }
            break;
        case NONE:
        default:
            // should never reach here
            log.error("You didn't see anything. I did not exist.");
            break;
        }
    }

    // Intercepts mastership events
    private class InternalMastershipListener implements MastershipListener {

        @Override
        public void event(MastershipEvent event) {

            if (event.type() != MastershipEvent.Type.MASTER_CHANGED) {
                // Don't care if backup list changed.
                return;
            }

            final DeviceId did = event.subject();
            final NodeId myNodeId = clusterService.getLocalNode().id();

            // myRole suggested by MastershipService
            MastershipRole myNextRole;
            if (myNodeId.equals(event.roleInfo().master())) {
                // confirm latest info
                MastershipTerm term = termService.getMastershipTerm(did);
                final boolean iHaveControl = myNodeId.equals(term.master());
                if (iHaveControl) {
                    deviceClockProviderService.setMastershipTerm(did, term);
                    myNextRole = MASTER;
                } else {
                    myNextRole = STANDBY;
                }
            } else if (event.roleInfo().backups().contains(myNodeId)) {
                myNextRole = STANDBY;
            } else {
                myNextRole = NONE;
            }


            final boolean isReachable = isReachable(did);
            if (!isReachable) {
                // device is not connected to this node
                if (myNextRole != NONE) {
                    log.warn("Node was instructed to be {} role for {}, "
                            + "but this node cannot reach the device.  "
                            + "Relinquishing role.  ",
                             myNextRole, did);
                    mastershipService.relinquishMastership(did);
                }
                return;
            }

            // device is connected to this node:
            if (store.getDevice(did) != null) {
                reassertRole(did, myNextRole);
            } else {
                log.warn("Device is not yet/no longer in the store: {}", did);
            }
        }
    }

    // Store delegate to re-post events emitted from the store.
    private class InternalStoreDelegate
            implements DeviceStoreDelegate {
        @Override
        public void notify(DeviceEvent event) {
            post(event);
        }
    }
}
