package com.doceditor.docmanager.controllers.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GrantAccessRequest {

    private String documentId;
    private String userEmail;
    private String ownerId;

}
