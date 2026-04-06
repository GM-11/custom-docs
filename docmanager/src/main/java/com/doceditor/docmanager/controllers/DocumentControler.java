package com.doceditor.docmanager.controllers;

import java.util.List;

import com.doceditor.docmanager.controllers.dto.CheckAccessResponse;
import com.doceditor.docmanager.controllers.dto.GrantAccessRequest;
import com.doceditor.docmanager.controllers.dto.NewDocumentRequest;
import com.doceditor.docmanager.entity.Documents;
import com.doceditor.docmanager.services.DocumentService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentControler {

    @Autowired
    private DocumentService documentService;

    @PostMapping()
    public ResponseEntity<String> createDocument(@RequestBody NewDocumentRequest request) {
        try {
            String newDocId = documentService.createNewDocument(request.getUserId(), request.getTitle());
            return ResponseEntity.ok(newDocId);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error creating document: " + e.getMessage());
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<Documents>> getUserDocuments(@PathVariable String userId) {
        try {
            return ResponseEntity.ok(documentService.getUserDocuments(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @PutMapping("/access")
    public ResponseEntity<String> grantAccessToDocument(@RequestBody GrantAccessRequest request) {
        try {
            documentService.grantAccess(request.getDocumentId(), request.getUserEmail(), request.getOwnerId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/access")
    public ResponseEntity<CheckAccessResponse> checkDocumentAccess(@RequestParam String documentId,
            @RequestParam String userId) {
        try {
            Boolean accessInfo = documentService.checkDocumentAccess(documentId, userId);
            return ResponseEntity.ok(new CheckAccessResponse(accessInfo));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

}
