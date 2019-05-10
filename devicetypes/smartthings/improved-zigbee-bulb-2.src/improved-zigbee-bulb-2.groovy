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
 *  ZigBee White Color Temperature Bulb
 *
 *  Author: SmartThings
 *  Date: 2015-09-22
 */

metadata {
	definition(name: "Improved Zigbee Bulb_2", namespace: "smartthings", author: "SmartThings", runLocally: true, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Zigbee") {

		capability "Actuator"
		capability "Color Temperature"
		capability "Configuration"
		capability "Health Check"
		capability "Refresh"
		capability "Switch"
		capability "Switch Level"
		capability "Light"

		attribute "colorName", "string"

		fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008, 0300", manufacturer: "SAMSUNG00", model: "ITMBZ", deviceJoinName: "IM_SLED"
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name: "switch", type: "lighting", width: 6, height: 4, canChangeIcon: true) {
			tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turningOn"
				attributeState "turningOn", label: '${name}', action: "switch.off", icon: "st.switches.light.on", backgroundColor: "#00A0DC", nextState: "turningOff"
				attributeState "turningOff", label: '${name}', action: "switch.on", icon: "st.switches.light.off", backgroundColor: "#ffffff", nextState: "turningOn"
			}
			tileAttribute("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action: "switch level.setLevel"
			}
		}

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label: "", action: "refresh.refresh", icon: "st.secondary.refresh"
		}

		controlTile("colorTempSliderControl", "device.colorTemperature", "slider", width: 4, height: 2, inactiveLabel: false, range: "(2700..5000)") {
			state "colorTemperature", action: "color temperature.setColorTemperature"
		}
		valueTile("colorName", "device.colorName", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "colorName", label: '${currentValue}'
		}

		main(["switch"])
		details(["switch", "colorTempSliderControl", "colorName", "refresh"])
	}
}
// Parse incoming device messages to generate events
def parse(String description) {
   log.info "description is $description"
   
    if (description?.startsWith("read attr -")) {
        def descMap = parseDescriptionAsMap(description)
        log.trace "descMap : $descMap"

        if (descMap.cluster == "0300") {	// color control cluster
            if( descMap.attrId == "0007") {		// colortemperature attribute
                def tempInMired = convertHexToInt(descMap.value)
             		def tempInKelvin = Math.round(1000000/tempInMired)
                log.debug "Color temperature returned is $tempInKelvin"
                
             		sendEvent(name: "colorTemperature", value: convertCCTrangeIn(tempInKelvin))
            }
        }
        else if(descMap.cluster == "0008"){	// level cluster
            def dimmerValue = Math.round(convertHexToInt(descMap.value) * 100 / 255)	// convert percent
            log.debug "dimmer value is $dimmerValue"
            sendEvent(name: "level", value: dimmerValue)
        }
        else if(descMap.cluster == "0006"){	// onoff cluster
            if(convertHexToInt(descMap.value)>0){
            	sendEvent(name : "switch", value : "on")
            	log.debug "Light On"
            }
            else{
            	sendEvent(name : "switch", value : "off")
            	log.debug "Light Off"
            }
        }
    }
    else {
    		def event = zigbee.getEvent(description)
    		def attname = null;
    		def attvalue = null;
    		if(event){
    				if(event.name == "switch"){
    						if(event.value == "on"){
    							attname = "switch"
    							attvalue "on"
    						}
    						else if(event.value == "off"){
    						  attname = "switch"
    							attvalue "off"
    						}
    				}
    				else if (event.name == "level"){
    						if(event.level < 1){
    							log.debug "level less than 2"
                                sendEvent(name : "switch", value : "off")
    						}
    						else{
    							sendEvent(name : "switch", value : "on")
    						}
    						attname = "level"
    						attvalue = event.value
    				}
    				else if(event.name == "colortemperature"){
    						attname = "colortemperature"
    						
                            attvalue = event.value
    						//setColorTemperature(event.value)
    				}
    				else{
    						sendEvent(event)
    				}
    				def result = creatEvent(name : attname, value : attvalue)
    				return result
    		}
    }
}

def parseDescriptionAsMap(description) {
    (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}

def on() {
    // just assume it works for now
    log.debug "on()"
    sendEvent(name: "switch", value: "on")
    zigbee.on()
}

def off() {
    // just assume it works for now
    log.debug "off()"
    sendEvent(name: "switch", value: "off")
    zigbee.off()
}

def setColorTemperature(value) {
	def new_value = convertCCTrangeOut(value)
    def tempInMired = Math.round(1000000/new_value)
    def finalHex = swapEndianHex(hexF(tempInMired, 4))
    // def genericName = getGenericName(value)
    // log.debug "generic name is : $genericName"
    sendEvent(name: "colorTemperature", value: value)
    
    // sendEvent(name: "colorName", value: genericName)
		def cmds = []
    cmds << "st cmd 0x${device.deviceNetworkId} ${endpointId} 0x0300 0x0a {${finalHex} 2000}"
    //cmds << zigbee.command(0x0300, 0x0a, "$finalHex 0100")
    cmds
}



def refresh() {
// Ping the device with color as to not get out of sync 
    [
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 6 0", "delay 500",
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 8 0", "delay 500",
    "st rattr 0x${device.deviceNetworkId} ${endpointId} 0x0300 7","delay 500",
    ]
}

def poll(){
 log.debug "Poll is calling refresh"
 refresh()
}

def configure(){
 log.debug "Initiating configuration reporting and binding"
    
    [  
     "zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 6 {${device.zigbeeId}} {}", "delay 1000",
     "zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 8 {${device.zigbeeId}} {}", "delay 1000",
     "zdo bind 0x${device.deviceNetworkId} ${endpointId} 1 0x0300 {${device.zigbeeId}} {}"
 ]
}

def setLevel(value) {
	log.trace "setLevel($value)"
    
	if (value < 1) {
		sendEvent(name: "switch", value: "off")
	}
	else if (device.currentValue("switch") == "off") {
		sendEvent(name: "switch", value: "on")

	}
    sendEvent(name: "level", value: value)

	zigbee.setLevel(value,10)

}

private getEndpointId() {
 new BigInteger(device.endpointId, 16).toString()
}

private hex(value, width=2) {
 def s = new BigInteger(Math.round(value).toString()).toString(16)
 while (s.size() < width) {
  s = "0" + s
 }
 s
}

private evenHex(value){
    def s = new BigInteger(Math.round(value).toString()).toString(16)
    while (s.size() % 2 != 0) {
        s = "0" + s
    }
    s
}

//Need to reverse array of size 2
private byte[] reverseArray(byte[] array) {
    byte tmp;
    tmp = array[1];
    array[1] = array[0];
    array[0] = tmp;
    return array
}

private hexF(value, width) {
 def s = new BigInteger(Math.round(value).toString()).toString(16)
 while (s.size() < width) {
  s = "0" + s
 }
 s
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private Integer convertHexToInt(hex) {
 Integer.parseInt(hex,16)
}

private Integer convertCCTrangeOut(val){
	def ret = val
    if(val <= 100){
    	ret = val * 2300 /100 + 2700
    }
    return ret
}

private Integer convertCCTrangeIn(val){
	ret = val
    if(device.currentValue("colortemperature") <= 100){
    	ret = (val-2700) * 100 / 2300
    }
    return ret
}

def installed() {
    on() +
    setLevel(100) +
    setColorTemperature(3000) +
    sendEvent(name: "switch", value: "on") +
    sendEvent(name: "level", value: 100) +
    sendEvent(name: "colorTemperature", value: 3000) +
    configure()
}
