package com.invoice.api.service;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoItemIn;

public interface SvcItem {
    ApiResponse create(DtoItemIn in);
}
