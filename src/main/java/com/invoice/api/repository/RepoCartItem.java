package com.invoice.api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.invoice.api.entity.CartItem;

@Repository
public interface RepoCartItem extends JpaRepository<CartItem, Integer> {

    List<CartItem> findByUserIdAndStatus(Integer userId, Integer status);

    List<CartItem> findByUserId(Integer userId);
}
