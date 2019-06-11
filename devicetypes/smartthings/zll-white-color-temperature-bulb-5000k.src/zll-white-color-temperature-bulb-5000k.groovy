/**
 *  Copyright 2017 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
    definition (name: "ZLL White Color Temperature Bulb 5000K", namespace: "smartthings", author: "SmartThings",runLocally: true, minHubCoreVersion: '000.022.00001', executeCommandsLocally: true, genericHandler: "Zigbee") {

        capability "Actuator"
        capability "Color Temperature"
        capability "Configuration"
        capability "Polling"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"
        capability "Health Check"

        attribute "colorName", "string"
		fingerprint profileId: "0104", inClusters: "0000,0003,0004,0005,0006,0008,0300",  manufacturer: "SAMSUNG", model: "ITMBZ", deviceJoinName: "SAMSUNG LED Light"
    }

    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
            tileAttribute ("colorName", key: "SECONDARY_CONTROL") {
                attributeState "colorName", label:'${currentValue}'
            }
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        controlTile("colorTempSliderControl", "device.colorTemperature", "slider", width: 4, height: 2, inactiveLabel: false, range:"(2700..5000)") {
            state "colorTemperature", action:"color temperature.setColorTemperature"
        }
        valueTile("colorTemp", "device.colorTemperature", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "colorTemperature", label: '${currentValue} K'
        }

        main(["switch"])
        details(["switch", "colorTempSliderControl", "colorTemp", "refresh"])
    }
}



def parseDescriptionAsMap(description) {
    (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}
private Integer convertHexToInt(hex) {
 Integer.parseInt(hex,16)
}


// Globals
private getMOVE_TO_COLOR_TEMPERATURE_COMMAND() { 0x0A }
private getCOLOR_CONTROL_CLUSTER() { 0x0300 }
private getATTRIBUTE_COLOR_TEMPERATURE() { 0x0007 }

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "parse: description is $description"
    def event = zigbee.getEvent(description)
    if (event) {
        if (event.name == "colorTemperature") {
            event.unit = "K"
            setGenericName(event.value)
        }
        log.debug "parse: Sending event $event"
        sendEvent(event)
    }
    else {
        log.warn "parse: DID NOT PARSE MESSAGE for description : $description"
        log.debug zigbee.parseDescriptionAsMap(description)
    }
}


def off() {
    zigbee.off()// + ["delay 1500"] + zigbee.onOffRefresh()
    //sendEvent(name: "switch", value: "off")
}

def on() {
    zigbee.on()// + ["delay 1500"] + zigbee.onOffRefresh()
    //sendEvent(name: "switch", value: "on")
}

def setLevel(value, rate = null) {
    if (device.currentValue("switch") == "off"){
        sendEvent(name: "switch", value: "on")
    }
    zigbee.setLevel(value) //+ ["delay 1500"] + zigbee.levelRefresh() + zigbee.onOffRefresh()
}

def refresh() {
    def cmds =  zigbee.levelRefresh() + zigbee.colorTemperatureRefresh() + zigbee.onOffRefresh() 
    cmds
}

def poll() {
    zigbee.levelRefresh()+ zigbee.colorTemperatureRefresh() + zigbee.onOffRefresh()
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    return zigbee.levelRefresh()
}

def healthPoll() {
    log.debug "healthPoll()"
    def cmds = poll()
    cmds.each{ sendHubCommand(new physicalgraph.device.HubAction(it))}
}

def configureHealthCheck() {
    Integer hcIntervalMinutes = 12
    if (!state.hasConfiguredHealthCheck) {
        log.debug "Configuring Health Check, Reporting"
        unschedule("healthPoll", [forceForLocallyExecuting: true])
        runEvery5Minutes("healthPoll", [forceForLocallyExecuting: true])
        // Device-Watch allows 2 check-in misses from device
        sendEvent(name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
        state.hasConfiguredHealthCheck = true
    }
}

def configure() {
    log.debug "configure()"
    configureHealthCheck()
    zigbee.onOffConfig() + zigbee.levelConfig() + zigbee.onOffRefresh() + zigbee.levelRefresh() + zigbee.colorTemperatureRefresh()
}

def updated() {
    log.debug "updated()"
    configureHealthCheck()
}

def setColorTemperature(value) {
    value = value as Integer
    def tempInMired = Math.round(1000000 / value)
    def finalHex = zigbee.swapEndianHex(zigbee.convertToHexString(tempInMired, 4))
	sendEvent(name: "colorTemperature", value: value)
    zigbee.command(COLOR_CONTROL_CLUSTER, MOVE_TO_COLOR_TEMPERATURE_COMMAND, "$finalHex 0000") +
    ["delay 3000"] + 
    zigbee.readAttribute(COLOR_CONTROL_CLUSTER, ATTRIBUTE_COLOR_TEMPERATURE)
}

//Naming based on the wiki article here: http://en.wikipedia.org/wiki/Color_temperature
def setGenericName(value){
    if (value != null) {
        def genericName = ""
        if (value < 3300) {
            genericName = "Soft White"
        } else if (value < 4150) {
            genericName = "Moonlight"
        } else if (value <= 5000) {
            genericName = "Cool White"
        } else {
            genericName = "Daylight"
        }
        sendEvent(name: "colorName", value: genericName, displayed: true)
    }
}

def installed() {
    sendEvent(name: "switch", value: "on") +
    sendEvent(name: "level", value: 100) +
    sendEvent(name: "colorTemperature", value: 3000) +
    configure()
}