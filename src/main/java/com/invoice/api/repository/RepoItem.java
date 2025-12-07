package com.invoice.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.invoice.api.entity.Item;

@Repository
public interface RepoItem extends JpaRepository<Item, String> {
}
