package org.biobank.platedecoder.service;

import java.util.Arrays;
import java.util.List;

import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.Response;
import org.biobank.platedecoder.model.Participant;
import org.biobank.platedecoder.model.PmiDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.jr.ob.JSON;
import static org.asynchttpclient.Dsl.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;

/**
 *
 */
public class ParticipantRetrievalService extends Service<ObservableList<Participant>> {

    //@SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ParticipantRetrievalService.class);

    private static final String REST_API = "openspecimen/rest/ng/participants/match";

    private final String hostname;

    private final String siteName;

    private final String medicalRecordNum;

    public ParticipantRetrievalService(String host, String siteName, String medicalRecordNum) {
        this.hostname = host;
        this.siteName = siteName;
        this.medicalRecordNum = medicalRecordNum;
    }

    private String getUrl() {
        StringBuffer buf = new StringBuffer();
        buf.append(hostname);
        buf.append(REST_API);
        return buf.toString();
    }

    @Override
    protected Task<ObservableList<Participant>> createTask() {
        return new Task<ObservableList<Participant>>() {

            @Override
            protected ObservableList<Participant> call() throws Exception {
                ObservableList<Participant> result = FXCollections.observableArrayList();
                String url = getUrl();
                HttpClient client = new HttpClient();
                BoundRequestBuilder post = client.preparePost(url);
                post.addHeader("Content-type", "application/json");

                try {
                    PmiDetail pmi = new PmiDetail();
                    pmi.setSiteName(siteName);
                    pmi.setMrn(medicalRecordNum);
                    ParticipantQuery p = new ParticipantQuery();
                    p.setPmis(Arrays.asList(pmi));

                    String json = JSON.std.asString(p);
                    LOG.debug("json param: {}", json);

                    post.setBody(json);
                    Response response = post.execute().get();
                    int statusCode = response.getStatusCode();
                    LOG.debug("statusCode: {}", statusCode);

                    if (statusCode == 200) {
                        String body = response.getResponseBody();
                        LOG.debug("response: {}", body);
                        List<MatchedParticipant> match =
                            JSON.std.listOfFrom(MatchedParticipant.class, body);
                        result.addAll(match.get(0).getParticipant());
                    }
                } catch (Exception e) {
                    LOG.error(e.getMessage());
                }
                client.close();
                return result;
            }

        };
    }

    private static class ParticipantQuery {

        private List<PmiDetail> pmis;

        @SuppressWarnings("unused")
        public List<PmiDetail> getPmis() {
            return pmis;
        }

        public void setPmis(List<PmiDetail> pmis) {
            this.pmis = pmis;
        }
    }

    private static class MatchedParticipant {
        List<String> matchedAttrs;

        Participant participant;

        @SuppressWarnings("unused")
        public List<String> getMatchedAttrs() {
            return matchedAttrs;
        }

        @SuppressWarnings("unused")
        public void setMatchedAttrs(List<String> matchedAttrs) {
            this.matchedAttrs = matchedAttrs;
        }


        public Participant getParticipant() {
            return participant;
        }


        @SuppressWarnings("unused")
        public void setParticipant(Participant participant) {
            this.participant = participant;
        }

        @Override
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("matchedAttrs: ").append(matchedAttrs);
            buf.append(", participant: [ ").append(participant);
            buf.append(" ]");
            return buf.toString();
        }

    }
}
