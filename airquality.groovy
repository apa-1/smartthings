/**
 *  Foobot Air quality controls
 *  Controls Holmes Wemo Air Purifiers, Keen Smart Vents, Thermostat Fans and optionally a switch for a dehumidifier based on data from a Foobot
 */
definition(
        name: "Air Quality Controls for Foobot",
        namespace: "apa-1",
        author: "alex",
        description: "Controls air purifier, thermostat fans and smart vents based on foobot conditions",
        category: "My Apps",
        iconUrl: "http://cdn.device-icons.smartthings.com/Appliances/appliances11-icn.png",
        iconX2Url: "http://cdn.device-icons.smartthings.com/Appliances/appliances11-icn@2x.png"
)

preferences {
    section("Foobot") {
        input("foobot", "capability.sensor", title: "Select a Foobot to monitor:", required: true, multiple: false)
    }
    section("Thermostat Fans") {
        input("thermostats", "capability.thermostat", title: "Thermostat Fan to control", required: true, multiple: true)
        input("fan_mode", "enum", title: "Select the air quality to run fan", required: true, options: ["good", "fair", "poor"], multiple: true, defaultValue: "poor")
    }
    section("Smart Vents") {
        input("smartvents", "capability.switchLevel", title: "Select Smart Vents to control", required: true, multiple: true)
    }
    section("Air Purifiers") {
        input("airpurifiers", "capability.switch", title: "Select Wemo Air Purifiers", required: true, multiple: true)
        input("air_great_mode", "enum", title: "Select mode for 'Great' GPI", required: true, options: ["Off", "Low", "Med", "High", "Auto"], defaultValue: "Off")
        input("air_good_mode", "enum", title: "Select mode for 'Good' GPI", required: true, options: ["Off", "Low", "Med", "High", "Auto"], defaultValue: "Low")
        input("air_fair_mode", "enum", title: "Select mode for 'Fair' GPI", required: true, options: ["Off", "Low", "Med", "High", "Auto"], defaultValue: "Med")
        input("air_poor_mode", "enum", title: "Select mode for 'Poor' GPI", required: true, options: ["Off", "Low", "Med", "High", "Auto"], defaultValue: "High")
    }
    section("DeHumidifier Control") {
        input("humiditypercent", "number", title: "Control at what %", required: false)
        input("dehumidifier", "capability.switch", title: "Select Dehumidifier", required: false, multiple: false)
    }
}

def installed() {
    initialized()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialized()
}

def initialized() {
    subscribe(foobot, "polling", airHandler)
    subscribe(foobot, "refresh", airHandler)
    subscribe(foobot, "humidity", airHandler)
    subscribe(foobot, "GPIState", airHandler)
}

def get_mode(gpistate) {
    if (gpistate == "great") {
        return air_great_mode
    }
    if (gpistate == "good") {
        return air_good_mode
    }
    if (gpistate == "fair") {
        return air_fair_mode
    }
    if (gpistate == "poor") {
        return air_poor_mode
    }
}

def airHandler(evt) {
    def gpistate = foobot.currentValue("GPIState")
    def humidity = foobot.currentValue("humidity")
    log.debug("Current Humidity is: $humidity")
    log.debug("Current GPI State is: $gpistate")

    if (humiditypercent != null && dehumidifier != null) {
        if (humidity >= humiditypercent) {
            log.debug("Humdifier set to on")
            //TODO Add Check for already on
            dehumidifier.on()
        } else {
            log.debug("Humdifier set to off")
            dehumidifier.off()
        }
    }
    //setting purifiers
    for (purifier in airpurifiers) {
        log.debug "Checking $purifier"
        def currentMode = purifier.latestState('mode').stringValue
        log.debug("State of $purifier: $currentMode")
        def desiredMode = get_mode(gpistate)
        log.debug("Desired State of $purifier: $desiredMode")
        if (currentMode.toLowerCase() != desiredMode.toLowerCase()) {
            log.debug("Setting $purifier")
            switch (desiredMode.toLowerCase()) {
                case "off":
                    purifier.fanOff()
                    break
                case "low":
                    purifier.fanLow()
                    break
                case "med":
                    purifier.fanMed()
                    break
                case "high":
                    purifier.fanHigh()
                    break
                case "auto":
                    purifier.fanAuto()
                    break
            }
        } else {
            log.debug("Purifier Mode is correct not changing")
        }
    }
    //check thermostat fan
    if (fan_mode.contains(gpistate)) {
        for (fan in thermostats) {
            if (fan.currentValue("thermostatFanMode") != "on") {
                log.debug "Turning $fan fan to on"
                fan.fanOn()
            }
        }
        if (smartvents != null) {
            for (vent in smartvents) {
                log.debug "Setting $vent to 100%"
                vent.setLevel(100)
            }
        }

    } else {
        for (fan in thermostats) {
            if (fan.currentValue("thermostatFanMode") != "auto") {
                log.debug "Turning $fan fan to auto"
                //Since another air quality smartapp could be using this we will let fan run for 5 mins
                runIn(300, fan.fanAuto())
            } else {
                log.debug("$fan is already set to Auto")
            }
        }
    }
}
