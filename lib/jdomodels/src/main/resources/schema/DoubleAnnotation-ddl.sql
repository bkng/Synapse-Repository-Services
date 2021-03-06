CREATE TABLE `JDODOUBLEANNOTATION` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT,
  `ATTRIBUTE` varchar(256) CHARACTER SET latin1 COLLATE latin1_bin DEFAULT NULL,
  `OWNER_ID` bigint(20) NOT NULL,
  `VALUE` double DEFAULT NULL,
  PRIMARY KEY (`ID`),
  KEY `JDODOUBLEANNOTATION_N49` (`OWNER_ID`),
  CONSTRAINT `DOUBLE_ANNON_OWNER_FK` FOREIGN KEY (`OWNER_ID`) REFERENCES `JDONODE` (`ID`) ON DELETE CASCADE
)