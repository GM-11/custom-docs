package com.doceditor.docmanager.services;

import com.doceditor.docmanager.repository.DocumentsRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DocumentService {

    @Autowired
    private DocumentsRepository documentsRepository;

}
