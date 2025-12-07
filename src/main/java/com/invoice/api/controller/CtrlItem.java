package com.invoice.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoItemIn;
import com.invoice.api.service.SvcItem;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/item")
@Tag(name = "Item", description = "Administración de artículos (gtin, stock, precio)")
public class CtrlItem {

    private final SvcItem svcItem;

    public CtrlItem(SvcItem svcItem) {
        this.svcItem = svcItem;
    }

    @PostMapping
    @Operation(summary = "Registrar artículo", description = "Crea un artículo con gtin, stock y precio")
    public ResponseEntity<ApiResponse> create(@RequestBody DtoItemIn in) {
        return ResponseEntity.ok(svcItem.create(in));
    }
}
