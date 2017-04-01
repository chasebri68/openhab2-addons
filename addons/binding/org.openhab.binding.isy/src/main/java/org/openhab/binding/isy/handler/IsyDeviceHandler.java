/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.isy.handler;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.isy.config.IsyInsteonDeviceConfiguration;
import org.openhab.binding.isy.internal.NodeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link IsyDeviceHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Craig Hamilton - Initial contribution
 */
public class IsyDeviceHandler extends AbtractIsyThingHandler {

    protected Logger logger = LoggerFactory.getLogger(IsyDeviceHandler.class);

    protected Map<Integer, String> mDeviceidToChannelMap = new HashMap<Integer, String>();

    private String mControlUID = null;

    // most devices only have one device id, which is 1. so if map is empty, we'll return 1
    protected int getDeviceIdForChannel(String channel) {
        if (mDeviceidToChannelMap.size() == 0) {
            return 1;
        }
        for (int id : mDeviceidToChannelMap.keySet()) {
            if (mDeviceidToChannelMap.get(id).equals(channel)) {
                return id;
            }
        }
        throw new IllegalArgumentException("Could not find device id for channel: '" + channel + "'");
    }

    private static String toStringForObject(Object... parameters) {
        StringBuilder returnValue = new StringBuilder();
        for (Object object : parameters) {
            returnValue.append(object.toString()).append(":");
        }
        return returnValue.toString();
    }

    @Override
    public void handleUpdate(Object... parameters) {
        if (logger.isDebugEnabled()) {
            logger.debug("handleUpdate called, parameters: " + toStringForObject(parameters));
        }
        NodeAddress insteonAddress = NodeAddress.parseAddressString((String) parameters[2]);
        int deviceId = insteonAddress.getDeviceId();
        if ("ST".equals(parameters[0])) {
            State newState;
            int newIntState = Integer.parseInt((String) parameters[1]);
            if (newIntState == 0) {
                newState = OnOffType.OFF;
            } else if (newIntState == 255) {
                newState = OnOffType.ON;
            } else if (insteonAddress.toStringNoDeviceId().substring(0, 1).equals("Z")) {
                logger.debug("UpdateVariable for: " + insteonAddress.toStringNoDeviceId());
                newState = IsyDeviceHandler.statusUpdateDecimalState(newIntState);
            } else {
                newState = IsyDeviceHandler.statusValuetoState(newIntState);
            }
            // below to map to channel find out how!!
            updateState(mDeviceidToChannelMap.get(deviceId), newState);
        } else if ("CLISPC".equals(parameters[0])) {
            logger.debug("InsertCommand for CoolTempChange for: " + insteonAddress.toStringNoDeviceId());
            // Assign new state (Temp Value) to:
            // <channel id="cooltemp" typeId="coolTemp" />
            //
            // NEED HELP!!
            // logger.debug("InsertCommand for CoolTempChange for: " + insteonAddress.toStringNoDeviceId() + " : " + " :
            // "
            // + mDeviceidToChannelMap.get(deviceId));
            // getDeviceIdForChannel("CHANNEL_THERM_COOLTEMP") +
        } else if ("CLISPH".equals(parameters[0])) {
            logger.debug("InsertCommand for HeatTempChange for: " + insteonAddress.toStringNoDeviceId());
            // Assign new state (Temp Value) to:
            // <channel id="heattemp" typeId="heatTemp" />
        } else if ("CLIMD".equals(parameters[0])) {
            logger.debug("InsertCommand for TempStat for: " + insteonAddress.toStringNoDeviceId());
            // Assign new state (Temp Value) to:
            // <channel id="therm_setting" typeId="thermostatSetting" />
        } else if (mControlUID != null && ("DOF".equals(parameters[0]) || "DFOF".equals(parameters[0])
                || "DON".equals(parameters[0]) || "DFON".equals(parameters[0]))) {
            if (deviceId == 1) {
                updateState(mControlUID, new StringType((String) parameters[0]));
            } else {
                logger.debug("control status ignored because device id was not 1, it was : " + deviceId);
            }
        }
    }

    protected IsyDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handle command, channel: " + channelUID + ", command: " + command);
        IsyBridgeHandler bridgeHandler = getBridgeHandler();
        IsyInsteonDeviceConfiguration test = getThing().getConfiguration().as(IsyInsteonDeviceConfiguration.class);

        if (command instanceof OnOffType) {
            // isy needs device id appended to address
            String isyAddress = NodeAddress.parseAddressString(test.address, getDeviceIdForChannel(channelUID.getId()))
                    .toString();
            logger.debug("insteon address for command is: " + isyAddress);
            if (command.equals(OnOffType.ON)) {
                boolean result = bridgeHandler.getInsteonClient().changeNodeState("DON", "0", isyAddress);
                logger.debug("result: " + result);
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.getInsteonClient().changeNodeState("DOF", "0", isyAddress);
            } else if (command.equals(RefreshType.REFRESH)) {
                logger.debug("should retrieve state");
            }
        } else if (command instanceof PercentType) {
            // isy needs device id appended to address
            String isyAddress = NodeAddress.parseAddressString(test.address, getDeviceIdForChannel(channelUID.getId()))
                    .toString();
            logger.debug("insteon address for command is: " + isyAddress);
            int commandValue = ((PercentType) command).intValue() * 255 / 100;
            if (commandValue == 0) {
                bridgeHandler.getInsteonClient().changeNodeState("DOF", Integer.toString(0), isyAddress);
            } else {
                bridgeHandler.getInsteonClient().changeNodeState("DON", Integer.toString(commandValue), isyAddress);
            }
        } else if (command instanceof StringType) {
            // isy needs device id appended to address
            logger.debug("Start DecimalType");
            String isyAddress = NodeAddress.parseAddressString(test.address, getDeviceIdForChannel(channelUID.getId()))
                    .toString();
            logger.debug("insteon address for command is: " + isyAddress);
            int commandValue = ((DecimalType) command).intValue();
            IsyInsteonDeviceConfiguration device_config = getThing().getConfiguration()
                    .as(IsyInsteonDeviceConfiguration.class);
            bridgeHandler.getInsteonClient().changeNodeState("ST", Integer.toString(commandValue), isyAddress);
            // bridgeHandler.getInsteonClient().changeNodeState(device_config, ((DecimalType)
            // command).intValue(),isyAddress);
        } else if (RefreshType.REFRESH.equals(command)) {
            logger.debug("Refresh here: " + command.toFullString());
        } else {
            logger.warn("unhandled Command: " + command.toFullString());
        }
    }

    @Override
    public void initialize() {
        // TODO: Initialize the thing. If done set status to ONLINE to indicate proper working.
        // Long running initialization should be done asynchronously in background.
        updateStatus(ThingStatus.ONLINE);

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work
        // as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    protected void addChannelToDevice(String channel, int deviceId) {
        this.mDeviceidToChannelMap.put(deviceId, channel);
    }

    protected void setControlChannel(String channelId) {
        mControlUID = channelId;
    }

    public static State statusValuetoState(int updateValue) {
        State returnValue;
        if (updateValue > 0) {
            returnValue = new PercentType(updateValue * 100 / 255);
        } else {
            returnValue = OnOffType.OFF;
        }
        return returnValue;

    }

    public static State statusUpdateDecimalState(int updateValue) {
        State returnValue = null;
        if (updateValue > 0) {
            returnValue = new DecimalType(updateValue);
        } else {
            // returnValue.equals(0);
        }
        return returnValue;

    }

}
