package ca.oshadami.servicedesk.extract;

/**
 * Request categories and the work queue each one is routed to.
 */
public enum RequestType {
    DRIVER_LICENCE("motor-vehicle-licensing"),
    VEHICLE_REGISTRATION("motor-vehicle-registration"),
    ADDRESS_CHANGE("client-records"),
    PHOTO_ID_CARD("client-records"),
    DRIVER_EXAM_BOOKING("driver-examinations"),
    COMPLAINT("client-relations"),
    OTHER("general-inquiries");

    private final String queue;

    RequestType(String queue) {
        this.queue = queue;
    }

    public String queue() {
        return queue;
    }
}
