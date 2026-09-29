package vn.edu.parking.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "family_members", uniqueConstraints = @UniqueConstraint(columnNames = "citizen_id"))
public class FamilyMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Household household;
    @Column(nullable = false)
    private String fullName;
    @Column(name = "citizen_id", length = 20)
    private String citizenId;
    @Column(length = 20)
    private String oldCitizenId;
    @Column(length = 1000)
    private String cccdQrRaw;
    private LocalDate dateOfBirth;
    private String gender;
    private String relationshipToHead;
    private String phone;
    private String email;
    @Column(length = 500)
    private String permanentAddress;
    private LocalDate cccdIssueDate;
    private String cccdIssuePlace;
    @Column(length = 500)
    private String cccdImagePath;
    @Column(length = 500)
    private String registrationFaceImagePath;
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Household getHousehold() { return household; }
    public void setHousehold(Household household) { this.household = household; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getCitizenId() { return citizenId; }
    public void setCitizenId(String citizenId) { this.citizenId = citizenId; }
    public String getOldCitizenId() { return oldCitizenId; }
    public void setOldCitizenId(String oldCitizenId) { this.oldCitizenId = oldCitizenId; }
    public String getCccdQrRaw() { return cccdQrRaw; }
    public void setCccdQrRaw(String cccdQrRaw) { this.cccdQrRaw = cccdQrRaw; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getRelationshipToHead() { return relationshipToHead; }
    public void setRelationshipToHead(String relationshipToHead) { this.relationshipToHead = relationshipToHead; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPermanentAddress() { return permanentAddress; }
    public void setPermanentAddress(String permanentAddress) { this.permanentAddress = permanentAddress; }
    public LocalDate getCccdIssueDate() { return cccdIssueDate; }
    public void setCccdIssueDate(LocalDate cccdIssueDate) { this.cccdIssueDate = cccdIssueDate; }
    public String getCccdIssuePlace() { return cccdIssuePlace; }
    public void setCccdIssuePlace(String cccdIssuePlace) { this.cccdIssuePlace = cccdIssuePlace; }
    public String getCccdImagePath() { return cccdImagePath; }
    public void setCccdImagePath(String cccdImagePath) { this.cccdImagePath = cccdImagePath; }
    public String getRegistrationFaceImagePath() { return registrationFaceImagePath; }
    public void setRegistrationFaceImagePath(String registrationFaceImagePath) { this.registrationFaceImagePath = registrationFaceImagePath; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
