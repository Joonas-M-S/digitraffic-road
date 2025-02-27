package fi.livi.digitraffic.tie.model.v1.datex2;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import com.vladmihalcea.hibernate.type.json.JsonType;

import fi.livi.digitraffic.tie.model.ReadOnlyCreatedAndModifiedFields;

@TypeDef(name = "json", typeClass = JsonType.class)
@Entity
@DynamicUpdate
@Table(name = "DATEX2")
public class Datex2 extends ReadOnlyCreatedAndModifiedFields {

    @Id
    @GenericGenerator(name = "SEQ_DATEX2", strategy = "org.hibernate.id.enhanced.SequenceStyleGenerator",
                      parameters = @Parameter(name = "sequence_name", value = "SEQ_DATEX2"))
    @GeneratedValue(generator = "SEQ_DATEX2")
    private Long id;

    @Column(name = "IMPORT_DATE")
    @NotNull
    private ZonedDateTime importTime;

    @NotNull
    private String message;

    @Deprecated
    @NotNull
    @Enumerated(EnumType.STRING)
    private Datex2MessageType messageType;

    @NotNull
    @Enumerated(EnumType.STRING)
    private SituationType situationType;

    // Only for SituationType.TRAFFIC_ANNOUNCEMENT
    @Enumerated(EnumType.STRING)
    private TrafficAnnouncementType trafficAnnouncementType;

    private ZonedDateTime publicationTime;

    @OneToMany(mappedBy = "datex2", cascade = CascadeType.ALL)
    private List<Datex2Situation> situations;

    @Type(type = "json")
    @Column(columnDefinition = "json")
    private String jsonMessage;

    /** If geometry is not valid the original jsonMessage will be saved here and fixed version to jsonMessage-field */
    @Type(type = "json")
    @Column(columnDefinition = "json")
    private String originalJsonMessage;

    public Datex2() {
        // For JPA
    }

    public Datex2(final SituationType situationType, final TrafficAnnouncementType trafficAnnouncementType) {
        this.situationType = situationType;
        this.trafficAnnouncementType = trafficAnnouncementType;
        this.messageType = situationType.getDatex2MessageType();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }

    public ZonedDateTime getImportTime() {
        return importTime;
    }

    public void setImportTime(final ZonedDateTime importTime) {
        this.importTime = importTime;
    }

    public ZonedDateTime getPublicationTime() {
        return publicationTime;
    }


    public void setPublicationTime(final ZonedDateTime publicationTime) {
        this.publicationTime = publicationTime;
    }

    public List<Datex2Situation> getSituations() {
        return situations;
    }

    public void setSituations(final List<Datex2Situation> situations) {
        this.situations = situations;
    }

    public void addSituation(final Datex2Situation situation) {
        if (this.situations == null) {
            this.situations = new ArrayList<>();
        }
        situations.add(situation);
        situation.setDatex2(this);
    }

    @Deprecated
    public Datex2MessageType getMessageType() {
        return messageType;
    }

    public void setJsonMessage(final String jsonMessage) {
        this.jsonMessage = jsonMessage;
    }

    public String getJsonMessage() {
        return jsonMessage;
    }

    public SituationType getSituationType() {
        return situationType;
    }

    public TrafficAnnouncementType getTrafficAnnouncementType() {
        return trafficAnnouncementType;
    }

    public void setTrafficAnnouncementType(TrafficAnnouncementType trafficAnnouncementType) {
        this.trafficAnnouncementType = trafficAnnouncementType;
    }

    public void setOriginalJsonMessage(final String originalJsonMessage) {
        this.originalJsonMessage = originalJsonMessage;
    }
}
