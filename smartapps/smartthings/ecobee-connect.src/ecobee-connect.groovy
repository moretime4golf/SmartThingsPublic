/**
 *  Copyright 2015 SmartThings
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
 *	Ecobee Service Manager
 *
 *	Author: scott
 *	Date: 2013-08-07
 *
 *  Last Modification:
 *      JLH - 01-23-2014 - Update for Correct SmartApp URL Format
 *      JLH - 02-15-2014 - Fuller use of ecobee API
 *      10-28-2015 DVCSMP-604 - accessory sensor, DVCSMP-1174, DVCSMP-1111 - not respond to routines
 *      StrykerSKS - 12-11-2015 - Make it work (better) with the Ecobee 3
 *
 *  See Changelog for change history
 *
 */  
private def getVersion() { return "ecobee (Connect) Version 0.9.0-RC" }
private def getHelperSmartApps() {
	return [ "ecobee Routines": [multiple: true, description: "Example Description"],
    		 "ecobee ABC": [multiple: false, description: "Example Description for ecobee ABC"]
             ]    
}
 
definition(
	name: "Ecobee (Connect)",
	namespace: "smartthings",
	author: "Sean Kendall Schneyer",
	description: "Connect your Ecobee thermostat to SmartThings.",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png",
	singleInstance: true
) {
	appSetting "clientId"
}

preferences {
	page(name: "mainPage")
    page(name: "removePage")
	page(name: "authPage")
	page(name: "thermsPage")
	page(name: "sensorsPage")
    page(name: "preferencesPage")    
    page(name: "helperSmartAppsPage")    
    // Part of debug Dashboard
    page(name: "debugDashboardPage")
    page(name: "pollChildrenPage")
}

mappings {
	path("/oauth/initialize") {action: [GET: "oauthInitUrl"]}
	path("/oauth/callback") {action: [GET: "callback"]}
}


// Begin Preference Pages
def mainPage() {
	
	def deviceHandlersInstalled = testForDeviceHandlers()
    def readyToInstall = deviceHandlersInstalled
    
	dynamicPage(name: "mainPage", title: "Welcome to ecobee (Connect)", install: readyToInstall, uninstall: false, submitOnChange: true) {
    	def ecoAuthDesc = (state.authToken != null) ? "[Connected]\n" :"[Not Connected]\n"        
		
        // If not device Handlers we cannot proceed
        if(!deviceHandlersInstalled) {
			section() {
				paragraph "ERROR!\n\nYou MUST add the ${getChildThermostatName()} and ${getChildSensorName()} Device Handlers to the IDE BEFORE running the setup."				
			}		
        } else {
        	readyToInstall = true
        }
        
        if(state.initialized && !state.authToken) {
        	section() {
				paragraph "WARNING!\n\nYou are no longer connected to the ecobee API. Please re-Authorize below."				
			}
        }       

		if(state.authToken != null && deviceHandlersInstalled) {
			section("Devices") {
				def howManyThermsSel = settings.thermostats?.size() ?: 0
                def howManyTherms = state.numAvailTherms ?: "?"
                def howManySensors = state.numAvailSensors ?: "?"
                
                // Thermostats
				state.settingsCurrentTherms = settings.thermostats ?: []
    	    	href ("thermsPage", title: "Thermostats", description: "Tap to select Thermostats [${howManyThermsSel}/${howManyTherms}]")                
                
                // Sensors
            	if (settings.thermostats?.size() > 0) {
					state.settingsCurrentSensors = settings.ecobeesensors ?: []
                	def howManySensorsSel = settings.ecobeesensors?.size() ?: 0
                    if (howManySensorsSel > howManySensors) { howManySensorsSel = howManySensors } // This is due to the fact that you can remove alread selected hiden items
            		href ("sensorsPage", title: "Sensors", description: "Tap to select Sensors [${howManySensorsSel}/${howManySensors}]")
	            }
    	    }        
	        section("Preferences") {
    	    	href ("preferencesPage", title: "Preferences", description: "Tap to review SmartApp settings.")
                LOG("In Preferences page section after preferences line", 5, null, "trace")
        	}
            if ( debugLevel(5) ) {
	        	section ("Debug Dashboard") {
					href ("debugDashboardPage", description: "Tap to enter the Debug Dashboard", title: "Debug Dashboard")
    	    	}
			}
            section("Remove ecobee (Connect)") {
				href ("removePage", description: "Tap to remove ecobee (Connect) ", title: "Remove ecobee (Connect)")
        	}            
    	} // End if(state.authToken)
        
        // Setup our API Tokens       
		section("Ecobee Authentication") {
			href ("authPage", title: "ecobee Authorization", description: "${ecoAuthDesc}Tap for ecobee Credentials")
		}        
     
		section (getVersion())
	}
}


def removePage() {
	dynamicPage(name: "removePage", title: "Remove ecobee (Connect) and All Devices", install: false, uninstall: true) {
    	section ("WARNING!\n\nRemoving ecobee (Connect) also removes all Devices\n") {
        }
    }
}

// Setup OAuth between SmartThings and Ecobee clouds
def authPage() {
	LOG("=====> authPage() Entered", 5)

	if(!state.accessToken) { //this is an access token for the 3rd party to make a call to the connect app
		state.accessToken = createAccessToken()
	}

	def description = "Click to enter ecobee Credentials"
	def uninstallAllowed = false
	def oauthTokenProvided = false

	if(state.authToken) {
		description = "You are connected. Tap Done above."
		uninstallAllowed = true
		oauthTokenProvided = true
        state.connected = "full"
	} else {
		description = "Tap to enter ecobee Credentials"
	}

	def redirectUrl = buildRedirectUrl //"${serverUrl}/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}"
    LOG("authPage() --> RedirectUrl = ${redirectUrl}")
    
	// get rid of next button until the user is actually auth'd
	if (!oauthTokenProvided) {
    	LOG("authPage() --> in !oauthTokenProvided")    	
		return dynamicPage(name: "authPage", title: "ecobee Setup", nextPage: "", uninstall: uninstallAllowed) {
			section() {
				paragraph "Tap below to log in to the ecobee service and authorize SmartThings access. Be sure to press the 'Allow' button on the 2nd page."
				href url:redirectUrl, style:"embedded", required:true, title: "ecobee Account Authorization", description:description 
			}
		}
	} else {    	
        LOG("authPage() --> in else for oauthTokenProvided - ${state.authToken}.")
        return dynamicPage(name: "authPage", title: "ecobee Setup", nextPage: "mainPage", uninstall: uninstallAllowed) {
        	section() {
            	paragraph "Return to main menu."
                href url:redirectUrl, style: "embedded", state: "complete", title: "ecobee Account Authorization", description: description
                }
        }           
	}
}

// Select which Thermostats are to be used
def thermsPage(params) {
	LOG("=====> thermsPage() entered", 5)
    state.thermsPageVisited = true
        
	def stats = getEcobeeThermostats()
    LOG("thermsPage() -> thermostat list: ${stats}")
    LOG("thermsPage() starting settings: ${settings}")
    LOG("thermsPage() params passed? ${params}", 4, null, "trace")

    dynamicPage(name: "thermsPage", title: "Select Thermostats", params: params, nextPage: "", content: "thermsPage", uninstall: false) {    
    	section("Units") {
        	paragraph "NOTE: The units type (F or C) is determined by your Hub Location settings automatically. Please update your Hub settings (under My Locations) to change the units used. Current value is ${getTemperatureScale()}."
        }
    	section("Select Thermostats") {
			LOG("thersPage(): state.settingsCurrentTherms=${state.settingsCurrentTherms}   settings.thermostats=${settings.thermostats}", 4, null, "trace")
			if (state.settingsCurrentTherms != settings.thermostats) {
				LOG("state.settingsCurrentTherms != settings.thermostats determined!!!", 4, null, "trace")			
			} else { LOG("state.settingsCurrentTherms == settings.thermostats: No changes detected!", 4, null, "trace") }
        	paragraph "Tap below to see the list of ecobee thermostats available in your ecobee account and select the ones you want to connect to SmartThings."
			input(name: "thermostats", title:"Select Thermostats", type: "enum", required:false, multiple:true, description: "Tap to choose", params: params, metadata:[values:stats], , submitOnChange: true)        
        }      
    }      
}

def sensorsPage() {
	// Only show sensors that are part of the chosen thermostat(s)
    // Refactor to show the sensors under their corresponding Thermostats. Use Thermostat name as section header?
    LOG("=====> sensorsPage() entered. settings: ${settings}", 5)
    state.sensorsPageVisited = true

	def options = getEcobeeSensors() ?: []
	def numFound = options.size() ?: 0
    
    LOG("options = getEcobeeSensors == ${options}")

    dynamicPage(name: "sensorsPage", title: "Select Sensors", nextPage: "") {
		if (numFound > 0)  {
			section("Select Sensors"){
				LOG("sensorsPage(): state.settingsCurrentSensors=${state.settingsCurrentSensors}   settings.ecobeesensors=${settings.ecobeesensors}", 4, null, "trace")
				if (state.settingsCurrentSensors != settings.ecobeesensors) {
					LOG("state.settingsCurrentSensors != settings.ecobeesensors determined!!!", 4, null, "trace")					
				} else { LOG("state.settingsCurrentSensors == settings.ecobeesensors: No changes detected!", 4, null, "trace") }
				paragraph "Tap below to see the list of ecobee sensors available for the selected thermostat(s) and select the ones you want to connect to SmartThings."
                if (settings.showThermsAsSensor) { paragraph "NOTE: Also showing Thermostats as an available sensor to allow for actual temperature values to be used." }
				input(name: "ecobeesensors", title:"Select Ecobee Sensors (${numFound} found)", type: "enum", required:false, description: "Tap to choose", multiple:true, metadata:[values:options])
			}
		} else {
    		 // No sensors associated with this set of Thermostats was found
           LOG("sensorsPage(): No sensors found.", 4)
           section(""){
           		paragraph "No associated sensors were found. Click Done above."
           }
	    }        
	}
}

def preferencesPage() {
    LOG("=====> preferencesPage() entered. settings: ${settings}", 5)

    dynamicPage(name: "preferencesPage", title: "Update SmartApp Preferences", nextPage: "") {
		section("SmartApp Preferences") {
        	input(name: "holdType", title:"Select Hold Type", type: "enum", required:false, multiple:false, description: "Until I Change", metadata:[values:["Until I Change", "Until Next Program"]])
            paragraph "The 'Smart Auto Temperature Adjust' feature determines if you want to allow the thermostat setpoint to be changed using the arrow buttons in the Tile when the thermostat is in 'auto' mode."
            input(name: "smartAuto", title:"Use Smart Auto Temperature Adjust?", type: "bool", required:false, description: false)
            input(name: "pollingInterval", title:"Polling Interval (in Minutes)", type: "enum", required:false, multiple:false, description: "5", options:["5", "10", "15", "30"])
            input(name: "debugLevel", title:"Debugging Level (higher # for more information)", type: "enum", required:false, multiple:false, description: "3", metadata:[values:["5", "4", "3", "2", "1", "0"]])            
            paragraph "Showing a Thermostat as a Remote Sensor is useful if you need to access the actual temperature in the room where the Thermostat is located and not just the (average) temperature displayed on the Thermostat"
            input(name: "showThermsAsSensor", title:"Include Thermostats as a Remote Sensor?", type: "bool", required:false, description: false)
        }
	}
}

def debugDashboardPage() {
	LOG("=====> debugDashboardPage() entered.", 5)
    
    
    dynamicPage(name: "debugDashboardPage", title: "") {
    	section (getVersion())
		section("Commands") {
        	href(name: "pollChildrenPage", title: "", required: false, page: "pollChildrenPage", description: "Tap to execute command: pollChildren()")
        }    	
    	section("Settings Information") {
        	paragraph "debugLevel: ${settings.debugLevel} (default=3 if null)"
            paragraph "holdType: ${settings.holdType} (default='Until I Change' if null)"
            paragraph "pollingInterval: ${settings.pollingInterval} {default=5 if null)"
            paragraph "showThermsAsSensor: ${settings.showThermsAsSensor} {default=false if null)"
            paragraph "smartAuto: ${settings.smartAuto} (default=false if null)"   
            paragraph "Selected Thermostats: ${settings.thermostats}"
        }
    	section("Commands") {
        	href(name: "pollChildrenPage", title: "", required: false, page: "pollChildrenPage", description: "Tap to execute command: pollChildren()")
            href ("removePage", description: "Tap to remove ecobee (Connect) ", title: "")
        }
    }    
}

// pages that are part of Debug Dashboard
def pollChildrenPage() {
	LOG("=====> pollChildrenPage() entered.", 5)
    state.lastPoll = 0 // Reset to force the poll to happen
	pollChildren(null)
    
	dynamicPage(name: "pollChildrenPage", title: "") {
    	section() {
        	paragraph "pollChildren() was called"
        }
    }    
}


def helperSmartAppsPage() {


}
// End Prefernce Pages


// Preference Pages Helpers
private def Boolean testForDeviceHandlers() {
	if (state.runTestOnce != null) { return state.runTestOnce }
    
    def DNIAdder = now().toString()
    def d1
    def d2
    def success = true
    
	try {    	
		d1 = addChildDevice(app.namespace, getChildThermostatName(), "dummyThermDNI-${DNIAdder}", null, ["label":"Ecobee Thermostat:TestingForInstall"])			
		d2 = addChildDevice(app.namespace, getChildSensorName(), "dummySensorDNI-${DNIAdder}", null, ["label":"Ecobee Sensor:TestingForInstall"])
	} catch (physicalgraph.app.exception.UnknownDeviceTypeException e) {
		LOG("You MUST add the ${getChildThermostatName()} and ${getChildSensorName()} Device Handlers to the IDE BEFORE running the setup.", 1, null, "error")
		success = false
	}
    
    state.runTestOnce = success
    
    if (d1) deleteChildDevice("dummyThermDNI-${DNIAdder}") 
    if (d2) deleteChildDevice("dummySensorDNI-${DNIAdder}") 
    
    return success
}

// End Preference Pages Helpers

// OAuth Init URL
def oauthInitUrl() {
	LOG("oauthInitUrl with callback: ${callbackUrl}", 5)
	state.oauthInitState = UUID.randomUUID().toString()

	def oauthParams = [
			response_type: "code",
			client_id: smartThingsClientId,			
			scope: "smartRead,smartWrite",
			redirect_uri: callbackUrl, //"https://graph.api.smartthings.com/oauth/callback"
			state: state.oauthInitState			
	]

	LOG("oauthInitUrl - Before redirect: location: ${apiEndpoint}/authorize?${toQueryString(oauthParams)}", 4)
	redirect(location: "${apiEndpoint}/authorize?${toQueryString(oauthParams)}")
}

// OAuth Callback URL and helpers
def callback() {
	LOG("callback()>> params: $params, params.code ${params.code}, params.state ${params.state}, state.oauthInitState ${state.oauthInitState}", 4)
	def code = params.code
	def oauthState = params.state

	//verify oauthState == state.oauthInitState, so the callback corresponds to the authentication request
	if (oauthState == state.oauthInitState){
    	LOG("callback() --> States matched!", 4)
		def tokenParams = [
			grant_type: "authorization_code",
			code      : code,
			client_id : smartThingsClientId,
			state	  : oauthState,
			redirect_uri: callbackUrl //"https://graph.api.smartthings.com/oauth/callback"
		]

		def tokenUrl = "${apiEndpoint}/token?${toQueryString(tokenParams)}"
        LOG("callback()-->tokenURL: ${tokenUrl}", 2)

		httpPost(uri: tokenUrl) { resp ->
			state.refreshToken = resp.data.refresh_token
			state.authToken = resp.data.access_token
            
            LOG("Expires in ${resp?.data?.expires_in} seconds")
            state.authTokenExpires = now() + (resp.data.expires_in * 1000)
            LOG("swapped token: $resp.data; state.refreshToken: ${state.refreshToken}; state.authToken: ${state.authToken}", 2)
		}

		if (state.authToken) {
        	state.lastTokenRefresh = now()
			success()            
		} else {
			fail()
		}

	} else {
    	LOG("callback() failed oauthState != state.oauthInitState", 1, null, "warn")
	}

}

def success() {
	def message = """
    <p>Your ecobee Account is now connected to SmartThings!</p>
    <p>Click 'Done' to finish setup.</p>
    """
	connectionStatus(message)
}

def fail() {
	def message = """
        <p>The connection could not be established!</p>
        <p>Click 'Done' to return to the menu.</p>
    """
	connectionStatus(message)
}

def connectionStatus(message, redirectUrl = null) {
	def redirectHtml = ""
	if (redirectUrl) {
		redirectHtml = """
			<meta http-equiv="refresh" content="3; url=${redirectUrl}" />
		"""
	}

	def html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=640">
<title>Ecobee & SmartThings connection</title>
<style type="text/css">
        @font-face {
                font-family: 'Swiss 721 W01 Thin';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
                font-weight: normal;
                font-style: normal;
        }
        @font-face {
                font-family: 'Swiss 721 W01 Light';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
                         url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
                font-weight: normal;
                font-style: normal;
        }
        .container {
                width: 90%;
                padding: 4%;
                /*background: #eee;*/
                text-align: center;
        }
        img {
                vertical-align: middle;
        }
        p {
                font-size: 2.2em;
                font-family: 'Swiss 721 W01 Thin';
                text-align: center;
                color: #666666;
                padding: 0 40px;
                margin-bottom: 0;
        }
        span {
                font-family: 'Swiss 721 W01 Light';
        }
</style>
</head>
<body>
        <div class="container">
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/ecobee%402x.png" alt="ecobee icon" />
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/st-logo%402x.png" alt="SmartThings logo" />
                ${message}
        </div>
</body>
</html>
"""

	render contentType: 'text/html', data: html
}
// End OAuth Callback URL and helpers

// Get the list of Ecobee Thermostats for use in the settings pages
def getEcobeeThermostats() {	
	LOG("====> getEcobeeThermostats() entered", 5)	
    
 	def requestBody = '{"selection":{"selectionType":"registered","selectionMatch":"","includeRuntime":true,"includeSensors":true,"includeProgram":true}}'

	def deviceListParams = [
			uri: apiEndpoint,
			path: "/1/thermostat",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.authToken}"],
			query: [format: 'json', body: requestBody]
	]

	def stats = [:]
    try {
        httpGet(deviceListParams) { resp ->

		LOG("httpGet() response: ${resp.data}")
        
        // Initialize the Thermostat Data. Will reuse for the Sensor List intialization
        state.thermostatData = resp.data
        	
        
            if (resp.status == 200) {
            	LOG("httpGet() in 200 Response")
                state.numAvailTherms = resp.data.thermostatList?.size() ?: 0
                
            	resp.data.thermostatList.each { stat ->
					def dni = [app.id, stat.identifier].join('.')
					stats[dni] = getThermostatDisplayName(stat)
                }
            } else {                
                LOG("httpGet() - in else: http status: ${resp.status}")
                //refresh the auth token
                if (resp.status == 500 && resp.data.status.code == 14) {
                	LOG("Storing the failed action to try later")
                    state.action = "getEcobeeThermostats"
                    LOG("Refreshing your auth_token!", 1)
                    refreshAuthToken()
                } else {
                	LOG("Other error. Status: ${resp.status}  Response data: ${resp.data} ", 1)
                }
            }
        }
    } catch(Exception e) {
    	LOG("___exception getEcobeeThermostats(): ${e}", 1, null, "error")
        refreshAuthToken()
    }
	state.thermostatsWithNames = stats
    LOG("state.thermostatsWithNames == ${state.thermostatsWithNames}", 4)
	return stats
}

// Get the list of Ecobee Sensors for use in the settings pages (Only include the sensors that are tied to a thermostat that was selected)
// NOTE: getEcobeeThermostats() should be called prior to getEcobeeSensors to refresh the full data of all thermostats
Map getEcobeeSensors() {	
    LOG("====> getEcobeeSensors() entered. thermostats: ${thermostats}", 5)

	def sensorMap = [:]
    def foundThermo = null
	// TODO: Is this needed?
	state.remoteSensors = [:]    

	// Need to query to get full list of Thermostats (need to pull this here as we can call getEcobeeSensors out of sequence after initial setup
    // TODO: Check on possible race conditions. Leave to update and initialize procedures to call in sequence?
    // getEcobeeThermostats()
	
	state.thermostatData.thermostatList.each { singleStat ->
		LOG("thermostat loop: singleStat.identifier == ${singleStat.identifier} -- singleStat.remoteSensors == ${singleStat.remoteSensors} ", 4)
        
    	if (!settings.thermostats.findAll{ it.contains(singleStat.identifier) } ) {
        	// We can skip this thermostat as it was not selected by the user
            LOG("getEcobeeSensors() --> Skipping this thermostat: ${singleStat.identifier}", 5)
        } else {
        	LOG("getEcobeeSensors() --> Entering the else... we found a match. singleStat == ${singleStat.name}", 4)
                        
        	state.remoteSensors = state.remoteSensors ? (state.remoteSensors + singleStat.remoteSensors) : singleStat.remoteSensors
            LOG("After state.remoteSensors setup...", 5)	        
                        
    	    // WORKAROUND: Iterate over remoteSensors list and add in the thermostat DNI
        	// 		 This is needed to work around the dynamic enum "bug" which prevents proper deletion
            // TODO: Check to see if this is still needed. Seem to use elibleSensors now instead
        	// singleStat.remoteSensors.each { tempSensor ->
        	//	tempSensor.thermDNI = "${thermostat}"
            //	state.remoteSensors = state.remoteSensors + tempSensor
			//}
            LOG("getEcobeeSensors() - singleStat.remoteSensors: ${singleStat.remoteSensors}", 4)
            LOG("getEcobeeSensors() - state.remoteSensors: ${state.remoteSensors}", 4)
		}
        
        LOG("remoteSensors all before each loop: ${state.remoteSensors}", 5, null, "trace")
		state.remoteSensors.each {
        	LOG("Looping through each remoteSensor. Current remoteSensor: ${it}", 5, null, "trace")
			if (it.type == "ecobee3_remote_sensor") {
            	LOG("Adding an ecobee3_remote_sensor: ${it}", 4, null, "trace")
				def value = "${it?.name}"
				def key = "ecobee_sensor-"+ it?.id + "-" + it?.code
				sensorMap["${key}"] = value
			} else if ( (it.type == "thermostat") && (settings.showThermsAsSensor == true) ) {            	
				LOG("Adding a Thermostat as a Sensor: ${it}", 4, null, "trace")
                def value = "${it?.name}"
				def key = "ecobee_sensor_thermostat-"+ it?.id + "-" + it?.name
                LOG("Adding a Thermostat as a Sensor: ${it}, key: ${key}  value: ${value}", 4, null, "trace")
				sensorMap["${key}"] = value + " (Thermostat)"
            } else {
            	LOG("Did NOT add: ${it}. settings.showThermsAsSensor=${settings.showThermsAsSensor}", 4, null, "trace")
            }
		}
	} // end thermostats.each loop
	
    LOG("getEcobeeSensors() - remote sensor list: ${sensorMap}", 4)
    state.eligibleSensors = sensorMap
    state.numAvailSensors = sensorMap.size() ?: 0
	return sensorMap
        
}
        
     
def getThermostatDisplayName(stat) {
	if(stat?.name)
		return stat.name.toString()
	return (getThermostatTypeName(stat) + " (${stat.identifier})").toString()
}

def getThermostatTypeName(stat) {
	return stat.modelNumber == "siSmart" ? "Smart Si" : "Smart"
}

def installed() {
	LOG("Installed with settings: ${settings}", 4)	
	return initialize()
}

def updated() {	
    LOG("Updated with settings: ${settings}", 4)
	unsubscribe() // Do we really need/want to unsubscribe to anything?
    
    // refresh Thermostats and Sensor full lists
    getEcobeeThermostats()
    getEcobeeSensors()
    
    // Children
	if (settings.thermostats?.size() > 0) { 
    	createChildrenThermostats() 
		if (settings.ecobeesensors?.size() > 0) { createChildrenSensors() } 
	}        
    deleteUnusedChildren()
    
        
    // Force the rescheduling
    state.lastScheduledPoll = 0
    state.lastScheduledTokenRefresh = 0
    state.lastPoll = 0
    state.lastTokenRefresh = 0
    scheduleHandlers()	    
    
    // Add subscriptions as little "daemons" that will check on our health
    subscribe(location, "routineExecuted", scheduleHandlers)
    subscribe(location, "sunset", scheduleHandlers)
    subscribe(location, "sunrise", scheduleHandlers)    
}

def initialize() {	
    LOG("=====> initialize()", 4)
    if (state.initialized) {
    	LOG("initialized() called more than once. Please contact the developer of this app.", 1, null, "error")
        return false
    }    
    state.connected = "full"    
    unschedule() // Shouldn't be needed as we are only calling intialize once now, but just in case
    state.reAttempt = 0
    
    // Initialize several variables
	state.lastScheduledPoll = 0
    state.lastScheduledTokenRefresh = 0    
	state.lastPoll = 0
	state.lastTokenRefresh = 0
    
    // Setup initial polling and determine polling intervals
	state.pollingInterval = (settings.pollingInterval?.toInteger() >= 5) ? settings.pollingInterval.toInteger() : 5
    state.tokenGrace = 16 // Anything more than this then we have a possible failed 
	
    // Children
    def aOK = true
	if (settings.thermostats?.size() > 0) { aOK = aOK && createChildrenThermostats() }
	if (settings.ecobeesensors?.size() > 0) { aOK = aOK && createChildrenSensors() }
    deleteUnusedChildren()
    
    // Schedule the various handlers
    scheduleHandlers()    
    
    // Add subscriptions as little "daemons" that will check on our health
    subscribe(location, "routineExecuted", scheduleHandlers)
    subscribe(location, "sunset", scheduleHandlers)
    subscribe(location, "sunrise", scheduleHandlers)
    
    // TODO: Add ability to add additional physical (or virtual) items to subscribe to that have events generated that could heal our app
    
    //send activity feeds to tell that device is connected
    def notificationMessage = aOK ? "is connected to SmartThings" : "had an error during setup of devices"
    sendActivityFeeds(notificationMessage)
    state.timeSendPush = null    
    state.initialized = true
        
    return aOK
}

private def createChildrenThermostats() {
	LOG("createChildrenThermostats() entered: thermostats=${settings.thermostats}", 5)
    // Create the child Thermostat Devices
	def devices = settings.thermostats.collect { dni ->
		def d = getChildDevice(dni)
		if(!d) {
        	// TODO: Place in a try block and check for this exception: physicalgraph.app.exception.UnknownDeviceTypeException
            try {
				d = addChildDevice(app.namespace, getChildThermostatName(), dni, null, ["label":"Ecobee Thermostat:${state.thermostatsWithNames[dni]}"])			
			} catch (physicalgraph.app.exception.UnknownDeviceTypeException e) {
            	LOG("You MUST add the ${getChildSensorName()} Device Handler to the IDE BEFORE running the setup.", 1, null, "error")
                return false
            }
            LOG("created ${d.displayName} with id $dni", 4)
		} else {
			LOG("found ${d.displayName} with id $dni already exists", 4)            
		}
		return d
	}    
    
    LOG("Created/Updated ${devices.size()} thermostats")    
    return true
}

private def createChildrenSensors() {
	LOG("createChildrenSensors() entered: ecobeesensors=${settings.ecobeesensors}", 5)
    // Create the child Ecobee Sensor Devices
	def sensors = settings.ecobeesensors.collect { dni ->
		def d = getChildDevice(dni)
		if(!d) {
        	// TODO: Place in a try block and check for this exception: physicalgraph.app.exception.UnknownDeviceTypeException
            try {
				d = addChildDevice(app.namespace, getChildSensorName(), dni, null, ["label":"Ecobee Sensor:${state.eligibleSensors[dni]}"])
			} catch (physicalgraph.app.exception.UnknownDeviceTypeException e) {
            	LOG("You MUST add the ${getChildSensorName()} Device Handler to the IDE BEFORE running the setup.", 1, null, "error")
                return false
            }
            LOG("created ${d.displayName} with id $dni", 4)
		} else {
        	LOG("found ${d.displayName} with id $dni already exists", 4)
		}
		return d
	}
    
	LOG("Created/Updated ${sensors.size()} sensors.")
    return true
}

// NOTE: For this to work effectively getEcobeeThermostats() and getEcobeeSensors() should be called prior
private def deleteUnusedChildren() {
	LOG("deleteUnusedChildren() entered", 5)    
    
    if (settings.thermostats?.size() == 0) {
    	// No thermostats, need to delete all children
        LOG("Deleting All My Children!", 2, null, "warn")
    	getAllChildDevices().each { deleteChildDevice(it.deviceNetworkId) }        
    } else {
    	// Only delete those that are no longer in the list
        // This should be a combination of any removed thermostats and any removed sensors
        def allMyChildren = getAllChildDevices()
        LOG("These are currently all of my childred: ${allMyChildren}", 5, null, "debug")
        
        // Update list of "eligibleSensors"       
        def childrenToKeep = thermostats + (state.eligibleSensors?.keySet() ?: [])
        LOG("These are the children to keep around: ${childrenToKeep}", 4, null, "trace")
        
    	def childrenToDelete = allMyChildren.findAll { !childrenToKeep.contains(it.deviceNetworkId) }        
        
        LOG("Ready to delete these devices. ${childrenToDelete}", 4, null, "trace")
		childrenToDelete.each { deleteChildDevice(it.deviceNetworkId) } //inherits from SmartApp (data-management)    
    }    
}
	

def scheduleHandlers() {
	if(state.connected == "lost") {
    	LOG("Unable to schedule handlers do to loss of API Connection. Please ensure you are authorized.", 1, null, "error")
    	return
    }
    
	// state.lastScheduledPoll == last time the poll() was run via the scheduler
    // state.lastScheduledTokenRefresh == last time the refreshAuthToken was run via the scheduler
	//automatically update devices status every 5 mins    
	def timeSinceLastScheduledPoll = (state.lastScheduledPoll == 0 || !state.lastScheduledPoll) ? 0 : ((now() - state.lastScheduledPoll?.toDouble()) / 1000 / 60)
    def timeSinceLastScheduledRefresh = (state.lastScheduledTokenRefresh == 0 || !state.lastScheduledTokenRefresh) ? 0 : ((now() - state.lastScheduledTokenRefresh?.toDouble()) / 1000 / 60)
	def timeBeforeExpiry = state.authTokenExpires ? ((state.authTokenExpires - now()) / 1000 / 60) : 0    
    
    LOG("Time since last poll? ${timeSinceLastScheduledPoll} -- state.lastScheduledPoll == ${state.lastScheduledPoll}", 4, null, "info")
    LOG("Time since last token refresh? ${timeSinceLastScheduledRefresh} -- state.lastScheduledTokenRefresh == ${state.lastScheduledTokenRefresh}", 4, null, "info")
    LOG("timeLeft until expiry (in min): ${timeBeforeExpiry}", 4, null, "info")
    
    // Reschedule polling if it has been a while since the previous poll
    def interval = (settings.pollingInterval?.toInteger() >= 5) ? settings.pollingInterval.toInteger() : 5
    if ( (timeSinceLastScheduledPoll == 0) || (timeSinceLastPoll >= (interval * 2)) || (!state.initialized) ) {
    	// automatically update devices status every ${interval} mins    
        // re-establish polling
        LOG("pollChildren() - Rescheduling handlers due to delays!", 1, child, "warn")
        unschedule("poll")
		"runEvery${interval}Minutes"("poll")        
        runIn(15, "pollScheduled")
    }    
    
    // Reschedule Authrefresh if we are over the grace period    
	if ( (timeSinceLastScheduledRefresh == 0)  || (timeBeforeExpiry >= state.tokenGrace) || (!state.initialized) ) {
    	unschedule("refreshAuthTokenScheduled")
		runEvery15Minutes("refreshAuthTokenScheduled")        
        refreshAuthTokenScheduled()
	}    
}

def pollScheduled() {
	state.lastScheduledPoll = now()
    poll()
}

def refreshAuthTokenScheduled() {
	state.lastScheduledTokenRefresh = now()
    refreshAuthToken()
}


// Called during initialization to get the inital poll
def pollHandler() {
	LOG("pollHandler()", 5)
    state.lastPoll = 0 // Initialize the variable and force a poll even if there was one recently    
	pollChildren(null) // Hit the ecobee API for update on all thermostats
}


def pollChildren(child = null) {
	LOG("=====> pollChildren()", 4)
    
    if (apiConnected() == "lost") {
        LOG("pollChildren() - Unable to pollChildren() due to API not being connected", 1, child)
    	return
    }
    
    if (settings.thermostats?.size() < 1) {
    	LOG("pollChildren() - Nothing to poll as there are no thermostats currently selected", 1, child, "warn")
		return    
    }    

    // Check to see if it is time to do an full poll to the Ecobee servers. If so, execute the API call and update ALL children
    def timeSinceLastPoll = (state.lastPoll == 0) ? 0 : ((now() - state.lastPoll?.toDouble()) / 1000 / 60) 
    LOG("Time since last poll? ${timeSinceLastPoll} -- state.lastPoll == ${state.lastPoll}", 3, child, "info")
    
    // TODO: Let the scheduleHandlers() function do this instead
    // Reschedule polling if it has been a while since the previous poll
    def interval = (settings.pollingInterval?.toInteger() >= 5) ? settings.pollingInterval.toInteger() : 5
    if ( timeSinceLastPoll >= (interval * 2) ) {
    	// automatically update devices status every ${interval} mins    
        // re-establish polling
        LOG("pollChildren() - Rescheduling handlers due to delays!", 1, child, "warn")
        unschedule()
		"runEvery${interval}Minutes"("poll")
        runEvery15Minutes("refreshAuthToken")
    }
    
    if ( (state.lastPoll == 0) || ( timeSinceLastPoll > getMinMinBtwPolls().toDouble() ) ) {
    	// It has been longer than the minimum delay
        LOG("Calling the Ecobee API to fetch the latest data...", 4, child)
    	pollEcobeeAPI(getChildThermostatDeviceIdsString())  // This will update the values saved in the state which can then be used to send the updates
	} else {
        LOG("pollChildren() - Not time to call the API yet. It has been ${timeSinceLastPoll} minutes since last full poll.", 4, child)
        generateEventLocalParams() // Update any local parameters and send
    }

	
	// Iterate over all the children
	def d = getChildDevices()
    d?.each() { oneChild ->
    	LOG("pollChildren() - Processing poll data for child: ${oneChild} has ${oneChild.capabilities}", 4)
        
    	if( oneChild.hasCapability("Thermostat") ) {
        	// We found a Thermostat, send all of its events
            LOG("pollChildren() - We found a Thermostat!", 5)
            oneChild.generateEvent(state.thermostats[oneChild.device.deviceNetworkId]?.data)
        } else {
        	// We must have a remote sensor
            LOG("pollChildren() - Updating sensor data for ${oneChild}: ${oneChild.device.deviceNetworkId} data: ${state.remoteSensorsData[oneChild.device.deviceNetworkId]?.data}", 4)
            oneChild.generateEvent(state.remoteSensorsData[oneChild.device.deviceNetworkId]?.data)
        } 
    }
}

private def generateEventLocalParams() {
	// Iterate over all the children
    LOG("Entered generateEventLocalParams() ", 5)
	def d = getChildDevices()
    d?.each() { oneChild ->
    	LOG("generateEventLocalParams() - Processing poll data for child: ${oneChild} has ${oneChild.capabilities}", 4)
        
    	if( oneChild.hasCapability("Thermostat") ) {
        	// We found a Thermostat, send local params as events
            LOG("generateEventLocalParams() - We found a Thermostat!")
            def data = [
            	apiConnected: apiConnected()
            ]
            
            state.thermostats[oneChild.device.deviceNetworkId].data?.apiConnected = apiConnected()            
            oneChild.generateEvent(data)
        } else {
        	// We must have a remote sensor
            LOG("generateEventLocalParams() - Updating sensor data: ${oneChild.device.deviceNetworkId}")
			// No local params to send            
        } 
    }

}

private def pollEcobeeAPI(thermostatIdsString = "") {
	LOG("=====> pollEcobeeAPI() entered - thermostatIdsString = ${thermostatIdsString}", 2, null, "info")

	// TODO: Check on any running EVENTs on thermostat

	// def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + thermostatIdsString + '","includeExtendedRuntime":"false","includeSettings":"true","includeRuntime":"true","includeEquipmentStatus":"true","includeSensors":true,"includeWeather":true,"includeProgram":true}}'
	def jsonRequestBody = buildBodyRequest("thermostatInfo", null, thermostatIdsString, null).toString()
    
    LOG("buildBodyRequest returned: ${jsonRequestBody}", 5)
    

    
    def result = false
	
	def pollParams = [
			uri: apiEndpoint,
			path: "/1/thermostat",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.authToken}"],
			query: [format: 'json', body: jsonRequestBody]
	]

	try{
		httpGet(pollParams) { resp ->

//            if (resp.data) {
//                debugEventFromParent(child, "pollChildren(child) >> resp.status = ${resp.status}, resp.data = ${resp.data}")
//            }

			if(resp.status == 200) {
				LOG("poll results returned resp.data ${resp.data}", 2)
				state.remoteSensors = resp.data.thermostatList.remoteSensors
				state.thermostatData = resp.data
               
                // Create the list of sensors and related data
				updateSensorData()
                // Create the list of thermostats and related data
				updateThermostatData()
                
				result = true
                
                if (state.connected != "full") {
					state.connected = "full"
                    generateEventLocalParams() // Update the connection status
                }
                state.lastPoll = now();
				LOG("httpGet: updated ${state.thermostats?.size()} stats: ${state.thermostats}")
			} else {
				LOG("pollEcobeeAPI() - polling children & got http status ${resp.status}", 1, null, "error")

				//refresh the auth token
				if (resp.status == 500 && resp.data.status.code == 14) {
					LOG("Resp.status: ${resp.status} Status Code: ${resp.data.status.code}. Unable to recover", 1, null, "error")
                    // Not possible to recover from a code 14
                    apiLost("pollEcobeeAPI() - Resp.status: ${resp.status} Status Code: ${resp.data.status.code}. Unable to recover.")
				}
				else {
					LOG("pollEcobeeAPI() - Other responses received. Resp.status: ${resp.status} Status Code: ${resp.data.status.code}.", 1, null, "error")
				}
			}
		}
	} catch (groovyx.net.http.HttpResponseException e) {    
		LOG("pollEcobeeAPI() >> HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}  response? data: ${e.getResponse()?.getData()}", 1, null, "error")
        
		def reAttemptPeriod = 45 // in sec
		if ( (e.statusCode == 500 && e.getResponse()?.data.status.code == 14) ||  (e.statusCode == 401 && e.getResponse()?.data.status.code == 14) ) {
        	// Not possible to recover from status.code == 14
            LOG("In HttpResponseException: Received data.stat.code of 14", 1, null, "error")
        	apiLost("pollEcobeeAPI() - In HttpResponseException: Received data.stat.code of 14")
		} else if (e.statusCode != 401) { //this issue might comes from exceed 20sec app execution, connectivity issue etc.
        	LOG("In HttpResponseException - statusCode != 401 (${e.statusCode})", 1, null, "warn")
            state.connected = "warn"
            generateEventLocalParams() // Update the connected state at the thermostat devices
			runIn(reAttemptPeriod, "pollEcobeeAPI") // retry to poll
		} else if (e.statusCode == 401) { // Status.code other than 14
			state.reAttemptPoll = state.reAttemptPoll + 1
			LOG("statusCode == 401: reAttempt refreshAuthToken to try = ${state.reAttemptPoll}", 1, null, "warn")
			if (state.reAttemptPoll <= 3) {
               	state.connected = "warn"
           		generateEventLocalParams() // Update the connected state at the thermostat devices
				runIn(reAttemptPeriod, "pollEcobeeAPI")
			} else {
               	LOG("Unable to poll EcobeeAPI after three attempts. Will try to refresh authtoken.", 1, null, "error")
                debugEvent( "Unable to poll EcobeeAPI after three attempts. Will try to refresh authtoken." )
                refreshAuthToken()
			}
		}    
    } catch (java.util.concurrent.TimeoutException e) {
		LOG("pollEcobeeAPI(), TimeoutException: ${e}.", 1, null, "warn")
        
	} catch (Exception e) {
    	LOG("pollEcobeeAPI(): General Exception: ${e}.", 1, null, "error")
    }
    LOG("<===== Leaving pollEcobeeAPI() results: ${result}", 5)
	return result
    
}


// poll() will be called on a regular interval using a runEveryX command
void poll() {	
    // Check to see if we are connected to the API or not
    if (apiConnected() == "lost") {
    	LOG("poll() - Skipping poll() due to lost API Connection", 1, null, "warn")
    } else {
    	LOG("poll() - Polling children with pollChildren(null)", 4)
    	pollChildren(null) // Poll ALL the children at the same time for efficiency
    }
}


def availableModes(child) {	
	def tData = state.thermostats[child.device.deviceNetworkId]
    LOG("state.thermostats = ${state.thermostats}", 3, child)
	LOG("Child DNI = ${child.device.deviceNetworkId}", 3, child)
	LOG("Data = ${tData}", 3, child)
	

	if(!tData)
	{
		LOG("ERROR: Device connection removed? no data for ${child.device.deviceNetworkId} after polling", 1, child, "error")

		// TODO: flag device as in error state
		// child.errorState = true

		return null
	}

	def modes = ["off"]

	if (tData.data.heatMode) modes.add("heat")
	if (tData.data.coolMode) modes.add("cool")
	if (tData.data.autoMode) modes.add("auto")
    // TODO: replace the use of auxHeatOnly with "emergency heat" to conform to the thermostatMode attributes allowed values
    // if (tData.data.auxHeatMode) modes.add("emergency heat")
	if (tData.data.auxHeatMode) modes.add("auxHeatOnly")

	modes

}

def currentMode(child) {
	def tData = state.thermostats[child.device.deviceNetworkId]
	LOG("state.thermostats = ${state.thermostats}", 3, child)
	LOG("Child DNI = ${child.device.deviceNetworkId}", 3, child)
	LOG("Data = ${tData}", 3, child)
    

	if(!tData) {
		LOG("ERROR: Device connection removed? no data for ${child.device.deviceNetworkId} after polling", 1, child, "error")

		// TODO: flag device as in error state
		// child.errorState = true

		return null
	}

	def mode = tData.data.thermostatMode

	mode
}

def updateSensorData() {
	LOG("Entered updateSensorData() ", 5)
 	def sensorCollector = [:]
                
	state.remoteSensors.each {
		it.each {
			if ( ( it.type == "ecobee3_remote_sensor" ) || ((it.type == "thermostat") && (settings.showThermsAsSensor)) ) {
				// Add this sensor to the list
				def sensorDNI 
                if (it.type == "ecobee3_remote_sensor") { 
                	sensorDNI = "ecobee_sensor-" + it?.id + "-" + it?.code 
				} else { 
                	LOG("We have a Thermostat based Sensor! it=${it}", 4, null, "trace")
                	sensorDNI = "ecobee_sensor_thermostat-"+ it?.id + "-" + it?.name
				}
				LOG("sensorDNI == ${sensorDNI}", 4)
            	                
				def temperature = ""
				def occupancy = ""
                            
				it.capability.each { cap ->
					if (cap.type == "temperature") {
                    	LOG("updateSensorData() - Sensor (DNI: ${sensorDNI}) temp is ${cap.value}", 4)
                        if ( cap.value.isNumber() ) { // Handles the case when the sensor is offline, which would return "unknown"
							temperature = cap.value as Double
							temperature = wantMetric() ? (temperature / 10).toDouble().round(1) : (temperature / 10).toDouble().round(0)
                        } else if (cap.value == "unknown") {
                        	// TODO: Do something here to mark the sensor as offline?
                            LOG("updateSensorData() - sensor (DNI: ${sensorDNI}) returned unknown temp value. Perhaps it is unreachable.", 1, null, "warn")
                            // Need to mark as offline somehow
                            temperature = "unknown"
                            
                        } else {
                        	 LOG("updateSensorData() - sensor (DNI: ${sensorDNI}) returned ${cap.value}.", 1, null, "error")
                        }
					} else if (cap.type == "occupancy") {
						if(cap.value == "true") {
							occupancy = "active"
            	        } else if (cap.value == "unknown") {
                        	// Need to mark as offline somehow
                            LOG("Setting sensor occupancy to unknown", 2, null, "warn")
                            occupancy = "unknown"
                        } else {
							occupancy = "inactive"
						}
                            
					}
				}
                                            				
				def sensorData = [
					temperature: ((temperature == "unknown") ? "unknown" : myConvertTemperatureIfNeeded(temperature, "F", 1)),
					motion: occupancy
				]
				sensorCollector[sensorDNI] = [data:sensorData]
                LOG("sensorCollector being updated with sensorData: ${sensorData}", 4)
                
			} else if ( (it.type == "thermostat") && (settings.showThermsAsSensor) ) { 
            	// Also update the thermostat based Remote Sensor
                
            
            } // end thermostat else if
		} // End it.each loop
	} // End remoteSensors.each loop
	state.remoteSensorsData = sensorCollector
	LOG("updateSensorData(): found these remoteSensors: ${sensorCollector}", 4)
                
}

def updateThermostatData() {
	// Create the list of thermostats and related data
	state.thermostats = state.thermostatData.thermostatList.inject([:]) { collector, stat ->
		def dni = [ app.id, stat.identifier ].join('.')

		LOG("Updating dni $dni, Got weather? ${stat.weather.forecasts[0].weatherSymbol.toString()}")
        
        // TODO: Put a wrapper here based on the thermostat brand
        def thermSensor = stat.remoteSensors.find { it.type == "thermostat" }
        LOG("updateThermostatData() - thermSensor == ${thermSensor}" )
        
        def occupancyCap = thermSensor?.capability.find { it.type == "occupancy" }
        LOG("updateThermostatData() - occupancyCap = ${occupancyCap} value = ${occupancyCap.value}")
        
        // Check to see if there is even a value, not all types have a sensor
        def occupancy =  occupancyCap.value ?: "not support"
        
        LOG("Program data: ${stat.program}  Current climate (ref): ${stat.program?.currentClimateRef}", 4)
        
        // Determine if an Event is running, find the first running event
        def runningEvent = null
        
        if ( stat.events.size() > 0 ) {         
        	runningEvent = stat.events.find { 
            	LOG("Checking event: ${it}", 5) 
                it.running == true
            }        	
        }        
        
        def usingMetric = wantMetric() // cache the value to save the function calls
        def tempTemperature = myConvertTemperatureIfNeeded( (stat.runtime.actualTemperature.toDouble() / 10), "F", (usingMetric ? 1 : 0))
        def tempHeatingSetpoint = myConvertTemperatureIfNeeded( (stat.runtime.desiredHeat.toDouble() / 10), "F", (usingMetric ? 1 : 0))
        def tempCoolingSetpoint = myConvertTemperatureIfNeeded( (stat.runtime.desiredCool.toDouble() / 10), "F", (usingMetric ? 1 : 0))
        def tempWeatherTemperature = myConvertTemperatureIfNeeded( ((stat.weather.forecasts[0].temperature.toDouble() / 10)), "F", (usingMetric ? 1 : 0))
                
        def currentClimateName = ""
		def currentClimateId = ""
        def currentFanMode = ""
                 
        if (runningEvent) {         	
            LOG("Found a running Event: ${runningEvent}", 4) 
            def tempClimateRef = runningEvent.holdClimateRef ?: ""
        	if ( runningEvent.type == "hold" ) {
               	currentClimateName = "Hold" + (runningEvent.holdClimateRef ? ": ${runningEvent.holdClimateRef.capitalize()}" : "")
			} else if (runningEvent.type == "vacation" ) {
               	currentClimateName = "Vacation"
            } else if (runningEvent.type == "quickSave" ) {
               	currentClimateName = "Quick Save"                
            } else if (runningEvent.type == "autoAway" ) {
             	currentClimateName = "Auto Away"
            } else if (runningEvent.type == "autoHome" ) {
               	currentClimateName = "Auto Home"
            } else {                
               	currentClimateName = runningEvent.type
            }
            currentClimateId = runningEvent.holdClimateRef
		} else if (stat.program?.currentClimateRef) {
        	currentClimateName = (stat.program.climates.find { it.climateRef == stat.program.currentClimateRef }).name
        	currentClimateId = stat.program.currentClimateRef            
        } else {
        	LOG("updateThermostatData() - No climateRef or running Event was found", 1, null, "error")
            currentClimateName = ""
        	currentClimateId = ""        	
        }
        
        LOG("currentClimateName set = ${currentClimateName}  currentClimateId set = ${currentClimateId}")
        
        
        if (runningEvent) {
        	currentFanMode = circulateFanModeOn ? "circulate" : runningEvent.fan
        } else {
        	currentFanMode = stat.runtime.desiredFanMode
        }
     

		def data = [ 
			temperatureScale: getTemperatureScale(),
			apiConnected: apiConnected(),
			coolMode: (stat.settings.coolStages > 0),
			heatMode: (stat.settings.heatStages > 0),
			autoMode: stat.settings.autoHeatCoolFeatureEnabled,
            currentProgramName: currentClimateName,
            currentProgramId: currentClimateId,
			auxHeatMode: (stat.settings.hasHeatPump) && (stat.settings.hasForcedAir || stat.settings.hasElectric || stat.settings.hasBoiler),
			temperature: usingMetric ? tempTemperature : tempTemperature.toInteger(),
			heatingSetpoint: usingMetric ? tempHeatingSetpoint : tempHeatingSetpoint.toInteger(),
			coolingSetpoint: usingMetric ? tempCoolingSetpoint : tempCoolingSetpoint.toInteger(),
			thermostatMode: stat.settings.hvacMode,
            thermostatFanMode: currentFanMode,
			humidity: stat.runtime.actualHumidity,
            motion: (occupancy == "true") ? "active" : "inactive",
			thermostatOperatingState: getThermostatOperatingState(stat),
			weatherSymbol: stat.weather.forecasts[0].weatherSymbol.toString(),
			weatherTemperature: usingMetric ? tempWeatherTemperature : tempWeatherTemperature.toInteger()
		]
        
		data["temperature"] = data["temperature"] ? ( wantMetric() ? data["temperature"].toDouble() : data["temperature"].toDouble().toInteger() ) : data["temperature"]
		data["heatingSetpoint"] = data["heatingSetpoint"] ? ( wantMetric() ? data["heatingSetpoint"].toDouble() : data["heatingSetpoint"].toDouble().toInteger() ) : data["heatingSetpoint"]
		data["coolingSetpoint"] = data["coolingSetpoint"] ? ( wantMetric() ? data["coolingSetpoint"].toDouble() : data["coolingSetpoint"].toDouble().toInteger() ) : data["coolingSetpoint"]
        data["weatherTemperature"] = data["weatherTemperature"] ? ( wantMetric() ? data["weatherTemperature"].toDouble() : data["weatherTemperature"].toDouble().toInteger() ) : data["weatherTemperature"]
        
		
		LOG("Event Data = ${data}", 4)

		collector[dni] = [data:data]
		return collector
	}
				
}

def getThermostatOperatingState(stat) {

	def equipStatus = (stat.equipmentStatus.size() > 0) ? stat.equipmentStatus : 'Idle'	
	equipStatus = equipStatus.trim().toUpperCase()
    
    LOG("getThermostatOperatingState() - equipStatus == ${equipStatus}", 4)
    
	def currentOpState = equipStatus.contains('HEAT')? 'heating' : (equipStatus.contains('COOL')? 'cooling' : 
    	equipStatus.contains('FAN')? 'fan only': 'idle')
	return currentOpState
}


def getChildThermostatDeviceIdsString(singleStat = null) {
	if(!singleStat) {
    	LOG("getChildThermostatDeviceIdsString() - !singleStat returning the list for all thermostats", 4, null, "info")
		return thermostats.collect { it.split(/\./).last() }.join(',')
	} else {
    	// Only return the single thermostat
        def ecobeeDevId = singleStat.device.deviceNetworkID.split(/\./).last()
        LOG("Received a single thermostat, returning the Ecobee Device ID as a String: ${ecobeeDevId}", 4, null, "info")
        return ecobeeDevId    	
    }
}

/* 
def toJson(Map m) {
	return new org.json.JSONObject(m).toString()
}
*/ // Pending delete if not used anywhere

def toQueryString(Map m) {
	return m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&")
}

private refreshAuthToken() {

	if(!state.refreshToken) {
    	LOG("refreshing auth token", 2)	
       LOG("refreshAuthToken() - There is no refreshToken stored! Unable to refresh OAuth token.", 1, null, "error")
    	apiLost("refreshAuthToken() - No refreshToken")
        return
    } else if ( !readyForAuthRefresh() ) {
    	// Not ready to refresh yet
        LOG("refreshAuthToken() - Not time to refresh yet, there is still time left before expiration.")
    	return
    } else {

        def refreshParams = [
                method: 'POST',
                uri   : apiEndpoint,
                path  : "/token",
                query : [grant_type: 'refresh_token', code: "${state.refreshToken}", client_id: smartThingsClientId],
        ]

        LOG("refreshParams = ${refreshParams}")

        try {
            def jsonMap
            httpPost(refreshParams) { resp ->

                if(resp.status == 200) {
                    LOG("refreshAuthToken() - 200 Response received - Extracting info." )
                    
                    jsonMap = resp.data // Needed to work around strange bug that wasn't updating state when accessing resp.data directly
                    LOG("resp.data = ${resp.data} -- jsonMap is? ${jsonMap}")

                    if(jsonMap) {
                        LOG("resp.data == ${resp.data}, jsonMap == ${jsonMap}")
						
                        state.refreshToken = jsonMap.refresh_token
                        
                        // TODO - Platform BUG: This was not updating the state values for some reason if we use resp.data directly??? 
                        // 		  Workaround using jsonMap for authToken                       
                        LOG("state.authToken before: ${state.authToken}")
                        def oldAuthToken = state.authToken
                        state.authToken = jsonMap?.access_token  
						LOG("state.authToken after: ${state.authToken}")
                        if (oldAuthToken == state.authToken) { 
                        	LOG("WARN: state.authToken did NOT update properly! This is likely a transient problem.", 1, null, "warn")
                            state.connected = "warn"
							generateEventLocalParams() // Update the connected state at the thermostat devices
						}

                        
                        // Save the expiry time to optimize the refresh
                        LOG("Expires in ${resp?.data?.expires_in} seconds")
                        state.authTokenExpires = (resp?.data?.expires_in * 1000) + now()

						LOG("Refresh Token = state =${state.refreshToken}  == in: ${resp?.data?.refresh_token}")
                        LOG("OAUTH Token = state ${state.authToken} == in: ${resp?.data?.access_token}")
                        

                        if(state.action && state.action != "") {
                            LOG("Token refreshed. Executing next action: ${state.action}")

                            "${state.action}"()

                            // Reset saved action
                            state.action = ""
                        }

                    } else {
                    	LOG("No jsonMap??? ${jsonMap}", 2)
                    }
                    state.action = ""
                    state.connected = "full"
                    generateEventLocalParams() // Update the connected state at the thermostat devices
                    
                } else {
                    LOG("Refresh failed ${resp.status} : ${resp.status.code}!", 1, null, "error")
					state.connected = "warn"
                    generateEventLocalParams() // Update the connected state at the thermostat devices
                }
            }
        } catch (groovyx.net.http.HttpResponseException e) {
        	LOG("refreshAuthToken() >> HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}  response? data: ${e.getResponse()?.getData()}", 1, null, "error")
           	def reAttemptPeriod = 300 // in sec
			if ( (e.statusCode == 500 && e.getResponse()?.data.status.code == 14) || (e.statusCode == 401 && e.getResponse()?.data.status.code == 14) ) {
            	LOG("refreshAuthToken() - Received data.status.code = 14", 1, null, "error")
            	apiLost("refreshAuthToken() - Received data.status.code = 14" )
            } else if (e.statusCode != 401) { //this issue might comes from exceed 20sec app execution, connectivity issue etc.
            	LOG("refreshAuthToken() - e.statusCode: ${e.statusCode}", 1, null, "warn")
            	state.connected = "warn"
            	generateEventLocalParams() // Update the connected state at the thermostat devices
				runIn(reAttemptPeriod, "refreshAuthToken")
			} else if (e.statusCode == 401) { // status.code other than 14
				state.reAttempt = state.reAttempt + 1
				LOG("reAttempt refreshAuthToken to try = ${state.reAttempt}", 1, null, "warn")
				if (state.reAttempt <= 3) {
                	state.connected = "warn"
            		generateEventLocalParams() // Update the connected state at the thermostat devices
					runIn(reAttemptPeriod, "refreshAuthToken")
				} else {
                	// More than 3 attempts, time to give up and notify the end user
                    LOG("More than 3 attempts to refresh tokens. Giving up", 1, null, "error")
                    debugEvent("More than 3 attempts to refresh tokens. Giving up")
                	apiLost("refreshAuthToken() - More than 3 attempts to refresh token. Have to give up")
				}
            }
        } catch (java.util.concurrent.TimeoutException e) {
			LOG("refreshAuthToken(), TimeoutException: ${e}.", 1, null, "error")
			// Likely bad luck and network overload, move on and let it try again
            runIn(300, "refreshAuthToken")
        } catch (Exception e) {
        	LOG("refreshAuthToken(), General Exception: ${e}.", 1, null, "error")
        }
    }
}



def resumeProgram(child, deviceId) {
	LOG("Entered resumeProgram for deviceID: ${deviceID}", 5, child)

	def jsonRequestBody = buildBodyRequest('resumeProgram',null,deviceId,null,null).toString()
	LOG("jsonRequestBody = ${jsonRequestBody}", 4, child)
    
	def result = sendJson(jsonRequestBody)
    LOG("resumeProgram(child) with result ${result}", 3, child)    

    return result
}

def setHold(child, heating, cooling, deviceId, sendHoldType=null, fanMode="", extraParams=[]) {
	int h = (getTemperatureScale() == "C") ? (cToF(heating) * 10) : (heating * 10)
	int c = (getTemperatureScale() == "C") ? (cToF(cooling) * 10) : (cooling * 10)
    
	LOG("setHold(): setpoints____ - h: ${heating} - ${h}, c: ${cooling} - ${c}, setHoldType: ${sendHoldType}", 3, child)
    
    
	def tstatSettings = ((sendHoldType != null) && (sendHoldType != "")) ?
		[coolHoldTemp:"${c}", heatHoldTemp: "${h}", holdType:"${sendHoldType}"
		] :
		[coolHoldTemp:"${c}", heatHoldTemp: "${h}"
		]		

	if (fanMode != "") { 
		tstatSettings << [fan:"${fanMode}"] 
	}
        
    if (extraParams != []) {
    	tstatSettings << extraParams
    }                
    
	//def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '","includeRuntime":true},"thermostat": {"settings":{"hvacMode":"'+"${mode}"+'"}}}'
	def jsonRequestBody = buildBodyRequest('setHold',null,deviceId,tstatSettings,null).toString()
	//LOG("Mode Request Body = ${jsonRequestBody}", 4, child)    
	
	//def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '","includeRuntime":true},"functions": [{ "type": "setHold", "params": { "coolHoldTemp": '+c+',"heatHoldTemp": '+h+', "holdType": '+sendHoldType+' } } ]}'
    LOG("about to sendJson with jsonRequestBody (${jsonRequestBody}", 4, child)

    
	def result = sendJson(child, jsonRequestBody)
    LOG("setHold: heating: ${h}, cooling: ${c} with result ${result}", 3, child)
    return result
}

def setMode(child, mode, deviceId) {
	LOG("setMode() to ${mode} with DeviceId: ${deviceId}", 5, child)
    
	def tstatSettings 
    tstatSettings = [hvacMode:"${mode}"]
        
	//def jsonRequestBody = '{"selection":{"selectionType":"thermostats","selectionMatch":"' + deviceId + '","includeRuntime":true},"thermostat": {"settings":{"hvacMode":"'+"${mode}"+'"}}}'
	def jsonRequestBody = buildBodyRequest('setThermostatSettings',null,deviceId,null,tstatSettings).toString()
	LOG("Mode Request Body = ${jsonRequestBody}", 4, child)
    

	def result = sendJson(jsonRequestBody)
    LOG("setMode to ${mode} with result ${result}", 4, child)
	if (result) {
    	child.generateQuickEvent("thermostatMode", mode, 15)
    } else {
    	LOG("Unable to set new mode (${mode})", 1, child, "warn")
    }

	return result
}

def setFanMode(child, fanMode, deviceId, sendHoldType=null) {
	LOG("setFanMode() to ${fanMode} with DeviceID: ${deviceId}", 5, child)
    
    def extraParams = [isTemperatureRelative: "false", isTemperatureAbsolute: "false"]
    // TODO: Set the fan mode to circulate in the events data sent to the device
    if (fanMode == "circulate") {    	
    	fanMode = "auto"        
        LOG("fanMode == 'circulate'", 5, child, "trace")        
        // Add a minimum circulate time here
        // NOTE: This is not currently honored by the Ecobee
        extraParams << [fanMinOnTime:15]
		child.circulateFanModeOn = true
    } else if (fanMode == "off") {
    	child.circulateFanModeOn = false    
        fanMode = "auto"
        // NOTE: This is not currently honored by the Ecobee
        extraParams << [fanMinOnTime: "0"]
    } else {
		child.circulateFanModeOn = false    
    }
    
    // TODO Check to see if there is an existing event and use that to overwrite?
    def currentHeatingSetpoint = child.device.currentValue("heatingSetpoint")
    def currentCoolingSetpoint = child.device.currentValue("coolingSetpoint")
    def holdType = sendHoldType ?: whatHoldType()
    
    LOG("about to call setHold: ${currentHeatingSetpoint}, ${currentCoolingSetpoint}, ${deviceId}, ${holdType}, ${fanMode}, ${extraParams}", 5, child, "trace")
    return setHold(child, currentHeatingSetpoint, currentCoolingSetpoint, deviceId, holdType, fanMode, extraParams)
    
}

def setProgram(child, program, deviceId, sendHoldType=null) {
	LOG("setProgram() to ${program} with DeviceID: ${deviceId}", 5, child)

	def tstatSettings 
    tstatSettings = ((sendHoldType != null) && (sendHoldType != "")) ?
				[holdClimateRef:"${program}", holdType:"${sendHoldType}"
				] :
				[holdClimateRef:"${program}"
				]
    
	def jsonRequestBody = buildBodyRequest('setHold',null,deviceId,tstatSettings,null).toString()
    LOG("about to sendJson with jsonRequestBody (${jsonRequestBody}", 4, child)    
	def result = sendJson(child, jsonRequestBody)	
    LOG("setProgram with result ${result}", 3, child)
    dirtyPollData()
    return result
}


// API Helper Functions
private def sendJson(child = null, String jsonBody) {
	// Reset the poll timer to allow for an immediate refresh
	dirtyPollData()
    
	def returnStatus = false
	def cmdParams = [
			uri: apiEndpoint,
			path: "/1/thermostat",
			headers: ["Content-Type": "application/json", "Authorization": "Bearer ${state.authToken}"],
			body: jsonBody
	]

	try{
    	def statusCode = true
        int j=0
        
        while ( (statusCode) && (j++ < 2) ) { // only retry once
			httpPost(cmdParams) { resp ->
    	    	statusCode = resp.data.status.code
	
				LOG("sendJson() resp.status ${resp.status}, resp.data: ${resp.data}, statusCode: ${statusCode}", 2, child)
				
            	// TODO: Perhaps add at least two tries incase the first one fails?
				if(resp.status == 200) {				
					LOG("Updated ${resp.data}", 4)
					returnStatus = resp.data.status.code
					if (resp.data.status.code == 0) {
						LOG("Successful call to ecobee API.", 2, child)
						state.connected = "full"
	                    generateEventLocalParams()
    	                statusCode=false
					} else {
						LOG("Error return code = ${resp.data.status.code}", 1, child, "error")
					}
				} else {
    	        	LOG("Sent Json & got http status ${resp.status} - ${resp.status.code}", 2, child, "warn")
	
					//refresh the auth token
					if (resp.status == 500 && resp.status.code == 14) {					
						LOG("Refreshing your auth_token!")					
						refreshAuthToken()
						return false // No way to recover from a status.code 14
					} else {
    	            	LOG("Possible Authentication error, invalid authentication method, lack of credentials, etc. Status: ${resp.status} - ${resp.status.code} ", 2, child, "error")
        	            state.connected = "warn"
            	        generateEventLocalParams()
                	    if (j == 2) { // Go ahead and refresh on the second pass through
                    		refreshAuthToken() 
	                        return false
    	                }						
					}
				} // resp.status if/else
			} // HttpPost
        } // While loop
	} catch (groovyx.net.http.HttpResponseException e) {
    	LOG("sendJson() >> HttpResponseException occured. Exception info: ${e} StatusCode: ${e.statusCode}  response? data: ${e.getResponse()?.getData()}", 1, child, "error")	
		state.connected = "warn"
        generateEventLocalParams()
        refreshAuthToken()
		return false
    } catch(Exception e) {
    	// Might need to further break down 
		LOG("sendJson() - Exception Sending Json: " + e, 1, child, "error")
        state.connected = "warn"
        generateEventLocalParams()
        if (j == 2) { // Go ahead and refresh on the second pass through
        	refreshAuthToken()
			return false
		}
	}

	if (returnStatus == 0)
		return true
	else
		return false
}

private def getChildThermostatName() { return "Ecobee Thermostat" }
private def getChildSensorName()     { return "Ecobee Sensor" }
private def getServerUrl()           { return "https://graph.api.smartthings.com" }
private def getShardUrl()            { return getApiServerUrl() }
private def getCallbackUrl()         { return "${serverUrl}/oauth/callback" }
private def getBuildRedirectUrl()    { return "${serverUrl}/oauth/initialize?appId=${app.id}&access_token=${state.accessToken}&apiServerUrl=${shardUrl}" }
private def getApiEndpoint()         { return "https://api.ecobee.com" }

// This is the API Key from the Ecobee developer page. Can be provided by the app provider or use the appSettings
private def getSmartThingsClientId() { 
	if(!appSettings.clientId) {
		return "obvlTjUuuR2zKpHR6nZMxHWugoi5eVtS"		
	} else {
		return appSettings.clientId 
    }
}



private def LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	def prefix = ""
	if ( settings.debugLevel?.toInteger() == 5 ) { prefix = "LOG: " }
	if ( debugLevel(level) ) { 
    	log."${logType}" "${prefix}${message}"
        // log.debug message
        if (event) { debugEvent(message, displayEvent) }
        if (event && child) { debugEventFromParent(child, message) }
	}    
}


private def debugEvent(message, displayEvent = false) {
	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	if ( debugLevel(4) ) { log.debug "Generating AppDebug Event: ${results}" }
	sendEvent (results)
}

private def debugEventFromParent(child, message) {

	 def data = [
            	debugEventFromParent: message
            ]         
	if (child) { child.generateEvent(data) }
}

//send both push notification and mobile activity feeds
private def sendPushAndFeeds(notificationMessage) {
	LOG("sendPushAndFeeds >> notificationMessage: ${notificationMessage}", 1, null, "warn")
	LOG("sendPushAndFeeds >> state.timeSendPush: ${state.timeSendPush}", 1, null, "warn")
    
    if (state.timeSendPush) {
        if ( (now() - state.timeSendPush) >= (1000 * 60 * 60 * 1)){ // notification is sent to remind user no more than once per hour
            sendPush("Your Ecobee thermostat " + notificationMessage)
            sendActivityFeeds(notificationMessage)
            state.timeSendPush = now()
        }
    } else {
        sendPush("Your Ecobee thermostat " + notificationMessage)
        sendActivityFeeds(notificationMessage)
        state.timeSendPush = now()
    }
    // state.authToken = null
}

private def sendActivityFeeds(notificationMessage) {
    def devices = getChildDevices()
    devices.each { child ->
        child.generateActivityFeedsEvent(notificationMessage) //parse received message from parent
    }
}













// Helper Functions
// Creating my own as it seems that the built-in version only works for a device, NOT a SmartApp
def myConvertTemperatureIfNeeded(scaledSensorValue, cmdScale, precision) {
	if ( (cmdScale != "C") && (cmdScale != "F") && (cmdScale != "dC") && (cmdScale != "dF") ) {
    	// We do not have a valid Scale input, throw a debug error into the logs and just return the passed in value
        LOG("Invalid temp scale used: ${cmdScale}", 2, null, "error")
        return scaledSensorValue
    }

	def returnSensorValue 
    
	// Normalize the input
	if (cmdScale == "dF") { cmdScale = "F" }
    if (cmdScale == "dC") { cmdScale = "C" }

	LOG("About to convert/scale temp: ${scaledSensorValue}", 5, null, "trace", false)
	if (cmdScale == getTemperatureScale() ) {
    	// The platform scale is the same as the current value scale
        returnSensorValue = scaledSensorValue.round(precision)
    } else if (cmdScale == "F") {		    	
    	returnSensorValue = fToC(scaledSensorValue).round(precision)
    } else {
    	returnSensorValue = cToF(scaledSensorValue).round(precision)
    }
    LOG("returnSensorValue == ${returnSensorValue}", 5, null, "trace", false)
    return returnSensorValue
}

def wantMetric() {
	return (getTemperatureScale() == "C")
}

private def cToF(temp) {
	LOG("cToF entered with ${temp}", 5, null, "info")
	return (temp * 1.8 + 32) as Double
    // return celsiusToFahrenheit(temp)
}
private def fToC(temp) {	
	LOG("fToC entered with ${temp}", 5, null, "info")
	return (temp - 32) / 1.8 as Double
    // return fahrenheitToCelsius(temp)
}
private def milesToKm(distance) {
	return (distance * 1.609344) 
}

// Establish the minimum amount of time to wait to do another poll
private def  getMinMinBtwPolls() {
    // TODO: Make this configurable in the SmartApp
	return 1
}


// Are we connected with the Ecobee service?
private String apiConnected() {
	// values can be "full", "warn", "lost"
	if (state.connected == null) state.connected = "lost"
	return state.connected?.toString() ?: "lost"
}

private def apiLost(where = "not specified") {
    LOG("apiLost() - ${where}: Lost connection with APIs. unscheduling Polling and refreshAuthToken. User MUST reintialize the connection with Ecobee by running the SmartApp and logging in again", 1, null, "error", true, true)
    
    // provide cleanup steps when API Connection is lost
	def notificationMessage = "is disconnected from SmartThings/Ecobee, because the access credential changed or was lost. Please go to the Ecobee (Connect) SmartApp and re-enter your account login credentials."
    state.connected = "lost"
    state.authToken = null
    
    sendPushAndFeeds(notificationMessage)
	generateEventLocalParams()

    LOG("Unscheduling Polling and refreshAuthToken. User MUST reintialize the connection with Ecobee by running the SmartApp and logging in again", 0, null, "error", true, true)
    
    // Notify each child that we lost so it gets logged
    if ( debugLevel(3) ) {
    	def d = getChildDevices()
    	d?.each { oneChild ->
        	LOG("apiLost() - notifying each child: ${oneChild} of loss", 0, child, "error", true, true)
		}
    }
    
    unschedule("poll")
    unschedule("refreshAuthToken")
    runEvery3Hours("notifyApiLost")
}

def notifyApiLost() {
	def notificationMessage = "is disconnected from SmartThings/Ecobee, because the access credential changed or was lost. Please go to the Ecobee (Connect) SmartApp and re-enter your account login credentials."
    if ( state.connected == "lost" ) {
    	generateEventLocalParams()
		sendPushAndFeeds(notificationMessage)
        LOG("notifyApiLost() - API Connection Previously Lost. User MUST reintialize the connection with Ecobee by running the SmartApp and logging in again", 0, null, "error", true, true)
	} else {
    	// Must have restored connection
        unschedule("notifyApiLost")
    }    
}

private String childType(child) {
	// Determine child type (Thermostat or Remote Sensor)
    if ( child.hasCapability("Thermostat") ) { return getChildThermostatName() }
    if ( child.name.contains( getChildSensorName() ) ) { return getChildSensorName() }
    return "Unknown"
    
}

private Boolean readyForAuthRefresh() {
	LOG("Entered readyForAuthRefresh() ", 5)
    def timeLeft 
    
    timeLeft = state.authTokenExpires ? ((state.authTokenExpires - now()) / 1000 / 60) : 0
    LOG("timeLeft until expiry (in min): ${timeLeft}", 3)

    
    // Since this runs as part of poll() we can be a bit more conservative on the time before renewing the token
    // def pollInterval = settings.pollingInterval ?: 5
    // def ready = timeLeft <= ((pollInterval * 3) + 2)
    def ready = timeLeft <= 29    
    LOG("Ready for authRefresh? ${ready}", 4)
    return ready
}

private def whatHoldType() {
	def sendHoldType = settings.holdType ? (settings.holdType=="Temporary" || settings.holdType=="Until Next Program")? "nextTransition" : (settings.holdType=="Permanent" || settings.holdType=="Until I Change")? "indefinite" : "indefinite" : "indefinite"
	LOG("Entered whatHoldType() with ${sendHoldType}  settings.holdType == ${settings.holdType}")
	 
    return sendHoldType
}

private debugLevel(level=3) {
	def debugLvlNum = settings.debugLevel?.toInteger() ?: 3
    def wantedLvl = level?.toInteger()
    
    return ( debugLvlNum >= wantedLvl )
}


// Mark the poll data as "dirty" to allow a new API call to take place
private def dirtyPollData() {
	LOG("dirtyPollData() called to reset poll state", 5)
	state.lastPoll = 0
}


// Ecobee API Related Functions - from Yves code


// tstatType =managementSet or registered (no spaces).  
//		registered is for Smart, Smart-Si & Ecobee thermostats, 
//		managementSet is for EMS thermostat
//		may also be set to a specific locationSet (ex. /Toronto/Campus/BuildingA)
//		may be set to null if not relevant for the given method
// thermostatId may be a list of serial# separated by ",", no spaces (ex. '123456789012,123456789013') 
private def buildBodyRequest(method, tstatType="registered", thermostatId, tstatParams = [],
	tstatSettings = [], tstatEvents = []) {
   LOG("Entered buildBodyRequest()", 5)
    
	def selectionJson = null
	def selection = null  
	if (method == 'thermostatSummary') {
		if (tstatType.trim().toUpperCase() == 'REGISTERED') {
			selection = [selection: [selectionType: 'registered', selectionMatch: '',
							includeEquipmentStatus: 'true']
						]
		} else {
			// If tstatType is different than managementSet, it is assumed to be locationSet specific (ex./Toronto/Campus/BuildingA)
			selection = (tstatType.trim().toUpperCase() == 'MANAGEMENTSET') ? 
				// get all EMS thermostats from the root
				[selection: [selectionType: 'managementSet', selectionMatch: '/',
					includeEquipmentStatus: 'true']
				] : // Or Specific to a location
				[selection: [selectionType: 'managementSet', selectionMatch: tstatType.trim(),
					includeEquipmentStatus: 'true']
				]
		}
		selectionJson = new groovy.json.JsonBuilder(selection)
		return selectionJson
	} else if (method == 'thermostatInfo') {
		selection = [selection: [selectionType: 'thermostats',
			selectionMatch: thermostatId,
			includeSettings: 'true',
			includeRuntime: 'true',
			includeProgram: 'true',           
			includeWeather: 'true',            
			includeAlerts: 'true',
			includeEvents: 'true',
			includeEquipmentStatus: 'true',
			includeSensors: 'true'
			]
		]
		selectionJson = new groovy.json.JsonBuilder(selection)
		return selectionJson
	} else {
		selection = [selectionType: 'thermostats', selectionMatch: thermostatId]
	}
	selectionJson = new groovy.json.JsonBuilder(selection)
    
	if ((method != 'setThermostatSettings') && (tstatSettings != null) && (tstatSettings != [])) {
		def function_clause = ((tstatParams != null) && (tsatParams != [])) ? 
			[type:method, params: tstatParams] : 
			[type: method]
		def bodyWithSettings = [functions: [function_clause], selection: selection,
				thermostat: [settings: tstatSettings]
			]
		def bodyWithSettingsJson = new groovy.json.JsonBuilder(bodyWithSettings)
		return bodyWithSettingsJson
	} else if (method == 'setThermostatSettings') {
		def bodyWithSettings = [selection: selection,thermostat: [settings: tstatSettings]
			]
		def bodyWithSettingsJson = new groovy.json.JsonBuilder(bodyWithSettings)
		return bodyWithSettingsJson
	} else if ((tstatParams != null) && (tsatParams != [])) {
		def function_clause = [type: method, params: tstatParams]
		def simpleBody = [functions: [function_clause], selection: selection]
		def simpleBodyJson = new groovy.json.JsonBuilder(simpleBody)
		return simpleBodyJson
	} else {
		def function_clause = [type: method]
		def simpleBody = [functions: [function_clause], selection: selection]
		def simpleBodyJson = new groovy.json.JsonBuilder(simpleBody)
		return simpleBodyJson
    }    
}


// iterateSetThermostatSettings: iterate thru all the thermostats under a specific account and set the desired settings
// tstatType =managementSet or registered (no spaces).  May also be set to a specific locationSet (ex./Toronto/Campus/BuildingA)
// settings can be anything supported by ecobee 
//		at https://www.ecobee.com/home/developer/api/documentation/v1/objects/Settings.shtml
/*
void iterateSetThermostatSettings(tstatType, tstatSettings = []) {
	Integer MAX_TSTAT_BATCH = get_MAX_TSTAT_BATCH()
	def tstatlist = null
	Integer nTstats = 0

	def ecobeeType = determine_ecobee_type_or_location(tstatType)
	getThermostatSummary(ecobeeType)
	if (settings.trace) {
		log.debug
			"iterateSetThermostatSettings>ecobeeType=${ecobeeType},about to loop ${data.thermostatCount} thermostat(s)"
		sendEvent name: "verboseTrace", value:
			"iterateSetThermostatSettings>ecobeeType=${ecobeeType},about to loop ${data.thermostatCount} thermostat(s)"
	}
	for (i in 0..data.thermostatCount - 1) {
		def thermostatDetails = data.revisionList[i].split(':')
		def Id = thermostatDetails[0]
		def thermostatName = thermostatDetails[1]
		def connected = thermostatDetails[2]
		if (connected == 'true') {
			if (nTstats == 0) {
				tstatlist = Id
				nTstats = 1
			}
			if ((nTstats > MAX_TSTAT_BATCH) || (i == (data.thermostatCount - 1))) { 
				// process a batch of maximum 25 thermostats according to API doc
				if (settings.trace) {
					sendEvent name: "verboseTrace", value:
						"iterateSetThermostatSettings>about to call setThermostatSettings for ${tstatlist}"
					log.debug "iterateSetThermostatSettings> about to call setThermostatSettings for ${tstatlist}"
				}
				setThermostatSettings("${tstatlist}",tstatSettings)
				tstatlist = Id
				nTstats = 1
			} else {
				tstatlist = tstatlist + "," + Id
				nTstats++ 
			}
		}
	}
}

*/

/*
// thermostatId may be a list of serial# separated by ",", no spaces (ex. '123456789012,123456789013') 
//	if no thermostatId is provided, it is defaulted to the current thermostatId 
// settings can be anything supported by ecobee at https://www.ecobee.com/home/developer/api/documentation/v1/objects/Settings.shtml
void setThermostatSettings(thermostatId,tstatSettings = []) {
   	thermostatId= determine_tstat_id(thermostatId) 	    
	if ( debugLevel(5) ) {
		log.debug "setThermostatSettings>called with values ${tstatSettings} for ${thermostatId}"		
	}
    
	def bodyReq = buildBodyRequest('setThermostatSettings',null,thermostatId,null,tstatSettings)
	def statusCode=true
	int j=0        
	while ((statusCode) && (j++ <2)) { // retries once if api call fails
		apiHelper('setThermostatSettings', bodyReq) {resp ->
			statusCode = resp.data.status.code
			def message = resp.data.status.message
			if (!statusCode) {
				if ( debugLevel(3) ) { log.debug "setThermostatSettings() successful for ${thermostatId} with settings ${tstatSettings}" }            	

			} else {
            	if ( debugLevel(1) ) {
					log.error "setThermostatSettings() error=${statusCode.toString()},message=${message} for ${thermostatId}"
					debugEvent( "setThermostatSettings() error=${statusCode.toString()},message=${message} for ${thermostatId}" )
				} 

				// introduce a 1 second delay before re-attempting any other command                    
				def cmd= []           
				cmd << "delay 1000"                    
				cmd            
		} // end if statusCode 
		} // end api call              
	} // end for 
}

*/