package com.doceditor.docmanager.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotMessage {
    private String type;
    private String documentId;
    private String documentString;
    private Long lamportClock;
    private Long timestamp;

}
