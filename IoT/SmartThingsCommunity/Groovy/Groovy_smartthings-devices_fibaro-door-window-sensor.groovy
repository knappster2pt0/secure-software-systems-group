/**
 *  Device Type Definition File
 *
 *  Device Type:		Fibaro Door/Window Sensor
 *  File Name:			fibaro-door-window-sensor.groovy
 *	Initial Release:	2014-12-10
 *	@author:			Todd Wackford
 *  Email:				todd@wackford.net
 *  @version:			1.0
 *
 *  Copyright 2014 SmartThings
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
  
 /**
 * Sets up metadata, simulator info and tile definition. The tamper tile is setup, but 
 * not displayed to the user. We do this so we can receive events and display on device 
 * activity. If the user wants to display the tamper tile, adjust the tile display lines
 * with the following:
 *		main(["contact", "temperature", "tamper"])
 *		details(["contact", "temperature", "battery", "tamper"])
 *
 * @param none
 *
 * @return none
 */
 metadata {
	definition (name: "Fibaro Door/Window Sensor", namespace: "smartthings", author: "Todd Wackford") {      
		capability 	"Temperature Measurement"        
		capability 	"Contact Sensor"
		capability 	"Sensor"
		capability 	"Battery"
        capability 	"Alarm"
        capability 	"Configuration"
        
        command		"resetParams2StDefaults"
        command		"listCurrentParams"
        command		"updateZwaveParam"
        command		"test"

		fingerprint deviceId: "0x2001", inClusters: "0x30,0x9C,0x85,0x72,0x70,0x86,0x80,0x56,0x84,0x7A,0xEF,0x2B"
	}

	simulator {
		// messages the device returns in response to commands it receives
		status "open"  :  	"command: 2001, payload: FF"
		status "closed":	"command: 2001, payload: 00"

		for (int i = 0; i <= 100; i += 20) {
			status "temperature ${i}F": new physicalgraph.zwave.Zwave().sensorMultilevelV2.sensorMultilevelReport(
				scaledSensorValue: i, precision: 1, sensorType: 1, scale: 1).incomingMessage()
		}

		for (int i = 0; i <= 100; i += 20) {
			status "battery ${i}%": new physicalgraph.zwave.Zwave().batteryV1.batteryReport(
				batteryLevel: i).incomingMessage()
		}
	}

	tiles {
		standardTile("contact", "device.contact", width: 2, height: 2) {
			state "open",   label: '${name}', icon: "st.contact.contact.open",   backgroundColor: "#ffa81e"
			state "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#79b821"
		}
		valueTile("temperature", "device.temperature", inactiveLabel: false) {
			state "temperature", label:'${currentValue}°',
			backgroundColors:[
				[value: "", color: "#ffffff"],
                [value: 31, color: "#153591"],
				[value: 44, color: "#1e9cbb"],
				[value: 59, color: "#90d2a7"],
				[value: 74, color: "#44b621"],
				[value: 84, color: "#f1d801"],
				[value: 95, color: "#d04e00"],
				[value: 96, color: "#bc2323"]
			]
		}
        standardTile("tamper", "device.alarm") {
			state("secure", label:'secure',    icon:"st.locks.lock.locked",   backgroundColor:"#ffffff")
			state("tampered", label:'tampered', icon:"st.locks.lock.unlocked", backgroundColor:"#53a7c0")
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		//this will display a temperature tile for the DS18B20 sensor
		main(["contact", "temperature"])						//COMMENT ME OUT IF NO TEMP INSTALLED
		details(["contact", "temperature", "battery"])			//COMMENT ME OUT IF NO TEMP INSTALLED
        
        //this will hide the temperature tile if the DS18B20 sensor is not installed
		//main(["contact"])										//UNCOMMENT ME IF NO TEMP INSTALLED
		//details(["contact", "battery"])						//UNCOMMENT ME IF NO TEMP INSTALLED
	}
}

// Parse incoming device messages to generate events 
def parse(String description)
{
	def result = []
	def cmd = zwave.parse(description, [0x30: 1, 0x84: 1, 0x9C: 1, 0x70: 2, 0x80: 1, 0x72: 2, 0x56: 2, 0x60: 3])
	if (cmd) {
		if( cmd.CMD == "8407" ) { 
        	result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format()) 
        }
		result << createEvent(zwaveEvent(cmd))   
	}
    
    if (description == "updated") {
    	if (!state.MSR) {
			result << response(zwave.wakeUpV1.wakeUpIntervalSet(seconds: 60*60, nodeid:zwaveHubNodeId))
			result << response(zwave.manufacturerSpecificV2.manufacturerSpecificGet())
            result << response(zwave.batteryV1.batteryGet().format())
		} 
    }
    
    result << response(zwave.batteryV1.batteryGet().format())
    
    if ( result[0] != null ) {
		log.debug "Parse returned ${result}"
		result
    }
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x30: 2, 0x31: 2]) // can specify command class versions here like in zwave.parse
	log.debug ("Command from endpoint ${cmd.sourceEndPoint}: ${encapsulatedCommand}")
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}

// Event Generation
def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{	
	[descriptionText: "${device.displayName} woke up", isStateChange: false]
	
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv2.SensorMultilevelReport cmd)
{
	def map = [:]
	switch (cmd.sensorType) {
		case 1:
			// temperature
			def cmdScale = cmd.scale == 1 ? "F" : "C"
			map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
			map.unit = getTemperatureScale()
			map.name = "temperature"
			break;
	}
	map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	map
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
	def map = [:]
	map.value = cmd.sensorValue ? "open" : "closed"
	map.name = "contact"
	if (map.value == "closed") {
		map.descriptionText = "$device.displayName is closed"
	}
	else {
		map.descriptionText = "$device.displayName is open"
	}
    
    response(zwave.batteryV1.batteryGet().format()) // update battery when we open/close
	map
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
    //log.debug "in Zwave BasicSet"
}


def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	def map = [:]
	map.value = cmd.sensorState ? "tampered" : "secure"
	map.name = "tamper"
	if (map.value == "tampered") {
		map.descriptionText = "$device.displayName has been tampered with"
	}
	else {
		map.descriptionText = "$device.displayName is secure"
	}
    map  
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Catchall reached for cmd: ${cmd.toString()}}"
	[:]
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if ((cmd.parameterNumber == 15) && (cmd.configurationValue[0] == 1)) { //error in temp probe
        sendEvent(name:"temperature",value:"-99")
	}
    
    if ((cmd.parameterNumber == 15) && (cmd.configurationValue[0] == 255)) { //no temp probe
		sendEvent(name:"temperature",value:"")
    }
	log.debug "${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd.configurationValue}'"
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
	device.updateDataValue(["MSR", msr])
    
    if ( msr == "010F-0700-2000" ) { //this is the msr and device type for the fibaro motion sensor
    	configure()
    }

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
	result
}

 /**
 * Configures the device to settings needed by SmarthThings at device discovery time.
 *
 * @param none
 *
 * @return none
 */
def configure() {
	log.debug "Configuring Device..."
    def cmds = []
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0,0], parameterNumber: 1, size: 2).format()
    // send associate to group 3 to get sensor data reported only to hub
    cmds << zwave.associationV2.associationSet(groupingIdentifier:3, nodeId:[zwaveHubNodeId]).format()
    
    // send associate to group 2 to get tamper alarm data reported
    cmds << zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format()
    
    // turn on the tamper alarm
	cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 10, size: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 10).format()
    
    // temperature change sensitivity
	cmds << zwave.configurationV1.configurationSet(configurationValue: [4], parameterNumber: 12, size: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 12).format()
    
	// see if there is a temp probe on board and is it working
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 15).format()

	delayBetween(cmds, 500)
}

//used to add "test" button for simulation of user changes to parameters
def test() {
	def params = [paramNumber:10,value:1,size:1]
	updateZwaveParam(params)
}

 /**
 * This method will allow the user to update device parameters (behavior) from an app.
 * A "Zwave Tweaker" app will be developed as an interface to do this. Or the user can
 * write his/her own app to envoke this method. No type or value checking is done to
 * compare to what device capability or reaction. It is up to user to read OEM
 * documentation prio to envoking this method.
 *
 * <p>THIS IS AN ADVANCED OPERATION. USE AT YOUR OWN RISK! READ OEM DOCUMENTATION!
 *
 * @param List[paramNumber:80,value:10,size:1]
 *
 *
 * @return none
 */
def updateZwaveParam(params) {
	if ( params ) {
        def pNumber = params.paramNumber
        def pSize	= params.size
        def pValue	= [params.value]
        log.debug "Make sure device is awake and in recieve mode"
        log.debug "Updating ${device.displayName} parameter number '${pNumber}' with value '${pValue}' with size of '${pSize}'"

		def cmds = []
        cmds << zwave.configurationV1.configurationSet(configurationValue: pValue, parameterNumber: pNumber, size: pSize).format()
        cmds << zwave.configurationV1.configurationGet(parameterNumber: pNumber).format()
        delayBetween(cmds, 1000)        
    }
}

 /**
 * Sets all of available Fibaro parameters back to the device defaults except for what
 * SmartThings needs to support the stock functionality as released. This will be
 * called from the "Fibaro Tweaker" or user's app.
 *
 * <p>THIS IS AN ADVANCED OPERATION. USE AT YOUR OWN RISK! READ OEM DOCUMENTATION!
 *
 * @param none
 *
 * @return none
 */
def resetParams2StDefaults() {
	log.debug "Resetting ${device.displayName} parameters to SmartThings compatible defaults"
	def cmds = []
	cmds << zwave.configurationV1.configurationSet(configurationValue: [0,0], parameterNumber: 1, size: 2).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 2, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [255], parameterNumber: 5, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [255], parameterNumber: 7, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 9, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [1], parameterNumber: 10, size: 1).format() //ST Custom
    cmds << zwave.configurationV1.configurationSet(configurationValue: [4], parameterNumber: 12, size: 1).format() //St Custom
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 13, size: 1).format()
    cmds << zwave.configurationV1.configurationSet(configurationValue: [0], parameterNumber: 14, size: 1).format()
    
    delayBetween(cmds, 500)
}

 /**
 * Lists all of available Fibaro parameters and thier current settings out to the 
 * logging window in the IDE. This will be called from the "Fibaro Tweaker" or 
 * user's own app.
 *
 * <p>THIS IS AN ADVANCED OPERATION. USE AT YOUR OWN RISK! READ OEM DOCUMENTATION!
 *
 * @param none
 *
 * @return none
 */
def listCurrentParams() {
	log.debug "Listing of current parameter settings of ${device.displayName}"
    def cmds = []
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 2).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 5).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 7).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 9).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 10).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 12).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 13).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 14).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 15).format()
    
	delayBetween(cmds, 500)
}