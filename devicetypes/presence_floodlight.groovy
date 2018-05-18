/*
Netatmo Presence Floodlight control with dimmer
*/

metadata {
	definition (name: "Netatmo Presence Floodlight", namespace: "apa-1", author: "alex") {
		capability "Switch"
        capability "Switch Level"
		capability "refresh"
        
	}
	tiles(scale: 2) {
	multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.switch.on", backgroundColor:"#00a0dc",
                nextState:"turningoff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#ffffff",
                nextState:"turningon"
				attributeState "turningon", label:'Turning On', action:"switch.on", icon:"st.switches.switch.off", backgroundColor:"#00a0dc"
				attributeState "turningoff", label:'Turning Off', action:"switch.off", icon:"st.switches.switch.off", backgroundColor:"#ffffff"	
			}
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
		}
		standardTile("refresh", "capability.refresh", width: 2, height: 2,  decoration: "flat") {
			state ("default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh")
		}	
		main("switch")
		details("switch", "level", "refresh")
	}

	preferences {
		input("deviceIP", "text", title: "Ip Address", required: true, displayDuringSetup: true)
        input("deviceuuid", "text", title: "Device Secret", required: true, displayDuringSetup: true)
    }
	
}
def installed() {
	updated()
}
def updated() {
	unschedule()
    runEvery1Minute(poll)
}

def poll() {
    log.info("Polling...")
	refresh()
}
                 
def on() {	
    sendCommand("command/floodlight_set_config?config=%7B%22mode%22%3A%22on%22%7D", "parseResponse_on")
    log.info "${device.label}: State is ${device.currentValue("switch")}."
}

def off() {
    sendCommand("command/floodlight_set_config?config=%7B%22mode%22%3A%22off%22%7D", "parseResponse_off")
    log.info "${device.label}: State is ${device.currentValue("switch")}."
}

def refresh() {
    sendCommand("command/floodlight_get_config", "setStatus")
    log.info "${device.label}: State is ${device.currentValue("switch")}."
}

def setLevel(value) {
	log.debug "setLevel >> value: $value"
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 100), 0)
	if (level > 0) {
		sendCommand("command/floodlight_set_config?config=%7B%22intensity%22%3A${level}%2C%22mode%22%3A%22on%22%7D", "getStatuslevel")
	} else {
		sendCommand("command/floodlight_set_config?config=%7B%22intensity%22%3A${level}%2C%22mode%22%3A%22off%22%7D", "getStatuslevel")
	}    
}

def ping() {
	refresh()
}

private sendCommand(command, action){
	def path = "${deviceuuid}/${command}"
    log.debug "Sending command ${path} to ${deviceIP}"
    def result = new physicalgraph.device.HubAction([
            method: "GET",
            path: path,
            headers: [
            	HOST: "${deviceIP}:80"
                   ]],
		null,
		[callback: action]
    )
    log.debug "Request: ${result}"

	sendHubCommand(result)
}

def setStatus(response) {
 	def cmdResponse = parseJson(response.body)
	log.debug "RESPONSE:  ${cmdResponse}"
    if (cmdResponse.mode == 'on') {
    	sendEvent(name: "switch", value: "on")
    }
    else {
    	sendEvent(name: "switch", value: "off")
    }
    sendEvent(name: "level", value: cmdResponse.intensity, unit: "%")
}

def getStatuslevel(response) {
 	def cmdResponse = parseJson(response.body)
	log.debug "RESPONSE:  ${cmdResponse}"
 	refresh()
}

def parseResponse_on(response) {
    def cmdResponse = parseJson(response.body)
	log.debug "RESPONSE:  ${response}"
        if (cmdResponse.status == 'ok') {
    	sendEvent(name: "switch", value: "on")
    }
    else {
    	sendEvent(name: "switch", value: "off")
    }
}

def parseResponse_off(response) {
    def cmdResponse = parseJson(response.body)
	log.debug "RESPONSE:  ${response}"
        if (cmdResponse.status == 'ok') {
    	sendEvent(name: "switch", value: "off")
    }
    else {
    	sendEvent(name: "switch", value: "on")
    }
}

