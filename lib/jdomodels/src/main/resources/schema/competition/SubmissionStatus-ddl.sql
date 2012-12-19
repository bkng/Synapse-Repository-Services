CREATE TABLE JDOSUBMISSION_STATUS (
    ID bigint(20) NOT NULL,
	ETAG char(36) NOT NULL,
    MODIFIED_ON bigint(20) NOT NULL,
    STATUS int NOT NULL,
    SCORE bigint(20) DEFAULT NULL,
    PRIMARY KEY (ID),
    FOREIGN KEY (ID) REFERENCES JDOSUBMISSION (ID) ON DELETE CASCADE
);