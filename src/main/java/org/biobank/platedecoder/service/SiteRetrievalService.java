package org.biobank.platedecoder.service;

import java.util.List;

import org.asynchttpclient.Response;
import org.biobank.platedecoder.model.Site;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.jr.ob.JSON;
import static org.asynchttpclient.Dsl.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

public class SiteRetrievalService extends Service<ObservableList<Site>> {

    //@SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SiteRetrievalService.class);

    private static final String REST_API = "openspecimen/rest/ng/sites?maxResults=";

    private final String hostname;

    private final int maxResults;

    public SiteRetrievalService(String host, int maxResults) {
        this.hostname = host;
        this.maxResults = maxResults;
    }

    public SiteRetrievalService(String host) {
        this(host, 1000);
    }

    private String getUrl() {
        StringBuffer buf = new StringBuffer();
        buf.append(hostname);
        buf.append(REST_API);
        buf.append(maxResults);
        return buf.toString();
    }

    @Override
    protected Task<ObservableList<Site>> createTask()  {
        return new Task<ObservableList<Site>>() {

            @Override
            protected ObservableList<Site> call() throws Exception {
                ObservableList<Site> result = FXCollections.observableArrayList();
                String url = getUrl();
                HttpClient client = new HttpClient();
                Response response = client.prepareGet(url).execute().get();
                int statusCode = response.getStatusCode();
                LOG.debug("statusCode: {}", statusCode);

                if (statusCode == 200) {
                    String body = response.getResponseBody();
                    try {
                        List<Site> sites = JSON.std.listOfFrom(Site.class, body);
                        result.addAll(sites);
                    } catch (Exception e) {
                        LOG.error(e.getMessage());
                    }
                }
                client.close();
                return result;
            }
        };
    }

}
