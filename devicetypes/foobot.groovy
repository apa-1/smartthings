/**
 * 
 * Based on Adam V. New Foobot API
 *
 */

 preferences {
    input("uuid", "text", title: "UUID", description: "The UUID of the Foobot that you would like information for")
    //api key is too long to enter in app so needs to be split
	input("apikey1", title: "API Key 1", description: "1st Half of API Key")
	input("apikey2", title: "API Key 2", description: "2nd Half of API Key")
    def myOptions = ["US", "EU"]
	input "region", 
    "enum", 
    title: "Select your region",
    defaultValue: "US",
    required: true, 
    options: myOptions, 
    displayDuringSetup: true
}
 
metadata {
	definition (name: "Foobot", namespace: "AdamV", author: "AdamV") {
		capability "Polling"
        capability "Refresh"
        capability "Sensor"
        capability "Thermostat"
		capability "relativeHumidityMeasurement"
        capability "temperatureMeasurement"
        capability "carbonDioxideMeasurement"
     
     	attribute "pollution", "number"
        attribute "particle", "number"
        attribute "voc", "number"
        attribute "GPIState", "String"
	}
    
	tiles (scale: 2){   
        multiAttributeTile(name:"pollution", type:"generic", width:6, height:4) {
            tileAttribute("device.pollution", key: "PRIMARY_CONTROL") {
    			attributeState("default", label:'${currentValue}% GPI', unit:"%", icon:"st.Weather.weather13", backgroundColors:[
                    [value: 24, color: "#1c71ff"],
                    [value: 49, color: "#5c93ee"],
                    [value: 74, color: "#ff4040"],
                    [value: 100, color: "#d62d20"]
                ])
  			}
       		tileAttribute("device.GPIState", key: "SECONDARY_CONTROL") {
    			attributeState("default", label:'${currentValue}')
			}
       }
        valueTile("voc", "device.voc", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", label:'${currentValue} VOC ppb', unit:"ppb"
        }
        valueTile("particle", "device.particle", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", label:'${currentValue} µg/m³', unit:"µg/m³ PM2.5"
        }
        valueTile("co2", "device.co2", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", label:'${currentValue} CO2 ppm', unit:"ppm"
        }
        valueTile("humidity", "device.humidity", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", label:'${currentValue}% humidty', unit:"%"
        }
        valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", label:'${currentValue}°', unit:"°"
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, width: 2, height: 2, decoration: "flat") {
            state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("spacerlastUpdatedLeft", "spacerTile", decoration: "flat", width: 1, height: 1) {
 		}
        valueTile("lastUpdated", "device.lastUpdated", inactiveLabel: false, decoration: "flat", width: 4, height: 1) {
			state "default", label:'Last updated:\n${currentValue}'
		}
        standardTile("spacerlastUpdatedRight", "spacerTile", decoration: "flat", width: 1, height: 1) {
 		}

        main "pollution"
        details(["pollution", "voc", "particle", "co2", "humidity", "temperature", "refresh", "spacerlastUpdatedLeft", "lastUpdated", "spacerlastUpdatedRight"])
	}
}

def parse(String description) {
	log.debug "Parsing '${description}'"
}

def installed() {
	updated()
}

def updated() {
	unschedule()
    runEvery30Minutes(poll)
}

def refresh() { 
	poll()
}

def celsiusToFahrenheit(celsius) {
        BigDecimal fahrenheit = (celsius.toInteger() *  (9/5) + 32)
        fahrenheit = fahrenheit.setScale(0, BigDecimal.ROUND_DOWN)
        return fahrenheit.toString()
}

def poll() {
    
    def start = new Date(Calendar.instance.time.time-1800000).format("yyyy-MM-dd'T'HH:MM':00'");
    def stop = new Date(Calendar.instance.time.time+1800000).format("yyyy-MM-dd'T'HH:MM':00'");
    
    def accessToken = apikey1 + apikey2
    
    def regionVar = ""
    def params = ""
    
    if (region){
    
    regionVar = region
    
    if (regionVar == "EU"){
    	params = "https://api.foobot.io/v2/device/${settings.uuid}/datapoint/0/last/0/?api_key=${accessToken}"
		}
 	if (regionVar == "US"){
    	params = "https://api-us-east-1.foobot.io/v2/device/${settings.uuid}/datapoint/0/last/0/?api_key=${accessToken}"
		}
    }
    
    try {
        httpGet(params) {resp ->
           resp.headers.each {
           log.debug "${it.name} : ${it.value}"
        }
        	def theHeaders = resp.getHeaders("Content-Length")
        	log.debug "response contentType: ${resp.contentType}"
       		log.debug "response status code: ${resp.status}"
        	log.debug "response data: ${resp.data}"

			def datapoints = resp.data.datapoints[-1]
            
             // sensors
             //["time","pm","tmp","hum","co2","voc","allpollu"]
            def sensors = resp.data.sensors
             
            def tmpindex = sensors.findIndexValues {it == "tmp"}
			BigDecimal tmp = datapoints[tmpindex][0]
			log.debug "tmp is: ${tmp}"
            
            if (regionVar == 'US') {
                tmp = celsiusToFahrenheit(tmp) as Integer
                sendEvent(name: "temperature", value: tmp, unit: "°F")            
            }
            else {
                def tmpround = String.format("%5.2f",tmp)
                log.debug ("tmpround: $tmpround")
            	sendEvent(name: "temperature", value: tmpround, unit: "°C")
            }

        	def pmindex = sensors.findIndexValues {it == "pm"}
            def pm = datapoints[pmindex][0]
            log.debug "pm: ${pm}"
            sendEvent(name: "particle", value: sprintf("%.2f",pm), unit: "µg/m³ PM2.5")   
            
            def humindex = sensors.findIndexValues {it == "hum"}
            def hum = datapoints[humindex][0]
            log.debug "hum: ${hum}"
            sendEvent(name: "humidity", value: hum as Integer, unit: "%")
            
            def coindex = sensors.findIndexValues {it == "co2"}
            def co = datapoints[coindex][0]
            log.debug "co2: ${co}"
            sendEvent(name: "co2", value: co as Integer, unit: "ppm")
            
            def vocindex = sensors.findIndexValues {it == "voc"}
            def voc = datapoints[vocindex][0]
            log.debug "voc: ${voc}"
            sendEvent(name: "voc", value: voc as Integer, unit: "ppb")
                       
            def polluindex = sensors.findIndexValues {it == "allpollu"}
            def allpollu = datapoints[polluindex][0]
            log.debug "allpollu: ${allpollu}"
            sendEvent(name: "pollution", value: allpollu as Integer, unit: "%")

            if (allpollu < 25){
            	sendEvent(name: "GPIState", value: "great", isStateChange: true)
            }
            else if (allpollu < 50){
            	sendEvent(name: "GPIState", value: "good", isStateChange: true)
            }
            else if (allpollu < 75){
            	sendEvent(name: "GPIState", value: "fair", isStateChange: true)
            }
            else if (allpollu > 75){
            	sendEvent(name: "GPIState", value: "poor", isStateChange: true)
            }
            
            def now = new Date().format("EEE, d MMM yyyy HH:mm:ss",location.timeZone)
            sendEvent(name:"lastUpdated", value: now, displayed: false)

        }
    } catch (e) {
        log.error "error: $e"
    }

}


