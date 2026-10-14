package ca.oshadami.servicedesk.workflow;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import ca.oshadami.servicedesk.config.AppProperties;

/**
 * REST connector to the case management system of record. Points at the built-in mock by default; swap the base URL
 * to integrate a real system (ServiceNow, Dynamics, a departmental API) without changing the workflow code.
 */
@Component
public class CaseManagementClient {

    private final RestClient rest;

    public CaseManagementClient(RestClient.Builder builder, AppProperties props) {
        this.rest = builder.baseUrl(props.caseApi().baseUrl()).build();
    }

    public CaseRecord create(CaseRecord draft) {
        return rest.post().uri("/api/cases")
                .contentType(MediaType.APPLICATION_JSON)
                .body(draft)
                .retrieve()
                .body(CaseRecord.class);
    }
}
