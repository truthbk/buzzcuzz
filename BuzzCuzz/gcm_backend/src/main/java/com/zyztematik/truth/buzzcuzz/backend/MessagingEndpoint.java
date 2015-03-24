/*
   For step-by-step instructions on connecting your Android application to this backend module,
   see "App Engine Backend with Google Cloud Messaging" template documentation at
   https://github.com/GoogleCloudPlatform/gradle-appengine-templates/tree/master/GcmEndpoints
*/

package com.zyztematik.truth.buzzcuzz.backend;

import com.google.android.gcm.server.Constants;
import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Named;

import static com.zyztematik.truth.buzzcuzz.backend.OfyService.ofy;

/**
 * An endpoint to send messages to devices registered with the backend
 * <p/>
 * For more information, see
 * https://developers.google.com/appengine/docs/java/endpoints/
 * <p/>
 * NOTE: This endpoint does not use any form of authorization or
 * authentication! If this app is deployed, anyone can access this endpoint! If
 * you'd like to add authentication, take a look at the documentation.
 */
@Api(name = "messaging", version = "v1", namespace = @ApiNamespace(ownerDomain = "backend.buzzcuzz.truth.zyztematik.com", ownerName = "backend.buzzcuzz.truth.zyztematik.com", packagePath = ""))
public class MessagingEndpoint {
    private static final Logger log = Logger.getLogger(MessagingEndpoint.class.getName());

    /**
     * Api Keys can be obtained from the google cloud console
     */
    private static final String API_KEY = System.getProperty("gcm.api.key");

    /**
     * Register a device to the backend
     *
     * @param email The email we wish to buzz.
     * @param message The message to send together with the buzz.
     */
    @ApiMethod(name = "buzz")
    public void buzzDevice(@Named("email") String email, @Named("message") String message) {
        log.info("Buzzing devices API called!");
        List<RegistrationRecord> records = getDevices(email);
        for (RegistrationRecord r : records) {
            try {
                log.warning("Sending message to : " + email + " msg: " + message);
                sendMessage(r.getRegId(), message);
            } catch (IOException ex) {
                log.warning("Error buzzing : " + email + " error: " + ex.getMessage());
            }
        }
    }

    /**
     * Send to the first 10 devices (You can modify this to send to any number of devices or a specific device)
     *
     * @param message The message to send
     */
    public void sendMessage(@Named("regId") String regId, @Named("message") String message) throws IOException {
        if (regId == null || regId.trim().length() == 0) {
            log.warning("Not sending message, missing registration ID.");
            return;
        }
        if (message == null || message.trim().length() == 0) {
            log.warning("Not sending message because it is empty");
            return;
        }
        // crop longer messages
        if (message.length() > 1000) {
            message = message.substring(0, 1000) + "[...]";
        }
        Sender sender = new Sender(API_KEY);
        Message msg = new Message.Builder().addData("message", message).build();

        Result result = sender.send(msg, regId, 5);
        if (result.getMessageId() != null) {
            log.info("Message sent to " + regId);
            String canonicalRegId = result.getCanonicalRegistrationId();
            if (canonicalRegId != null) {
                // if the regId changed, we have to update the datastore
                log.info("Registration Id changed for " + regId + " updating to " + canonicalRegId);
                RegistrationRecord record = ofy().load().type(RegistrationRecord.class).id(regId).now();
                record.setRegId(canonicalRegId);
                ofy().save().entity(record).now();
            }
        } else {
            String error = result.getErrorCodeName();
            if (error.equals(Constants.ERROR_NOT_REGISTERED)) {
                log.warning("Registration Id " + regId + " no longer registered with GCM, removing from datastore");
                // if the device is no longer registered with Gcm, remove it from the datastore
                RegistrationRecord record = ofy().load().type(RegistrationRecord.class).id(regId).now();
                ofy().delete().entity(record).now();
            } else {
                log.warning("Error when sending message : " + error);
            }
        }
    }

    public List<RegistrationRecord> getDevices(@Named("email") String email) {
        List<RegistrationRecord> records = ofy().load().type(RegistrationRecord.class).filter("email ==", email).list();
        return records;
    }
}
