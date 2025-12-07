package com.invoice.api.service;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.invoice.api.dto.ApiResponse;
import com.invoice.api.dto.DtoItemIn;
import com.invoice.api.entity.Item;
import com.invoice.api.repository.RepoItem;
import com.invoice.exception.ApiException;
import com.invoice.exception.DBAccessException;

@Service
public class SvcItemImp implements SvcItem {

    private final RepoItem repoItem;

    public SvcItemImp(RepoItem repoItem) {
        this.repoItem = repoItem;
    }

    @Override
    public ApiResponse create(DtoItemIn in) {
        try {
            if (in == null || in.getGtin() == null || in.getGtin().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El gtin es requerido");
            }
            if (repoItem.findById(in.getGtin()).isPresent()) {
                throw new ApiException(HttpStatus.CONFLICT, "El gtin ya existe");
            }
            if (in.getStock() == null || in.getStock() < 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El stock debe ser 0 o mayor");
            }
            if (in.getUnitPrice() == null || in.getUnitPrice() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "El precio debe ser mayor a 0");
            }
            Item item = new Item();
            item.setGtin(in.getGtin());
            item.setStock(in.getStock());
            item.setUnitPrice(in.getUnitPrice());
            item.setStatus(in.getStatus() != null ? in.getStatus() : 1);
            repoItem.save(item);
            return new ApiResponse("El artículo ha sido registrado");
        } catch (DataAccessException e) {
            throw new DBAccessException();
        }
    }
}
